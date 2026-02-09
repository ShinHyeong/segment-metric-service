package com.segment.segmentmetricservice.domain.metric;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SegmentDailyMetricRepository extends JpaRepository<SegmentDailyMetric, Long> {

    /**
     * [추세 분석 API용]
     * 특정 세그먼트의 지정된 기간 내 지표를 날짜 오름차순으로 조회합니다.
     * * 복합 인덱스(idx_segment_date)가 걸려 있어 고속 조회가 가능합니다.
     * SQL: SELECT * FROM segment_daily_metric
     * WHERE segment_id = ? AND metric_date BETWEEN ? AND ?
     * ORDER BY metric_date ASC
     */
    List<SegmentDailyMetric> findAllBySegmentIdAndMetricDateBetweenOrderByMetricDateAsc(
            Long segmentId,
            LocalDate startDate,
            LocalDate endDate
    );
}