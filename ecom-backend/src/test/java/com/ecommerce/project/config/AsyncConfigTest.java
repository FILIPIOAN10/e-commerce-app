package com.ecommerce.project.config;

import com.ecommerce.project.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AsyncConfig — bounded executor for order side effects")
class AsyncConfigTest {

    private ThreadPoolTaskExecutor buildExecutor() {
        AsyncConfig config = new AsyncConfig();
        ReflectionTestUtils.setField(config, "corePoolSize", 2);
        ReflectionTestUtils.setField(config, "maxPoolSize", 5);
        ReflectionTestUtils.setField(config, "queueCapacity", 100);
        ReflectionTestUtils.setField(config, "awaitTerminationSeconds", 30);
        return config.orderEventExecutor();
    }

    @Test
    @DisplayName("pool is bounded in core, max and queue")
    void poolIsBounded() {
        ThreadPoolTaskExecutor executor = buildExecutor();
        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(5);
            // Fresh, empty bounded queue: remaining capacity == configured capacity.
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(100);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("order-events-");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("full pool falls back to running on the caller, never to unbounded growth")
    void rejectionPolicyIsCallerRuns() {
        ThreadPoolTaskExecutor executor = buildExecutor();
        try {
            ThreadPoolExecutor delegate = executor.getThreadPoolExecutor();
            assertThat(delegate.getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("a void @Async failure is logged, not swallowed")
    void uncaughtExceptionHandlerIsProvided() {
        assertThat(new AsyncConfig().getAsyncUncaughtExceptionHandler()).isNotNull();
    }

    @Test
    @DisplayName("order-lifecycle listeners run after commit AND off the request thread")
    void orderListenersAreAsyncAfterCommit() {
        for (String name : new String[]{"onOrderPlaced", "onOrderStatusUpdated"}) {
            Method method = findMethod(name);

            Async async = method.getAnnotation(Async.class);
            assertThat(async)
                    .as("%s must be @Async so a slow SMTP server cannot block checkout", name)
                    .isNotNull();
            assertThat(async.value()).isEqualTo(AsyncConfig.ORDER_EVENT_EXECUTOR);

            TransactionalEventListener listener = method.getAnnotation(TransactionalEventListener.class);
            assertThat(listener)
                    .as("%s must only fire after the order transaction commits", name)
                    .isNotNull();
            assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        }
    }

    private Method findMethod(String name) {
        for (Method m : OrderServiceImpl.class.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new AssertionError("method not found: " + name);
    }
}
