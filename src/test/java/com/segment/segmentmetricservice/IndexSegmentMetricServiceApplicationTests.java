package com.segment.segmentmetricservice;

import com.segment.segmentmetricservice.service.batch.index.IndexBatchOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class IndexSegmentMetricServiceApplicationTests {

    @Autowired
    private IndexBatchOrchestrator indexBatchOchestrator;

    @Test
    @DisplayName("배치 수동 실행 테스트")
    void triggerBatch() {
        // 배치를 강제로 실행합니다.
        indexBatchOchestrator.executeDailyBatch();

        System.out.println("배치 실행 완료");
    }

}
