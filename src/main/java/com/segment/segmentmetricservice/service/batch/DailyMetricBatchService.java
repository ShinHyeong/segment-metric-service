package com.segment.segmentmetricservice.service.batch;

import com.segment.segmentmetricservice.domain.metric.ProcessStatus;
import com.segment.segmentmetricservice.domain.metric.SegmentDailyMetricProgress;
import com.segment.segmentmetricservice.domain.metric.SegmentDailyMetricProgressRepository;
import com.segment.segmentmetricservice.domain.segment.Segment;
import com.segment.segmentmetricservice.domain.segment.SegmentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DailyMetricBatchService {

    private final SegmentRepository segmentRepository;
    private final SegmentDailyMetricProgressRepository progressRepository;
    private final UserCountCalculator userCountCalculator;
    private final MetricBulkInserter metricBulkInserter;
    private final MeterRegistry meterRegistry;
    private final Executor taskExecutor;


    @Value("${batch.chunk-size:1000}")
    private int chunkSize;

    public DailyMetricBatchService(SegmentRepository segmentRepository,
                                   SegmentDailyMetricProgressRepository progressRepository,
                                   UserCountCalculator userCountCalculator,
                                   MetricBulkInserter metricBulkInserter,
                                   MeterRegistry meterRegistry,
                                   @Qualifier("batchTaskExecutor") Executor taskExecutor) {
        this.segmentRepository = segmentRepository;
        this.progressRepository = progressRepository;
        this.userCountCalculator = userCountCalculator;
        this.metricBulkInserter = metricBulkInserter;
        this.meterRegistry = meterRegistry;
        this.taskExecutor = taskExecutor;
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void executeDailyBatch() {
        LocalDate today = LocalDate.now();
        log.info("Batch Start: {}", today);
        // 전체 배치 소요시간 측정
        Timer.Sample totalSample = Timer.start(meterRegistry);

        // Step 1. 초기화 (진행 상태 테이블에 PENDING 상태로 세팅)
        initProgressTable(today);

        // Step 2. 병렬 Count 처리
        processCountStep(today);

        // Step 3. Bulk Insert
        flushCountedToMetricTable(today);

        totalSample.stop(Timer.builder("batch.total.duration")
                .tag("chunkSize", String.valueOf(chunkSize))
                .register(meterRegistry));

        log.info("Batch End");
    }

    private void initProgressTable(LocalDate date) {
        if (progressRepository.existsByMetricDate(date)) {
            log.info("Progress 테이블 이미 초기화됨. 스킵.");
            return;
        }

        Timer.Sample initSample = Timer.start(meterRegistry);

        int pageNumber = 0;
        Page<Segment> segmentPage;

        do {
            segmentPage = segmentRepository.findAll(PageRequest.of(pageNumber, chunkSize));

            List<SegmentDailyMetricProgress> initData = segmentPage.getContent().stream()
                    .map(s -> new SegmentDailyMetricProgress(s.getId(), date))
                    .collect(Collectors.toList());

            progressRepository.saveAll(initData);

            pageNumber++;
            log.info("Progress 초기화 중... Page: {}", pageNumber);
        } while (segmentPage.hasNext());

        initSample.stop(Timer.builder("batch.init.duration")
                .tag("chunkSize", String.valueOf(chunkSize))
                .register(meterRegistry));
    }

    private void processCountStep(LocalDate today) {
        Timer.Sample countStepSample = Timer.start(meterRegistry);
        int round = 0;

        while (true) {
            List<SegmentDailyMetricProgress> pendings =
                    progressRepository.findByMetricDateAndStatus(
                            today, ProcessStatus.PENDING, PageRequest.of(0, chunkSize));

            if (pendings.isEmpty()) break;

            Timer.Sample chunkSample = Timer.start(meterRegistry);

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (SegmentDailyMetricProgress progress : pendings) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    Timer.Sample segSample = Timer.start(meterRegistry);
                    try {
                        Segment segment = segmentRepository.findById(progress.getSegmentId()).orElseThrow();
                        Long count = userCountCalculator.countUsersBySegment(segment);

                        progress.updateCount(count);
                        progressRepository.save(progress);
                    } catch (Exception e) {
                        meterRegistry.counter("batch.segment.error",
                                "chunkSize", String.valueOf(chunkSize)).increment();
                        log.error("Error segment {}", progress.getSegmentId(), e);
                    } finally {
                        segSample.stop(Timer.builder("batch.segment.count.duration")
                                .tag("chunkSize", String.valueOf(chunkSize))
                                .register(meterRegistry));
                    }
                }, taskExecutor);
                futures.add(future);
            }

            // 청크 단위 대기 (Throttling)
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            chunkSample.stop(Timer.builder("batch.chunk.duration")
                    .tag("chunkSize", String.valueOf(chunkSize))
                    .tag("round", String.valueOf(round))
                    .register(meterRegistry));

            log.info("청크 처리 완료 | round={}, chunkSize={}", round, chunkSize);
            round++;
        }

        countStepSample.stop(Timer.builder("batch.count.step.duration")
                .tag("chunkSize", String.valueOf(chunkSize))
                .register(meterRegistry));
    }

    private void flushCountedToMetricTable(LocalDate date) {
        Timer.Sample flushSample = Timer.start(meterRegistry);

        List<SegmentDailyMetricProgress> countedList =
                progressRepository.findAllByMetricDateAndStatus(date, ProcessStatus.COUNTED);

        for (int i = 0; i < countedList.size(); i += chunkSize) {
            List<SegmentDailyMetricProgress> batch =
                    countedList.subList(i, Math.min(i + chunkSize, countedList.size()));

            Timer.Sample insertSample = Timer.start(meterRegistry);

            // 1. Metric 테이블에 Bulk Insert
            metricBulkInserter.bulkInsertMetrics(batch);

            // 2. Progress 상태 업데이트 (INSERTED)
            batch.forEach(SegmentDailyMetricProgress::markInserted);
            progressRepository.saveAll(batch);

            insertSample.stop(Timer.builder("batch.bulk.insert.duration")
                    .tag("chunkSize", String.valueOf(chunkSize))
                    .register(meterRegistry));
        }

        flushSample.stop(Timer.builder("batch.flush.step.duration")
                .tag("chunkSize", String.valueOf(chunkSize))
                .register(meterRegistry));
    }
}