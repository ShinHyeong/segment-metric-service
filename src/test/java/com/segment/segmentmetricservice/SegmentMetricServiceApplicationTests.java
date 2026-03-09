package com.segment.segmentmetricservice;

import com.segment.segmentmetricservice.service.batch.DailyMetricBatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SegmentMetricServiceApplicationTests {

    @Autowired
    private DailyMetricBatchService dailyMetricBatchService;

    @Test
    @DisplayName("배치 수동 실행 테스트")
    void triggerBatch() {
        // 배치를 강제로 실행합니다.
        dailyMetricBatchService.executeDailyBatch();

        System.out.println("배치 실행 완료");
    }

}
