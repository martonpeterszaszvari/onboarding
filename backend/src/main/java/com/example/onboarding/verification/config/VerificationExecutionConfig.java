package com.example.onboarding.verification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class VerificationExecutionConfig {

    @Bean(name = "verificationTimer")
    public ThreadPoolTaskScheduler verificationTimer() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1); // Only coordinates when to start work; never calls Hunter.
        scheduler.setThreadNamePrefix("verification-timer-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }

    @Bean(name = "verificationExecutor")
    public ThreadPoolTaskExecutor verificationExecutor(
            @Value("${app.worker.concurrency:3}") int concurrency) {
        if (concurrency < 1) throw new IllegalArgumentException("Worker concurrency must be positive");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(0); // No unbounded in-memory queue: PostgreSQL is the queue.
        executor.setKeepAliveSeconds(30);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("verification-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
