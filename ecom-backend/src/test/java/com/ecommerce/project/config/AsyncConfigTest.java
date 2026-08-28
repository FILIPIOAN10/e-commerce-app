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
    @DisplayName("effect-performing order listeners run after commit AND off the request thread")
    void effectListenersAreAsyncAfterCommit() {
        // The notification and activity-log listeners still perform their effect
        // directly, so they must run after the commit and off the request thread.
        // (The email listener is different — see enqueueListenerRunsInTransaction.)
        Class<?>[] listeners = { OrderNotificationListener.class, OrderActivityLogListener.class };

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

        assertThat(checked).as("expected the effect listeners to expose event-listener methods").isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("the email listener enqueues in the checkout transaction: BEFORE_COMMIT and synchronous")
    void enqueueListenerRunsInTransaction() {
        int checked = 0;
        for (Method method : OrderEmailListener.class.getDeclaredMethods()) {
            TransactionalEventListener listener = method.getAnnotation(TransactionalEventListener.class);
            if (listener == null) {
                continue;
            }
            checked++;
            String where = "OrderEmailListener." + method.getName();

            // The outbox row must be written in the order's own transaction, so
            // it commits with the order or not at all.
            assertThat(listener.phase())
                    .as("%s must run before the order commits so its outbox row is part of that commit", where)
                    .isEqualTo(TransactionPhase.BEFORE_COMMIT);
            assertThat(method.getAnnotation(Async.class))
                    .as("%s must be synchronous — an async enqueue would not be in the transaction", where)
                    .isNull();
        }

        assertThat(checked).as("expected OrderEmailListener to expose event-listener methods").isGreaterThanOrEqualTo(2);
    }
}
