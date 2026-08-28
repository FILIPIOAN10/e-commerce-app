package com.ecommerce.project.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Enables {@code @Async} and defines the executor that order-lifecycle side
 * effects run on (confirmation email, admin notification, activity log — see
 * {@code OrderServiceImpl}'s {@code @TransactionalEventListener} methods).
 * <p>
 * The pool is deliberately bounded: fixed core and max sizes and a fixed queue,
 * with a {@link ThreadPoolExecutor.CallerRunsPolicy} fallback. When no executor
 * is defined, Spring's default for {@code @Async} is a
 * {@code SimpleAsyncTaskExecutor} that starts a fresh thread per task with no
 * ceiling — a slow or stuck SMTP server would then spawn threads until the JVM
 * runs out of memory. Here, once the queue is full and every max thread is busy,
 * the caller (the request thread that committed the order) runs the task itself:
 * latency degrades, nothing is dropped, and thread count cannot grow without
 * bound.
 * <p>
 * A {@code void} {@code @Async} method has no {@code Future} to carry a failure,
 * so an uncaught exception would otherwise vanish;
 * {@link #getAsyncUncaughtExceptionHandler()} logs it. Making such a failure
 * <em>recoverable</em> (retry / dead-letter) is a separate change — see the
 * transactional-outbox finding.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /** Bean name of the executor for order-lifecycle side effects. */
    public static final String ORDER_EVENT_EXECUTOR = "orderEventExecutor";

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Value("${app.async.order-events.core-pool-size:2}")
    private int corePoolSize;

    @Value("${app.async.order-events.max-pool-size:5}")
    private int maxPoolSize;

    @Value("${app.async.order-events.queue-capacity:100}")
    private int queueCapacity;

    @Value("${app.async.order-events.await-termination-seconds:30}")
    private int awaitTerminationSeconds;

    @Bean(name = ORDER_EVENT_EXECUTOR)
    public ThreadPoolTaskExecutor orderEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("order-events-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // The app runs with server.shutdown=graceful; let in-flight side effects
        // finish rather than being cut off mid-send.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error(
                "Unhandled exception in @Async {}.{}(): {}",
                method.getDeclaringClass().getSimpleName(), method.getName(), ex.getMessage(), ex);
    }
}
