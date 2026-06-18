package com.segment.segmentmetricservice.config.async;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${app.async.segment-count.core-size}") private int segmentCountCoreSize;
    @Value("${app.async.segment-count.max-size}") private int segmentCountMaxSize;
    @Value("${app.async.segment-count.queue-capacity}") private int segmentCountQueueCapacity;

    /**
     * 1000개의 DB COUNT쿼리를 30스레드가 나눠서 보냄
     */
    @Bean(name = "segmentCountExecutor")
    public Executor segmentCountExecutor() {
        return createTaskExecutor(segmentCountCoreSize,segmentCountMaxSize,
                segmentCountQueueCapacity, "Segment-Count-");
    }

    @Value("${app.async.cube-persist.core-size}") private int cubePersistCoreSize;
    @Value("${app.async.cube-persist.max-size}") private int cubePersistMaxSize;
    @Value("${app.async.cube-persist.queue-capacity}") private int cubePersistQueueCapacity;

    /**
     * 인메모리 큐브를 DB로 bulk INSERT
     */
    @Bean(name = "cubePersistExecutor")
    public Executor cubePersistExecutor() {
        return createTaskExecutor(cubePersistCoreSize,cubePersistMaxSize,
                cubePersistQueueCapacity,"Cube-Persist-");
    }

    private ThreadPoolTaskExecutor createTaskExecutor(int corePoolSize, int maxPoolSize,
                                                      int queueCapacity, String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);

        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);

        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();
        return executor;
    }
}