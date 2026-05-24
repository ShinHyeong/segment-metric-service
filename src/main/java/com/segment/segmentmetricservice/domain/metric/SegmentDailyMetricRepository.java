package com.segment.segmentmetricservice.domain.metric;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SegmentDailyMetricRepository extends JpaRepository<SegmentDailyMetric, Long> {

    List<SegmentDailyMetric> findAllBySegmentIdAndMetricDateBetweenOrderByMetricDateAsc(
            Long segmentId, LocalDate startDate, LocalDate endDate);

    List<SegmentDailyMetric> findByMetricDateAndStatus(
            LocalDate date, ProcessStatus status, Pageable pageable);

    boolean existsByMetricDate(LocalDate metricDate);

    @Modifying
    @Query(value = """
    INSERT INTO segment_daily_metric (segment_id, metric_date, user_count, status)
    SELECT s.segment_id, :metricDate, 0, 'PENDING'
    FROM segment s
    WHERE s.segment_id IN (:segmentIds)
    ON DUPLICATE KEY UPDATE segment_daily_metric.segment_id = segment_daily_metric.segment_id
    """, nativeQuery = true)
    void bulkInsertIfNotExists(
            @Param("metricDate") LocalDate metricDate,
            @Param("segmentIds") List<Long> segmentIds
    );
}