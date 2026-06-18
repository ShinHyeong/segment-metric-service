package com.segment.segmentmetricservice.controller;

import com.segment.segmentmetricservice.service.batch.index.IndexBatchOrchestrator;
import com.segment.segmentmetricservice.service.batch.cube.CubeBatchOrchestrator;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@RequestMapping("/admin")
@Profile("!prod")
public class AdminController {

    private final IndexBatchOrchestrator batchService;
    private final CubeBatchOrchestrator cubeBatchOrchestrator;

    @PostMapping("/batch/run")
    public ResponseEntity<String> runBatch() {
        long start = System.currentTimeMillis();
        //batchService.executeDailyBatch();
        cubeBatchOrchestrator.runDailyBatch();
        long elapsed = System.currentTimeMillis() - start;
        return ResponseEntity.ok("Batch completed in " + elapsed + " ms");
    }
}