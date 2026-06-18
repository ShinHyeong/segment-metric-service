package com.segment.segmentmetricservice.service.batch.cube;

import com.segment.segmentmetricservice.aspect.TrackExecutionTime;
import com.segment.segmentmetricservice.domain.user.cube.UserCubeJdbcRepository;
import com.segment.segmentmetricservice.domain.user.cube.UserCubeKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class CubePersistenceService {
    private final UserCubeJdbcRepository userCubeJdbcRepository;

    @TrackExecutionTime(metricName = "cube.persist.duration",
                        tags = {"batchSize", "#batchSize"})
    @Async("cubePersistExecutor")
    public CompletableFuture<Void> persistAsync(Map<UserCubeKey, Long> cube, int batchSize){
        try{
            log.info("== 인메모리 큐브 DB INSERT 시작 (대상 건수: {}) ==", cube.size());
            userCubeJdbcRepository.replaceCubeData(cube, batchSize);
            log.info("== 인메모리 큐브 DB INSERT 완료 ==");

            return CompletableFuture.completedFuture(null);
        } catch(Exception e) {
            log.error("인메모리 큐브 DB INSERT 실패", e);
            return CompletableFuture.failedFuture(e);
        }
    }

}
