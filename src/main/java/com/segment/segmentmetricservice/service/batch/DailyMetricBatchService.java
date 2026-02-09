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
        // OOM 방지를 위해 필요한 경우 여기서도 페이징/스트림 처리 가능 (설계상 2만개는 OK)
        List<Segment> allSegments = segmentRepository.findAll();
        initProgressTable(allSegments, today);

        // Step 2. 병렬 Count 처리
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // 아직 완료되지 않은(PENDING) 작업 조회 (재시도 시에도 유효)
        List<SegmentDailyMetricProgress> pendings =
                progressRepository.findAllByMetricDateAndStatus(today, ProcessStatus.PENDING);

        for (SegmentDailyMetricProgress progress : pendings) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // 세그먼트 정보 조회
                    Segment segment = segmentRepository.findById(progress.getSegmentId()).orElseThrow();

                    // Count 계산 (Slave DB)
                    Long count = userCountCalculator.countUsersBySegment(segment);

                    // Progress 업데이트 (COUNTED)
                    progress.updateCount(count);
                    progressRepository.save(progress); // 개별 트랜잭션 (빠른 커밋)

                } catch (Exception e) {
                    log.error("Error calculating segment {}", progress.getSegmentId(), e);
                    // 실패 시 Status는 PENDING 유지 -> 다음 배치/재시도 시 처리
                }
            }, taskExecutor);
            futures.add(future);
        }

        // 모든 병렬 작업 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Step 3. Bulk Insert
        flushCountedToMetricTable(today);

        log.info("Batch End");
    }

    private void initProgressTable(List<Segment> segments, LocalDate date) {
        // 이미 해당 날짜 데이터가 있으면 스킵 (재시도 로직 지원)
        if (progressRepository.existsByMetricDate(date)) return;

        List<SegmentDailyMetricProgress> initData = segments.stream()
                .map(s -> new SegmentDailyMetricProgress(s.getId(), date))
                .collect(Collectors.toList());

        progressRepository.saveAll(initData); // JPA Batch Insert 설정 활용
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