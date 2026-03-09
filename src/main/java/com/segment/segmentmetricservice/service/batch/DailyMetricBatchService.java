package com.segment.segmentmetricservice.service.batch;

import com.segment.segmentmetricservice.domain.metric.ProcessStatus;
import com.segment.segmentmetricservice.domain.metric.SegmentDailyMetricProgress;
import com.segment.segmentmetricservice.domain.metric.SegmentDailyMetricProgressRepository;
import com.segment.segmentmetricservice.domain.segment.Segment;
import com.segment.segmentmetricservice.domain.segment.SegmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyMetricBatchService {

    private final SegmentRepository segmentRepository;
    private final SegmentDailyMetricProgressRepository progressRepository;
    private final UserCountCalculator userCountCalculator;
    private final MetricBulkInserter metricBulkInserter;

    // 비동기 처리를 위한 스레드 풀 (Config에서 정의된 빈)
    @Qualifier("batchTaskExecutor")
    private final Executor taskExecutor;

    @Scheduled(cron = "0 0 0 * * *") // 매일 자정 실행
    public void executeDailyBatch() {
        LocalDate today = LocalDate.now();
        log.info("Batch Start: {}", today);

        // Step 1. 초기화 (진행 상태 테이블에 PENDING 상태로 세팅)
        initProgressTable(today);

        // Step 2. 병렬 Count 처리
        int pageSize = 1000;
        while (true) {
            // PENDING 상태인 데이터를 1000개만 가져옴
            List<SegmentDailyMetricProgress> pendings = progressRepository.findTop1000ByMetricDateAndStatus(today, ProcessStatus.PENDING);

            if (pendings.isEmpty()) break; // 더 이상 처리할 게 없으면 탈출

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (SegmentDailyMetricProgress progress : pendings) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        Segment segment = segmentRepository.findById(progress.getSegmentId()).orElseThrow();
                        Long count = userCountCalculator.countUsersBySegment(segment);

                        progress.updateCount(count);
                        progressRepository.save(progress);
                    } catch (Exception e) {
                        log.error("Error segment {}", progress.getSegmentId(), e);
                    }
                }, taskExecutor);
                futures.add(future);
            }

            // 1000개 묶음이 다 끝날 때까지 대기 후 다음 1000개 진행 (Throttling 효과)
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            log.info("1000개 세그먼트 처리 완료...");
        }

        // Step 3. Bulk Insert
        flushCountedToMetricTable(today);

        log.info("Batch End");
    }

    private void initProgressTable(LocalDate date) {
        // 이미 해당 날짜 데이터가 있으면 스킵 (중복생성 방지)
        if (progressRepository.existsByMetricDate(date)) return;

        int pageSize = 1000;
        int pageNumber = 0;
        org.springframework.data.domain.Page<Segment> segmentPage;

        do {
            segmentPage = segmentRepository.findAll(org.springframework.data.domain.PageRequest.of(pageNumber, pageSize));

            List<SegmentDailyMetricProgress> initData = segmentPage.getContent().stream()
                    .map(s -> new SegmentDailyMetricProgress(s.getId(), date))
                    .collect(Collectors.toList());

            progressRepository.saveAll(initData);

            pageNumber++;
            log.info("Progress 초기화 중... Page: {}", pageNumber);
        } while (segmentPage.hasNext()); // 다음 페이지가 없을 때까지 반복
    }

    private void flushCountedToMetricTable(LocalDate date) {
        // COUNTED 상태인 데이터 조회
        List<SegmentDailyMetricProgress> countedList =
                progressRepository.findAllByMetricDateAndStatus(date, ProcessStatus.COUNTED);

        // 배치 사이즈(예: 1000) 단위로 나누어 Bulk Insert
        int batchSize = 1000;
        for (int i = 0; i < countedList.size(); i += batchSize) {
            List<SegmentDailyMetricProgress> batch = countedList.subList(i, Math.min(i + batchSize, countedList.size()));

            // 1. 실제 Metric 테이블에 Bulk Insert
            metricBulkInserter.bulkInsertMetrics(batch);

            // 2. Progress 테이블 상태 업데이트 (INSERTED)
            batch.forEach(SegmentDailyMetricProgress::markInserted);
            progressRepository.saveAll(batch);
        }

        // 3. (옵션) 모든 작업 완료 후 Progress 테이블 정리(Truncate)는 별도 스케줄러나 관리자가 수행
    }
}