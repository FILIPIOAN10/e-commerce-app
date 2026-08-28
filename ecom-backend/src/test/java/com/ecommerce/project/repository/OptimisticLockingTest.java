package com.ecommerce.project.repository;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.model.Coupon;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.StockMovement;
import com.ecommerce.project.model.StockMovementReason;
import com.ecommerce.project.service.stock.StockLedgerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F4: @Version turns a lost update into a 409 instead of last-write-wins, and the
 * native coupon UPDATE and the stock ledger's UPDATE bump the version so an
 * entity save that raced them is rejected rather than silently clobbering their
 * change.
 * <p>
 * No {@code @Transactional} here on purpose — each repository call is its own
 * unit of work, so the two loads are detached copies and the version clash
 * actually surfaces on flush.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OptimisticLockingTest {

    @Autowired private CouponRepository couponRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockLedgerService stockLedgerService;
    @Autowired private PlatformTransactionManager txManager;

    /**
     * {@code @Modifying} queries need an active transaction, as they have in
     * production — and {@link StockLedgerService} is {@code MANDATORY}, so it
     * refuses to run without one at all.
     */
    private <T> T inTx(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(txManager).execute(status -> work.get());
    }

    private Coupon newCoupon(String code) {
        return Coupon.builder()
                .code(code)
                .discountPercent(10)
                .expiryDate(LocalDate.now().plusDays(30))
                .maxUses(100)
                .usedCount(0)
                .active(true)
                .build();
    }

    private Product newProduct(String name, int quantity) {
        Product p = new Product();
        p.setProductName(name);
        p.setDescription("A stocked product");
        p.setQuantity(quantity);
        return p;
    }

    @Test
    @DisplayName("two concurrent edits of the same coupon: the second save is rejected")
    void concurrentCouponEditConflicts() {
        Coupon saved = couponRepository.saveAndFlush(newCoupon("LOCK-A-" + System.nanoTime()));
        Long id = saved.getId();

        Coupon first = couponRepository.findById(id).orElseThrow();
        Coupon second = couponRepository.findById(id).orElseThrow();

        first.setMaxUses(999);
        couponRepository.saveAndFlush(first);

        second.setMaxUses(1);
        assertThatThrownBy(() -> couponRepository.saveAndFlush(second))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(couponRepository.findById(id).orElseThrow().getMaxUses()).isEqualTo(999);
    }

    @Test
    @DisplayName("tryConsume bumps the version: a stale coupon save afterwards is rejected")
    void tryConsumeInvalidatesStaleCopy() {
        Coupon saved = couponRepository.saveAndFlush(newCoupon("LOCK-B-" + System.nanoTime()));
        Long id = saved.getId();

        Coupon stale = couponRepository.findById(id).orElseThrow();

        assertThat(inTx(() -> couponRepository.tryConsume(id))).isEqualTo(1);

        stale.setActive(false);
        assertThatThrownBy(() -> couponRepository.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("a ledger sale bumps the version: a stale product save cannot restore the sold stock")
    void ledgerSaleInvalidatesStaleCopy() {
        Product saved = productRepository.saveAndFlush(newProduct("Lock Widget " + System.nanoTime(), 5));
        Long id = saved.getProductId();

        Product staleAdminCopy = productRepository.findById(id).orElseThrow();

        // The production path: one statement takes the stock and bumps the
        // version, which is what makes the stale copy below unsavable.
        StockMovement sale = inTx(() -> stockLedgerService.tryApplyAndRecord(
                id, -2, StockMovementReason.SALE, "TEST", id, null)).orElseThrow();
        assertThat(sale.getBalanceAfter()).isEqualTo(3);

        // The admin edits an unrelated field from a form loaded before the sale.
        staleAdminCopy.setDescription("edited description");
        staleAdminCopy.setQuantity(5); // their stale form still shows 5

        assertThatThrownBy(() -> productRepository.saveAndFlush(staleAdminCopy))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // Stock stays at the sold-down value, not the admin's stale 5.
        assertThat(productRepository.findById(id).orElseThrow().getQuantity()).isEqualTo(3);
    }
}
