package com.segment.segmentmetricservice.domain.metric;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}