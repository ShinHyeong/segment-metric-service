#!/bin/bash

LABEL=${1:-snapshot}

if [[ "$LABEL" == "BEFORE" ]]; then
    OUTPUT_DIR="measurements/BEFORE"
    MODE="index"
elif [[ "$LABEL" == *"idx"* ]]; then
    OUTPUT_DIR="measurements/AFTER/idx"
    MODE="index"
elif [[ "$LABEL" == *"cube"* ]]; then
    OUTPUT_DIR="measurements/AFTER/cube"
    MODE="cube"
else
    OUTPUT_DIR="measurements/${LABEL}"
    MODE="index"
fi

mkdir -p "$OUTPUT_DIR"

PROM="http://localhost:9090/api/v1/query"

# Prometheus instant query 헬퍼: prom_scalar "<promql>"
prom_scalar() {
  curl -s "$PROM" --data-urlencode "query=$1" \
    | jq -r '.data.result[0].value[1] // "N/A"'
}

# user-db-slave 의 InnoDB READ 카운터 (두 후보 공통 - 직접 diff 가능)
mysql_counters() {
  local container=$1
  echo "--- ${container} ---"
  docker exec "$container" sh -c '
export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
mysql -uroot -e "
SELECT VARIABLE_NAME, VARIABLE_VALUE
FROM performance_schema.global_status
WHERE VARIABLE_NAME IN (
  \"Innodb_buffer_pool_read_requests\",
  \"Innodb_buffer_pool_reads\",
  \"Innodb_rows_read\",
  \"Innodb_data_read\",
  \"Handler_read_rnd_next\",
  \"Handler_read_key\"
);"'
}

# ============================================================
#  후보1 (인덱스) : 세그먼트별 COUNT 쿼리 히스토그램 기반
# ============================================================
capture_index() {
echo "=== 1. 최종 처리 개수 ==="
curl -s 'http://localhost:9090/api/v1/query?query=batch_segment_count_duration_seconds_count' \
  | jq -r '.data.result[] | "processed: \(.value[1])"'

echo "=== 2. 평균 latency ==="
curl -s 'http://localhost:9090/api/v1/query?query=batch_segment_count_duration_seconds_sum/batch_segment_count_duration_seconds_count' \
  | jq -r '.data.result[0].value[1]'

echo "=== 3. p50/p95/p99 ==="
for q in 0.5 0.95 0.99; do
  echo -n "p${q}: "
  curl -s "http://localhost:9090/api/v1/query" \
    --data-urlencode "query=histogram_quantile($q, sum by (le) (batch_segment_count_duration_seconds_bucket))" \
    | jq -r '.data.result[0].value[1]'
done

echo "=== 4. 전체 배치 시간 (batch.total.duration) ==="
curl -s 'http://localhost:9090/api/v1/query?query=batch_total_duration_seconds_sum' \
  | jq -r '.data.result[] | "total: \(.value[1])s"'

echo "=== 5. MySQL 카운터 ==="
mysql_counters user-db-slave

echo "=== 6. Bucket 분포 (latency 구간별 누적 카운트) ==="
curl -s 'http://localhost:9090/api/v1/query?query=batch_segment_count_duration_seconds_bucket' \
  | jq -r '.data.result[] | "\(.metric.le): \(.value[1])"' | sort -g | tail -25
}

# ============================================================
#  후보2 (큐브) : build(account 풀스캔) → persist → metric.save
#  세그먼트별 DB 쿼리가 없으므로 per-segment p50/p95/p99 는 해당 없음.
#  비교축 = (4) 전체시간  +  (5) MySQL READ 카운터  +  (1) 단계별 분해
# ============================================================
capture_cube() {
echo "=== 1. 단계별 소요 시간 (build / persist / metric.save) ==="
echo "build   : $(prom_scalar 'cube_build_duration_seconds_sum')s"
echo "persist : $(prom_scalar 'cube_persist_duration_seconds_sum')s   (async, 전체시간과 겹칠 수 있음)"
echo "metric  : $(prom_scalar 'cube_metric_save_duration_seconds_sum')s"

echo "=== 2. 단계별 실행 횟수 (count) ==="
echo "build   : $(prom_scalar 'cube_build_duration_seconds_count')"
echo "persist : $(prom_scalar 'cube_persist_duration_seconds_count')"
echo "metric  : $(prom_scalar 'cube_metric_save_duration_seconds_count')"

echo "=== 3. (참고) per-segment p50/p95/p99 ==="
echo "N/A - 후보2는 세그먼트별 DB 쿼리를 날리지 않고 인메모리 큐브 합산으로 계산함"

echo "=== 4. 전체 배치 시간 (cube.batch.total.duration) ==="
curl -s 'http://localhost:9090/api/v1/query?query=cube_batch_total_duration_seconds_sum' \
  | jq -r '.data.result[] | "total: \(.value[1])s"'

echo "=== 5. MySQL 카운터 ==="
# account 풀스캔이 일어나는 user-db-slave 가 핵심 (후보1과 동일 축)
mysql_counters user-db-slave
# 컷포인트/세그먼트 5천 READ 가 일어나는 segment-db-slave (후보2 고유)
mysql_counters segment-db-slave

echo "=== 6. account_cube 적재 건수 (카디널리티 곱 검증) ==="
docker exec user-db-master sh -c '
export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
mysql -uroot -e "SELECT COUNT(*) AS cube_rows FROM user_db.account_cube;"' 2>/dev/null \
  || echo "account_cube 조회 실패 (테이블/컨테이너 확인)"
}

# ============================================================
{
echo "############ LABEL=${LABEL}  MODE=${MODE}  $(date '+%F %T') ############"
if [[ "$MODE" == "cube" ]]; then
  capture_cube
else
  capture_index
fi
} | tee "$OUTPUT_DIR/${LABEL}_final.log"