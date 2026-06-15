package com.segment.segmentmetricservice.service.batch.index;

import com.segment.segmentmetricservice.aspect.TrackExecutionTime;
import com.segment.segmentmetricservice.domain.metric.ProcessStatus;
import com.segment.segmentmetricservice.domain.metric.SegmentDailyMetric;
import com.segment.segmentmetricservice.domain.metric.SegmentDailyMetricRepository;
import com.segment.segmentmetricservice.domain.segment.Segment;
import com.segment.segmentmetricservice.domain.segment.SegmentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricChunkProcessor {

    private final SegmentRepository segmentRepository;
    private final SegmentDailyMetricRepository metricRepository;
    private final UserCountQueryService userCountQueryService;
    private final Executor segmentCountExecutor;
    private final MeterRegistry meterRegistry;

    @TrackExecutionTime(metricName = "batch.chunk.duration",
                        tags = {"chunkSize", "#chunkSize", "round", "#round"})
    public boolean processChunk(LocalDate today, int chunkSize, int round) {
        List<SegmentDailyMetric> pendingMetricChunk =
                metricRepository.findByMetricDateAndStatus(today, ProcessStatus.PENDING, PageRequest.of(0, chunkSize));

        if (pendingMetricChunk.isEmpty()) return false;

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (SegmentDailyMetric pendingMetric : pendingMetricChunk) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                //단일 세그먼트 측정 타이머도 AOP로 처리할 수 있으나, Exception 카운트 증가 등 세밀한 제어가 필요하므로 유지했음
                Timer.Sample segSample = Timer.start(meterRegistry);
                try {
                    Segment segment = segmentRepository.findById(pendingMetric.getSegmentId()).orElseThrow();

                    long userCount = userCountQueryService.countUsersBySegment(segment);
                    pendingMetric.updateCount(userCount);

                    metricRepository.save(pendingMetric);
                } catch (Exception e) {
                    meterRegistry.counter("batch.segment.error",
                            "chunkSize", String.valueOf(chunkSize)).increment();
                    log.error("Error segment {}", pendingMetric.getSegmentId(), e);
                } finally {
                    segSample.stop(Timer.builder("batch.segment.count.duration")
                            .tag("chunkSize", String.valueOf(chunkSize))
                            .register(meterRegistry));
                }
            }, segmentCountExecutor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("청크 처리 완료 | round={}, chunkSize={}", round, chunkSize);
        return true;
    }
}
