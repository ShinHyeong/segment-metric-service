package com.segment.segmentmetricservice.domain.metric;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "segment_daily_metric_progress")
public class SegmentDailyMetricProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long segmentId;
    private LocalDate metricDate;
    private Long userCount;

    @Enumerated(EnumType.STRING)
    private ProcessStatus status;

    public SegmentDailyMetricProgress(Long segmentId, LocalDate metricDate) {
        this.segmentId = segmentId;
        this.metricDate = metricDate;
        this.status = ProcessStatus.PENDING;
        this.userCount = 0L;
    }

    public void updateCount(Long count) {
        this.userCount = count;
        this.status = ProcessStatus.COUNTED;
    }

    public void markInserted() {
        this.status = ProcessStatus.INSERTED;
    }
}