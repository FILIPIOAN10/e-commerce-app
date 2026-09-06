package com.ecommerce.project.model;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.service.pricing.Money;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 5, the end of the money migration: the last four columns that carried
 * {@code DOUBLE PRECISION} are now {@code NUMERIC(12,2)}, and the amounts among
 * them round-trip to the cent.
 *
 * <p>Two of the four are percentages ({@code bundles.discount_percentage},
 * {@code promo_campaigns.discount_percent}) rather than amounts, but they follow
 * the same rule V25 set for {@code products.discount}: a percentage that gets
 * multiplied into a price must be exact decimal too, and {@code 12.50} has to be
 * storable as {@code 12.50}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class LegacyMoneyFieldsNumericTest {

    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager txManager;

    @Test
    @DisplayName("all four legacy money columns are decimal, not floating point")
    void columnsAreDecimal() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        assertThat(columnType(tx, "bundles", "discount_percentage")).isEqualTo("numeric");
        assertThat(columnType(tx, "promo_campaigns", "discount_percent")).isEqualTo("numeric");
        assertThat(columnType(tx, "subscription_plans", "amount")).isEqualTo("numeric");
        assertThat(columnType(tx, "return_requests", "refund_amount")).isEqualTo("numeric");
    }

    @Test
    @DisplayName("a refund amount survives the round trip to the cent")
    void refundAmountRoundTrips() {
        TransactionTemplate tx = new TransactionTemplate(txManager);

        Long id = tx.execute(status -> {
            ReturnRequest r = new ReturnRequest();
            r.setOrderId(1L);
            r.setUserEmail("legacy-money@example.com");
            r.setReason("fixture");
            r.setStatus("REQUESTED");
            r.setRequestedAt(LocalDateTime.now());
            r.setRefundAmount(new BigDecimal("84.99"));
            entityManager.persist(r);
            return r.getId();
        });

        ReturnRequest reloaded = tx.execute(status -> {
            entityManager.clear();
            return entityManager.find(ReturnRequest.class, id);
        });

        assertThat(reloaded.getRefundAmount()).isEqualByComparingTo("84.99");
        assertThat(reloaded.getRefundAmount().scale()).isEqualTo(2);

        tx.executeWithoutResult(status -> entityManager.createNativeQuery(
                "DELETE FROM return_requests WHERE user_email = 'legacy-money@example.com'").executeUpdate());
    }

    @Test
    @DisplayName("a subscription price round-trips exactly and yields the right cents for Stripe")
    void subscriptionAmountRoundTrips() {
        TransactionTemplate tx = new TransactionTemplate(txManager);

        Long id = tx.execute(status -> {
            SubscriptionPlan plan = new SubscriptionPlan();
            plan.setName("legacy-money-plan");
            plan.setInterval("month");
            plan.setAmount(new BigDecimal("19.99"));
            entityManager.persist(plan);
            return plan.getPlanId();
        });

        SubscriptionPlan reloaded = tx.execute(status -> {
            entityManager.clear();
            return entityManager.find(SubscriptionPlan.class, id);
        });

        assertThat(reloaded.getAmount()).isEqualByComparingTo("19.99");
        assertThat(Money.of(reloaded.getAmount()).toCents())
                .as("the figure sent to Stripe, taken with Money.toCents() not (long)(x*100)")
                .isEqualTo(1999L);

        tx.executeWithoutResult(status -> entityManager.createNativeQuery(
                "DELETE FROM subscription_plans WHERE name = 'legacy-money-plan'").executeUpdate());
    }

    @Test
    @DisplayName("a bundle can hold a fractional discount percentage")
    void bundlePercentageIsExactDecimal() {
        TransactionTemplate tx = new TransactionTemplate(txManager);

        Long id = tx.execute(status -> {
            Bundle bundle = new Bundle();
            bundle.setName("legacy-money-bundle");
            bundle.setDiscountPercentage(new BigDecimal("12.50"));
            entityManager.persist(bundle);
            return bundle.getBundleId();
        });

        Bundle reloaded = tx.execute(status -> {
            entityManager.clear();
            return entityManager.find(Bundle.class, id);
        });

        assertThat(reloaded.getDiscountPercentage()).isEqualByComparingTo("12.50");

        tx.executeWithoutResult(status -> entityManager.createNativeQuery(
                "DELETE FROM bundles WHERE name = 'legacy-money-bundle'").executeUpdate());
    }

    private String columnType(TransactionTemplate tx, String table, String column) {
        return tx.execute(status -> (String) entityManager
                .createNativeQuery("SELECT data_type FROM information_schema.columns "
                                   + "WHERE table_name = :t AND column_name = :c")
                .setParameter("t", table)
                .setParameter("c", column)
                .getSingleResult());
    }
}
