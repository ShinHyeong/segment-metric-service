package com.segment.segmentmetricservice.service.batch.cube;

import com.segment.segmentmetricservice.aspect.TrackExecutionTime;
import com.segment.segmentmetricservice.domain.user.cube.UserCubeKey;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@Component
@Getter
@RequiredArgsConstructor
public class CubeBatchOrchestrator {
    private final CubeBuilderService cubeBuilderService;
    private final CubePersistenceService cubePersistenceService;
    private final CubeSegmentMetricService cubeSegmentMetricService;

    @Value("${batch.cube.page-size:1000}")
    private int pageSize;

    @Value("${batch.cube.batch-size:1000}")
    private int batchSize;

    @TrackExecutionTime(metricName = "cube.batch.total.duration",
                        tags = {"pageSize", "#target.pageSize", "batchSize", "#target.batchSize"})
    @Scheduled(cron="0 0 0 * * *")
    public void runDailyBatch(){

        Map<UserCubeKey, Long> cube = cubeBuilderService.build(pageSize);

        cubePersistenceService.persistAsync(cube, batchSize);

        LocalDate today = LocalDate.now();
        cubeSegmentMetricService.saveMetric(cube, today, pageSize, batchSize);

     }
}
