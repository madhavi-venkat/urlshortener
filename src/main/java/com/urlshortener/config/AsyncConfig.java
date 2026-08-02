package com.urlshortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Executor for asynchronous click recording.
 *
 * <p>The redirect path must never block on analytics. Click events are handed to this
 * bounded pool; if the pool and its queue are saturated we DROP the analytics event
 * (CallerRunsPolicy would push work back onto the redirect thread — the opposite of what
 * we want). Dropping a click under extreme load is an acceptable, deliberate trade-off:
 * analytics is best-effort, correctness of the redirect is not.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "clickExecutor")
    public Executor clickExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("click-");
        // Under saturation, discard rather than block the redirect thread.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
