package com.ecommerce.project.config;

import com.ecommerce.project.service.order.listener.OrderActivityLogListener;
import com.ecommerce.project.service.order.listener.OrderEmailListener;
import com.ecommerce.project.service.order.listener.OrderNotificationListener;
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
    @DisplayName("every order-lifecycle listener runs after commit AND off the request thread")
    void orderListenersAreAsyncAfterCommit() {
        Class<?>[] listeners = {
                OrderEmailListener.class, OrderNotificationListener.class, OrderActivityLogListener.class
        };

        int checked = 0;
        for (Class<?> listener : listeners) {
            for (Method method : listener.getDeclaredMethods()) {
                TransactionalEventListener onCommit = method.getAnnotation(TransactionalEventListener.class);
                if (onCommit == null) {
                    continue;
                }
                checked++;
                String where = listener.getSimpleName() + "." + method.getName();

                assertThat(onCommit.phase())
                        .as("%s must only fire after the order transaction commits", where)
                        .isEqualTo(TransactionPhase.AFTER_COMMIT);

                Async async = method.getAnnotation(Async.class);
                assertThat(async)
                        .as("%s must be @Async so a slow collaborator cannot block checkout", where)
                        .isNotNull();
                assertThat(async.value()).isEqualTo(AsyncConfig.ORDER_EVENT_EXECUTOR);
            }
        }

        assertThat(checked).as("expected the order listeners to expose event-listener methods").isGreaterThanOrEqualTo(3);
    }
}
