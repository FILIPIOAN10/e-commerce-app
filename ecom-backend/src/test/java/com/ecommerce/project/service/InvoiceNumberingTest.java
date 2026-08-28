package com.ecommerce.project.service;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.model.Invoice;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.repository.InvoiceRepository;
import com.ecommerce.project.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §3 (Critical): fiscal invoice numbers must be gapless within their year — a
 * rolled-back checkout must not burn a number. Backed by
 * {@link InvoiceNumberService}: a per-year counter row incremented inside the
 * issuing transaction, read under {@code SELECT ... FOR UPDATE}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class InvoiceNumberingTest {

    @Autowired private InvoiceNumberService invoiceNumberService;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private EntityManager entityManager;

    /** Unique per run so cleanup only removes this test's committed rows. */
    private final String emailTag = "inv-" + UUID.randomUUID() + "-";

    @AfterEach
    void cleanUp() {
        // These tests commit orders + invoices (they need real transactions), so
        // remove them or later tests that count orders will see the leftovers.
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            entityManager.createNativeQuery(
                    "DELETE FROM invoices WHERE order_id IN (SELECT id FROM orders WHERE email LIKE :tag)")
                    .setParameter("tag", emailTag + "%").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM orders WHERE email LIKE :tag")
                    .setParameter("tag", emailTag + "%").executeUpdate();
        });
    }

    private Order newOrder() {
        Order order = new Order();
        order.setEmail(emailTag + System.nanoTime() + "@example.com");
        order.setOrderDate(LocalDate.now());
        order.setOrderStatus("Placed");
        order.setTotalAmount(42.0);
        return orderRepository.save(order);
    }

    @Test
    @DisplayName("issuing twice for the same order returns the same number and creates one row")
    void issuanceIsIdempotent() {
        Order order = newOrder();
        long invoicesBefore = invoiceRepository.count();

        Invoice first = invoiceNumberService.issueFor(order.getId());
        Invoice again = invoiceNumberService.issueFor(order.getId());

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(again.getInvoiceNumber()).isEqualTo(first.getInvoiceNumber());
        assertThat(again.getSequenceNo()).isEqualTo(first.getSequenceNo());
        assertThat(invoiceRepository.count()).isEqualTo(invoicesBefore + 1);
        assertThat(first.getInvoiceNumber()).isEqualTo(LocalDate.now().getYear() + "-"
                + String.format("%06d", first.getSequenceNo()));
    }

    @Test
    @DisplayName("a checkout rolled back mid-issue returns its number to the pool — no gap")
    void rolledBackIssuanceLeavesNoGap() {
        Order a = newOrder();
        Order b = newOrder();
        Order c = newOrder();

        long firstSeq = invoiceNumberService.issueFor(a.getId()).getSequenceNo();

        // Issue for b, then abort the transaction it was issued in.
        TransactionTemplate tx = new TransactionTemplate(txManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            invoiceNumberService.issueFor(b.getId());
            throw new RuntimeException("boom — roll back after issuing b");
        })).isInstanceOf(RuntimeException.class);

        assertThat(invoiceRepository.existsByOrderId(b.getId())).isFalse();

        long thirdSeq = invoiceNumberService.issueFor(c.getId()).getSequenceNo();

        // c takes the number b's rolled-back transaction gave back, not the one after it.
        assertThat(thirdSeq).isEqualTo(firstSeq + 1);
    }

    @Test
    @DisplayName("concurrent issuance hands out distinct, contiguous numbers")
    void concurrentIssuanceIsSerialised() throws Exception {
        int concurrency = 12;

        long baseline = invoiceNumberService.issueFor(newOrder().getId()).getSequenceNo();

        List<Long> orderIds = new ArrayList<>();
        for (int i = 0; i < concurrency; i++) {
            orderIds.add(newOrder().getId());
        }

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        try {
            List<Callable<Long>> tasks = orderIds.stream()
                    .<Callable<Long>>map(id -> () -> invoiceNumberService.issueFor(id).getSequenceNo())
                    .collect(Collectors.toList());

            List<Future<Long>> futures = pool.invokeAll(tasks);
            List<Long> seqs = new ArrayList<>();
            for (Future<Long> f : futures) {
                seqs.add(f.get());
            }

            Set<Long> distinct = Set.copyOf(seqs);
            assertThat(distinct).hasSize(concurrency);
            assertThat(distinct).allMatch(s -> s > baseline);
            assertThat(distinct.stream().min(Long::compareTo)).contains(baseline + 1);
            assertThat(distinct.stream().max(Long::compareTo)).contains(baseline + concurrency);
        } finally {
            pool.shutdownNow();
        }
    }
}
