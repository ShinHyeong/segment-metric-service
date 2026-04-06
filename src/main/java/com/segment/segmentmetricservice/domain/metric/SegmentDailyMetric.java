package com.segment.segmentmetricservice.domain.metric;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "segment_daily_metric",
        indexes = {
                @Index(name = "idx_segment_date", columnList = "segment_id, metric_date")
        })
public class SegmentDailyMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long segmentId;
    private LocalDate metricDate;
    private Long userCount;

    @Enumerated(EnumType.STRING)
    private ProcessStatus status;

    public SegmentDailyMetric(Long segmentId, LocalDate metricDate) {
        this.segmentId = segmentId;
        this.metricDate = metricDate;
        this.userCount = 0L;
        this.status = ProcessStatus.PENDING;
    }

    public void updateCount(Long count) {
        this.userCount = count;
        this.status = ProcessStatus.COUNTED;
    }

    public void markCompleted() {
        this.status = ProcessStatus.INSERTED;
    }
}