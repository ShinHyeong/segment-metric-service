package com.segment.segmentmetricservice.service.batch.cube;

import com.segment.segmentmetricservice.aspect.TrackExecutionTime;
import com.segment.segmentmetricservice.domain.segment.SegmentRepository;
import com.segment.segmentmetricservice.domain.user.*;
import com.segment.segmentmetricservice.domain.user.cube.BucketRange;
import com.segment.segmentmetricservice.domain.user.cube.UserCubeBucketContext;
import com.segment.segmentmetricservice.domain.user.cube.UserCubeKey;
import com.segment.segmentmetricservice.domain.user.cube.UserCubeProjection;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 인메모리 큐브 생성
 * : 최하위 경계 조합의 user_count를 어플리케이션 메모리에서 집계한 결과
 */
@Slf4j
@Service
@Getter
@RequiredArgsConstructor
public class CubeBuilderService {

    private final UserRepository userRepository;
    private final SegmentRepository segmentRepository;

    private static final int MIN_POSSIBLE_VALUE = 0;

    @TrackExecutionTime(metricName = "cube.build.duration",
                        tags = {"pageSize", "#pageSize"})
    public Map<UserCubeKey, Long> build(int pageSize) {
        log.info("== 인메모리 큐브 생성 ==");
        UserCubeBucketContext bucketContext = initializeBucketContext();

        return aggregateUserCountByCubeKey(bucketContext, pageSize);
    }

    private UserCubeBucketContext initializeBucketContext(){
        TreeMap<Integer, BucketRange> ageBucketMap = createBucketMap(segmentRepository.getAgeCutPoints());
        log.info(">>> 메모리에 탑재된 AGE 버킷 총 개수: {}", ageBucketMap.size());

        TreeMap<Integer, BucketRange> orderCountBucketMap = createBucketMap(segmentRepository.getOrderCountCutPoints());
        log.info(">>> 메모리에 탑재된 ORDER_COUNT 버킷 총 개수: {}", orderCountBucketMap.size());

        return new UserCubeBucketContext(ageBucketMap,  orderCountBucketMap);
    }

    private TreeMap<Integer, BucketRange> createBucketMap(int[] cutPoints){
        // 칼럼별 속한 버킷을 빠르게 탐색하기 위해 JOIN 연산대신 이진 탐색 트리(TreeMap) 사용
        TreeMap<Integer, BucketRange> bucketMap = new TreeMap<>();

        if (cutPoints == null || cutPoints.length==0) return bucketMap;

        // cutPoint 최솟값보다 작은 데이터를 담기 위한 버킷
        int firstCutPoint = cutPoints[0];
        bucketMap.put(0, new BucketRange(MIN_POSSIBLE_VALUE, firstCutPoint));

        for(int i=0; i<cutPoints.length; i++){
            int startVal = cutPoints[i];
            int endVal = (i==cutPoints.length-1) ? Integer.MAX_VALUE : cutPoints[i+1];
            bucketMap.put(startVal, new BucketRange(startVal,endVal));
        }
        return bucketMap;
    }

    private Map<UserCubeKey, Long> aggregateUserCountByCubeKey(UserCubeBucketContext bucketContext, int pageSize){
        long totalProcessed = 0L;

        Map<UserCubeKey, Long> cube = new HashMap<>();

        // OOM 방지 및 DB 를 위해 전체 유저 정보를 페이징 단위로 가져옴
        long lastId = 0L;
        while(true){
            List<UserCubeProjection> usersChunk = userRepository.findProjectionsAfterId(lastId, pageSize);

            if(usersChunk.isEmpty()) break;

            for (UserCubeProjection user : usersChunk){
                UserCubeKey key = extractCubeKey(user, bucketContext);
                // DB 부하 분산을 위해 GROUP BY 연산 대신 어플리케이션에서 user_count 집계
                cube.merge(key, 1L, Long::sum);
            }

            totalProcessed += usersChunk.size();
            log.debug("인메모리 큐브 생성 진행중... 현재까지 처리된 유저 수: {}", totalProcessed);

            lastId = usersChunk.get(usersChunk.size() - 1).id();
        }

        return cube;
    }

    private UserCubeKey extractCubeKey(UserCubeProjection user,
                                       UserCubeBucketContext context){

        BucketRange ageBucket = matchBucket(context.ageBucketMap(), user.age())
                .orElseThrow(() -> new IllegalArgumentException("user_id = "+user.id()+", age = "+user.age()+" 에 일치하는 버킷을 찾을 수 없습니다"));
        BucketRange orderCountBucket = matchBucket(context.orderCountBucketMap(), user.orderCount())
                .orElseThrow(() -> new IllegalArgumentException("user_id = "+user.id()+", order_count = "+user.orderCount()+" 에 일치하는 버킷을 찾을 수 없습니다"));

        return new UserCubeKey(
                user.location(), user.gender(),
                ageBucket.startVal(), ageBucket.endVal(),
                orderCountBucket.startVal(), orderCountBucket.endVal()
        );
    }

    private Optional<BucketRange> matchBucket(TreeMap<Integer, BucketRange> bucketMap, int value){
        Map.Entry<Integer, BucketRange> entry = bucketMap.floorEntry(value);
        return Optional.ofNullable(entry).map(Map.Entry::getValue);
    }
}