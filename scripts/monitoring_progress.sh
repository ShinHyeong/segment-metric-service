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

PROGRESS_LOG="$OUTPUT_DIR/${LABEL}_progress.log"
VMSTAT_LOG="$OUTPUT_DIR/${LABEL}_vmstat.log"
MYSQL_LOG="$OUTPUT_DIR/${LABEL}_status_start.log"

# 1. MySQL 초기 카운터 저장 (시작 시점 스냅샷)
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
);"' | tee "$MYSQL_LOG"

# 2. vmstat을 백그라운드(&)로 실행하여 파일과 화면에 DB 자원 사용량 동시에 기록
docker exec user-db-slave bash -c "vmstat 5" | tee "$VMSTAT_LOG" &
VMSTAT_PID=$!

# 스크립트 종료(Ctrl+C) 시 백그라운드로 돌린 vmstat도 깔끔하게 같이 꺼지도록 설정
trap "kill $VMSTAT_PID; exit" INT TERM

# 3. 1분마다 메트릭 실시간 추이 관찰 루프
while true; do
  {
    echo "=== $(date '+%H:%M:%S') ==="
    curl -s 'http://localhost:9090/api/v1/query?query=batch_segment_count_duration_seconds_count' \
      | jq -r '.data.result[] | "processed: \(.value[1])"' 2>/dev/null || echo "processed: fetch error"

    curl -s 'http://localhost:9090/api/v1/query?query=batch_segment_count_duration_seconds_sum/batch_segment_count_duration_seconds_count' \
      | jq -r '.data.result[] | "avg: \(.value[1])s"' 2>/dev/null || echo "avg: fetch error"
  } | tee -a "$PROGRESS_LOG" # 누적 기록

  sleep 60
done