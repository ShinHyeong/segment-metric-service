package com.segment.segmentmetricservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync // @Async 어노테이션 활성화
public class AsyncConfig {

    /**
     * 배치 작업 전용 스레드 풀
     * Step 2. 세그먼트별 유저 수 병렬 계산에 사용됨
     */
    @Bean(name = "batchTaskExecutor")
    public Executor batchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 설계에서 가정한 동시 커넥션 수(100개)에 맞춰 설정
        executor.setCorePoolSize(50);   // 기본 스레드 수
        executor.setMaxPoolSize(100);   // 최대 스레드 수 (부하 시 확장)

        // 큐 용량: 모든 스레드가 바쁠 때 대기열 크기
        // 세그먼트가 수만 개이므로 넉넉하게 잡음
        executor.setQueueCapacity(500);

        executor.setThreadNamePrefix("Batch-Executor-");
        executor.initialize();
        return executor;
    }
}