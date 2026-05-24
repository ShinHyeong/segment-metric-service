package com.segment.segmentmetricservice.service.batch;

import com.segment.segmentmetricservice.domain.metric.ProcessStatus;
import com.segment.segmentmetricservice.domain.metric.SegmentDailyMetric;
import com.segment.segmentmetricservice.domain.metric.SegmentDailyMetricRepository;
import com.segment.segmentmetricservice.domain.segment.Segment;
import com.segment.segmentmetricservice.domain.segment.SegmentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final SegmentDailyMetricRepository metricRepository;
    private final UserCountCalculator userCountCalculator;
    private final Executor taskExecutor;
    private final MeterRegistry meterRegistry;

    @Value("${batch.chunk-size:1000}")
    private int chunkSize;

    public DailyMetricBatchService(
            SegmentRepository segmentRepository,
            SegmentDailyMetricRepository metricRepository,
            UserCountCalculator userCountCalculator,
            @Qualifier("batchTaskExecutor") Executor taskExecutor,
            MeterRegistry meterRegistry) {
        this.segmentRepository = segmentRepository;
        this.metricRepository = metricRepository;
        this.userCountCalculator = userCountCalculator;
        this.taskExecutor = taskExecutor;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional(transactionManager = "segmentTransactionManager")
    public void executeDailyBatch() {
        LocalDate today = LocalDate.now();
        log.info("Batch Start: {} | chunkSize={}", today, chunkSize);

        Timer.Sample totalSample = Timer.start(meterRegistry);

        // Step 1. PENDING 상태로 초기화
        initMetricTable(today);

        // Step 2. 병렬 Count 처리
        processCountStep(today);

        // Step 3. COUNTED → INSERTED 상태 전환 (완료 마킹)
        markCompleted(today);

        totalSample.stop(Timer.builder("batch.total.duration")
                .tag("chunkSize", String.valueOf(chunkSize))
                .register(meterRegistry));

        log.info("Batch End: {} | chunkSize={}", today, chunkSize);
    }

    private void initMetricTable(LocalDate date) {
        if (metricRepository.existsByMetricDate(date)) {
            log.info("이미 초기화됨. 스킵.");
            return;
        }

        Timer.Sample initSample = Timer.start(meterRegistry);

        int pageNumber = 0;
        Page<Segment> segmentPage;

        do {
            segmentPage = segmentRepository.findAll(PageRequest.of(pageNumber, chunkSize));

            List<SegmentDailyMetric> initData = segmentPage.getContent().stream()
                    .map(s -> new SegmentDailyMetric(s.getId(), date))
                    .collect(Collectors.toList());

            metricRepository.saveAll(initData);

            pageNumber++;
            log.info("초기화 중... Page: {}", pageNumber);
        } while (segmentPage.hasNext());

        initSample.stop(Timer.builder("batch.init.duration")
                .tag("chunkSize", String.valueOf(chunkSize))
                .register(meterRegistry));
    }

    private void processCountStep(LocalDate today) {
        Timer.Sample countStepSample = Timer.start(meterRegistry);
        int round = 0;

        while (true) {
            List<SegmentDailyMetric> pendings =
                    metricRepository.findByMetricDateAndStatus(
                            today, ProcessStatus.PENDING, PageRequest.of(0, chunkSize));

            if (pendings.isEmpty()) break;

            Timer.Sample chunkSample = Timer.start(meterRegistry);

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (SegmentDailyMetric metric : pendings) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    Timer.Sample segSample = Timer.start(meterRegistry);
                    try {
                        Segment segment = segmentRepository.findById(metric.getSegmentId()).orElseThrow();
                        Long count = userCountCalculator.countUsersBySegment(segment);

                        metric.updateCount(count); // status → COUNTED, userCount 반영
                        metricRepository.save(metric);
                    } catch (Exception e) {
                        meterRegistry.counter("batch.segment.error",
                                "chunkSize", String.valueOf(chunkSize)).increment();
                        log.error("Error segment {}", metric.getSegmentId(), e);
                    } finally {
                        segSample.stop(Timer.builder("batch.segment.count.duration")
                                .tag("chunkSize", String.valueOf(chunkSize))
                                .register(meterRegistry));
                    }
                }, taskExecutor);
                futures.add(future);
            }

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

    private void markCompleted(LocalDate date) {
        Timer.Sample flushSample = Timer.start(meterRegistry);

        List<SegmentDailyMetric> countedList =
                metricRepository.findByMetricDateAndStatus(
                        date, ProcessStatus.COUNTED, PageRequest.of(0, Integer.MAX_VALUE));

        for (int i = 0; i < countedList.size(); i += chunkSize) {
            List<SegmentDailyMetric> batch =
                    countedList.subList(i, Math.min(i + chunkSize, countedList.size()));

            batch.forEach(SegmentDailyMetric::markCompleted);
            metricRepository.saveAll(batch);
        }

        flushSample.stop(Timer.builder("batch.flush.step.duration")
                .tag("chunkSize", String.valueOf(chunkSize))
                .register(meterRegistry));
    }
}