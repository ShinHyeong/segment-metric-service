package com.segment.segmentmetricservice.domain.metric;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "segment_daily_metric",
        indexes = {
                // 추세 분석 API 성능을 위한 복합 인덱스 (세그먼트 조회 -> 날짜 범위 필터링)
                @Index(name = "idx_segment_date", columnList = "segment_id, metric_date")
        }
)
public class SegmentDailyMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "segment_id", nullable = false)
    private Long segmentId; // 연관관계 매핑 대신 ID 직접 참조 (Bulk Insert 최적화)

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "user_count", nullable = false)
    private Long userCount; // 수 억 건의 유저일 수 있으므로 Long 사용

    // 생성자 (Bulk Insert나 테스트용)
    public SegmentDailyMetric(Long segmentId, LocalDate metricDate, Long userCount) {
        this.segmentId = segmentId;
        this.metricDate = metricDate;
        this.userCount = userCount;
    }

    // 정적 팩토리 메서드 (필요시 사용)
    public static SegmentDailyMetric create(Long segmentId, LocalDate date, Long count) {
        return new SegmentDailyMetric(segmentId, date, count);
    }
}