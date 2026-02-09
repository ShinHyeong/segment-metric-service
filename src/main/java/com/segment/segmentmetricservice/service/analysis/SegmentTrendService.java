package com.segment.segmentmetricservice.service.analysis;

import com.segment.segmentmetricservice.domain.metric.SegmentDailyMetric;
import com.segment.segmentmetricservice.domain.metric.SegmentDailyMetricRepository;
import com.segment.segmentmetricservice.dto.analysis.TrendResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SegmentTrendService {

    private final SegmentDailyMetricRepository metricRepository;

    public List<TrendResponseDto> getTrend(Long segmentId, LocalDate startDate, LocalDate endDate) {
        // 복합 인덱스 (segment_id, metric_date)를 타는 조회
        List<SegmentDailyMetric> metrics = metricRepository.findAllBySegmentIdAndMetricDateBetweenOrderByMetricDateAsc(
                segmentId, startDate, endDate
        );

        return metrics.stream()
                .map(m -> new TrendResponseDto(m.getMetricDate(), m.getUserCount()))
                .collect(Collectors.toList());
    }
}