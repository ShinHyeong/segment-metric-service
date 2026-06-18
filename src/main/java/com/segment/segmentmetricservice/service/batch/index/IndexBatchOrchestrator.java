package com.segment.segmentmetricservice.service.batch.index;

import com.segment.segmentmetricservice.aspect.TrackExecutionTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Component
@Getter
@RequiredArgsConstructor
public class IndexBatchOrchestrator {
    private final InitMetricService initMetricService;
    private final IndexSegmentMetricService segmentMetricService;

    @Value("${batch.index.chunk-size:1000}")
    private int chunkSize;

    //@Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @TrackExecutionTime(metricName = "batch.total.duration",
                        tags = {"chunkSize", "#target.chunkSize"})
    @Transactional(transactionManager = "segmentTransactionManager")
    public void executeDailyBatch() {
        LocalDate today = LocalDate.now();
        log.info("Batch Start: {} | chunkSize={}", today, chunkSize);

        // Step 1. PENDING 상태로 초기화
        initMetricService.initMetricTable(today, chunkSize);

        // Step 2. 병렬 Count 처리
        segmentMetricService.processCountStep(today, chunkSize);
        log.info("Batch End: {} | chunkSize={}", today, chunkSize);
    }

}