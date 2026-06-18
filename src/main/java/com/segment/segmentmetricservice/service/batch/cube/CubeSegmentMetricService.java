package com.segment.segmentmetricservice.service.batch.cube;

import com.segment.segmentmetricservice.aspect.TrackExecutionTime;
import com.segment.segmentmetricservice.domain.metric.SegmentDailyMetric;
import com.segment.segmentmetricservice.domain.metric.SegmentDailyMetricJdbcRepository;
import com.segment.segmentmetricservice.domain.segment.Segment;
import com.segment.segmentmetricservice.domain.segment.SegmentRepository;
import com.segment.segmentmetricservice.domain.user.cube.UserCubeKey;
import com.segment.segmentmetricservice.utils.batch.cube.SegmentConditionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 페이징으로 세그먼트를 가져와서
 * 큐브 데이터의 합으로 세그먼트별 통계값을 계산하고
 * 그 값을 DB에 INSERT한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CubeSegmentMetricService {
    private final SegmentRepository segmentRepository;
    private final SegmentDailyMetricJdbcRepository segmentDailyMetricJdbcRepository;
    private final SegmentConditionEvaluator segmentConditionEvaluator;
    private final Executor segmentCountExecutor;

    @TrackExecutionTime(metricName = "cube.metric.save.duration",
            tags = {"pageSize", "#pageSize", "batchSize", "#batchSize"})
    public void saveMetric(Map<UserCubeKey, Long> cube, LocalDate metricDate, int pageSize, int batchSize) {
        log.info("== SegmentMetric 기록 시작 (metricDate={}) ==", metricDate);
        long totalProcessed = 0L;

        long lastId = 0L;
        while (true) {
            List<Segment> segmentsChunk = segmentRepository.findSegmentsAfterId(lastId, pageSize);
            if (segmentsChunk.isEmpty()) break;

            long firstSegmentIdInChunk = segmentsChunk.get(0).getId();

            //통계값 계산 또는 DB INSERT 과정에서 장애가 나서 스킵해야할 경우 무한 루프에 빠지지 않고 다음 청크로 넘어갈 수 있게 미리 계산
            long currentLastId = segmentsChunk.get(segmentsChunk.size() - 1).getId();

            List<SegmentDailyMetric> metricsChunk = processMetricChunk(segmentsChunk, cube, metricDate);
            if (metricsChunk.isEmpty()) {
                log.warn("청크 내 모든 세그먼트 계산 실패 -> Metric DB INSERT 스킵 (segment 범위 : {}~{}), 다음 청크로 진행", firstSegmentIdInChunk, currentLastId);
                lastId = currentLastId;
                continue;
            }

            try {
                /* Jdbc 사용한 이유 : 장애 후 재시도 했을 때 Upsert 동작을 원하기 때문
                * JPA saveAll()을 사용하면 현재 PK metric_id=null이므로 JPA는 무조건 새로운 엔티티로 간주함
                * -> INSERT 시도
                * -> (segment_id, metric_date) Unique Key 충돌 발생하여 INSERT 조차도 불가능해짐
                */
                segmentDailyMetricJdbcRepository.bulkUpsert(metricsChunk, batchSize);
                totalProcessed += metricsChunk.size();
                log.debug("SegmentMetric 기록 진행중... 현재까지 처리된 metric 수: {}", totalProcessed);
            } catch (Exception e){
                log.error("Metric 청크 DB INSERT 실패 (segment 범위: {}~{}) -> 스킵, 다음 청크로 진행", firstSegmentIdInChunk, currentLastId, e);
                lastId = currentLastId;
                continue;
            }
            lastId = currentLastId;
        }

        log.info("== SegmentMetric 기록 완료 (총 반영된 metric 수: {}) ==", totalProcessed);
    }

    /**
     * (세그먼트 청크 단위) * (큐브 크기) 만큼 Predicate 식을 비교해야함
     * 순서가 중요한게 아니므로 병렬로 처리한다
     */
    private List<SegmentDailyMetric> processMetricChunk(List<Segment> segmentsChunk, Map<UserCubeKey, Long> cube, LocalDate metricDate) {
        List<CompletableFuture<SegmentDailyMetric>> futures = segmentsChunk.stream()
                .map(segment -> CompletableFuture.supplyAsync(
                        () -> calculateSingleMetric(segment, cube, metricDate),
                        segmentCountExecutor
                        ).exceptionally(e->{
                            log.error("segment_id={} user_count 계산 실패 -> 해당 세그먼트 스킵, 다음 세그먼트로 진행", segment.getId(), e);
                            return null;
                        })
                ).toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private SegmentDailyMetric calculateSingleMetric(Segment segment, Map<UserCubeKey, Long> cube, LocalDate metricDate) {

        Predicate<UserCubeKey> segmentCondition = segmentConditionEvaluator.getCondition(segment);

        long userCount = cube.entrySet().stream()
                .filter(entry -> segmentCondition.test(entry.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();

        SegmentDailyMetric metric = new SegmentDailyMetric(segment.getId(), metricDate);
        metric.updateCount(userCount);
        metric.markCompleted();

        return metric;
    }
}
