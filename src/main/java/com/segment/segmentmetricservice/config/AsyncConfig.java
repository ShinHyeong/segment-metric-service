package com.segment.segmentmetricservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {

    /**
     * 배치 작업 전용 스레드 풀
     * Step 2. 세그먼트별 유저 수 병렬 계산에 사용됨
     *
     * 설계 근거:
     * - user-slave HikariCP 풀 사이즈(30)와 일치시켜 커넥션 대기 최소화
     * - core == max로 두어 동작 예측 가능
     * - chunk 사이즈(1000)만큼 큐 용량 확보로 백프레셔 자연 형성
     * - allowCoreThreadTimeOut으로 idle 시 메모리 절약
     */
    @Bean(name = "batchTaskExecutor")
    public Executor batchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(30);
        executor.setMaxPoolSize(30);
        executor.setQueueCapacity(1000);

        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);

        // 큐 가득 시 호출 스레드가 직접 실행 → 자연스러운 백프레셔
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // graceful shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.setThreadNamePrefix("Batch-Executor-");
        executor.initialize();
        return executor;
    }
}