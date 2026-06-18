package com.segment.segmentmetricservice.service.batch.index;

import com.segment.segmentmetricservice.aspect.TrackExecutionTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexSegmentMetricService {

    private final MetricChunkProcessor metricChunkProcessor;

    @TrackExecutionTime(metricName = "batch.count.step.duration",
                        tags = {"chunkSize", "#chunkSize"})
    public void processCountStep(LocalDate today, int chunkSize) {
        int round = 0;

        while (true) {
            boolean isProceed = metricChunkProcessor.processChunk(today, chunkSize, round);
            if (!isProceed) break;

            round++;
        }
    }
}
