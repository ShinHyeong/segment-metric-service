#!/bin/bash

LABEL=${1:-snapshot}

if [[ "$LABEL" == "BEFORE" ]]; then
    OUTPUT_DIR="measurements/BEFORE"
elif [[ "$LABEL" == *"idx"* ]]; then
    OUTPUT_DIR="measurements/AFTER/idx"
elif [[ "$LABEL" == *"cube"* ]]; then
    OUTPUT_DIR="measurements/AFTER/cube"
else
    OUTPUT_DIR="measurements/${LABEL}"
fi

mkdir -p "$OUTPUT_DIR"

{
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
docker exec user-db-slave sh -c '
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

echo "=== 6. Bucket 분포 (latency 구간별 누적 카운트) ==="
curl -s 'http://localhost:9090/api/v1/query?query=batch_segment_count_duration_seconds_bucket' \
  | jq -r '.data.result[] | "\(.metric.le): \(.value[1])"' | sort -g | tail -25

} | tee "$OUTPUT_DIR/${LABEL}_final.log"