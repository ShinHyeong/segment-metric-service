package com.segment.segmentmetricservice.controller;

import com.segment.segmentmetricservice.service.batch.DailyMetricBatchService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자정까지 기다리지 않고 원할 때 즉시 일일 배치를 실행하기 위한 수동 트리거 API.
 * (부하 테스트 및 로컬 환경용)
 */

@RestController
@RequestMapping("/admin")
@Profile("!prod")
public class AdminController {

    private final DailyMetricBatchService batchService;

    public AdminController(DailyMetricBatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping("/batch/run")
    public ResponseEntity<String> runBatch() {
        long start = System.currentTimeMillis();
        batchService.executeDailyBatch();
        long elapsed = System.currentTimeMillis() - start;
        return ResponseEntity.ok("Batch completed in " + elapsed + " ms");
    }
}