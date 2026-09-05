package com.ecommerce.project.service.promo;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.PromoCampaign;
import com.ecommerce.project.model.PromoCampaignProduct;
import com.ecommerce.project.payload.PromoCampaignDTO;
import com.ecommerce.project.service.PromoCampaignService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The promo campaign sweep: a campaign's discount goes onto its products when it
 * starts and comes back off when it stops, exactly once each.
 *
 * <p>The bug these cover is that the sweep used to push prices one way only.
 * When {@code end_time} passed, the campaign simply stopped being selected and
 * the promotional price stayed on the product forever — invisibly, since the
 * campaign no longer showed as active anywhere. Deleting or re-scoping a live
 * campaign was worse: it destroyed the link rows that were the only record of
 * what the price had been.
 *
 * <p>No injected {@code Clock}: the sweep decides on
 * {@code startTime <= now < endTime}, so moving a campaign's window relative to
 * real time is equivalent to moving time relative to a fixed window, and needs
 * no seam in production code. {@code app.promo.enabled=false} in the test
 * profile keeps the scheduled job from racing these assertions — the service is
 * driven directly, one pass at a time.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PromoCampaignSweepTest {

    private static final DateTimeFormatter DTO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Autowired private PromoCampaignService promoCampaignService;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager txManager;

    private final String tag = "promo" + Long.toUnsignedString(System.nanoTime(), 36);

    /** No prior discount — the common case. */
    private Long plainProductId;
    /** Already discounted 10% before any campaign — the case that must survive a revert. */
    private Long discountedProductId;
    private Long categoryId;

    @BeforeEach
    void seed() {
        alignIdentitySequences();
        tx().executeWithoutResult(status -> {
            Category category = new Category();
            category.setCategoryName(tag + "-cat");
            entityManager.persist(category);
            categoryId = category.getCategoryId();

            plainProductId = persistProduct(category, tag + "-plain",
                    new BigDecimal("29.99"), new BigDecimal("0.00"), new BigDecimal("29.99"));
            discountedProductId = persistProduct(category, tag + "-discounted",
                    new BigDecimal("89.99"), new BigDecimal("10.00"), new BigDecimal("80.99"));
        });
    }

    @Test
    @DisplayName("applying a campaign discounts its products and records what it replaced")
    void applyPushesDiscountAndRemembersTheOriginal() {
        Long campaignId = persistCampaign(30.0, minutesFromNow(-5), minutesFromNow(60), true);

        promoCampaignService.applyActiveCampaigns();

        assertThat(appliedFlag(campaignId)).isTrue();
        assertDiscount(plainProductId, "30.00", "20.99");
        assertDiscount(discountedProductId, "30.00", "62.99");

        // Without this the revert has nothing to restore to.
        assertThat(originalDiscounts(campaignId))
                .containsExactlyInAnyOrder(new BigDecimal("0.00"), new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("a campaign that has ended puts every price back where it found it")
    void revertRestoresTheOriginalDiscountWhenTheCampaignEnds() {
        Long campaignId = persistCampaign(30.0, minutesFromNow(-5), minutesFromNow(60), true);
        promoCampaignService.applyActiveCampaigns();
        assertThat(appliedFlag(campaignId)).isTrue();

        endCampaignNow(campaignId);
        promoCampaignService.applyActiveCampaigns();

        assertThat(appliedFlag(campaignId)).isFalse();
        assertDiscount(plainProductId, "0.00", "29.99");
        // The point of the whole exercise: back to its own 10%, not to zero.
        assertDiscount(discountedProductId, "10.00", "80.99");
        assertThat(originalDiscounts(campaignId)).containsOnlyNulls();
    }

    @Test
    @DisplayName("switching a campaign off reverts it just like expiry does")
    void revertRestoresWhenTheCampaignIsDeactivated() {
        Long campaignId = persistCampaign(30.0, minutesFromNow(-5), minutesFromNow(60), true);
        promoCampaignService.applyActiveCampaigns();

        tx().executeWithoutResult(status -> entityManager
                .createNativeQuery("UPDATE promo_campaigns SET active = false WHERE campaign_id = :id")
                .setParameter("id", campaignId).executeUpdate());
        promoCampaignService.applyActiveCampaigns();

        assertThat(appliedFlag(campaignId)).isFalse();
        assertDiscount(discountedProductId, "10.00", "80.99");
    }

    @Test
    @DisplayName("a sweep with nothing to do writes nothing")
    void anIdleSweepDoesNotTouchProducts() {
        persistCampaign(30.0, minutesFromNow(-5), minutesFromNow(60), true);
        promoCampaignService.applyActiveCampaigns();

        long plainVersion = version(plainProductId);
        long discountedVersion = version(discountedProductId);

        // The old sweep rewrote every product of every running campaign on every
        // pass, bumping @Version and churning WAL for values that had not moved.
        promoCampaignService.applyActiveCampaigns();
        promoCampaignService.applyActiveCampaigns();

        assertThat(version(plainProductId)).isEqualTo(plainVersion);
        assertThat(version(discountedProductId)).isEqualTo(discountedVersion);
    }

    @Test
    @DisplayName("deleting a live campaign releases its products before the links go")
    void deletingALiveCampaignRestoresPrices() {
        Long campaignId = persistCampaign(30.0, minutesFromNow(-5), minutesFromNow(60), true);
        promoCampaignService.applyActiveCampaigns();
        assertDiscount(discountedProductId, "30.00", "62.99");

        promoCampaignService.deleteCampaign(campaignId);

        // deleteByPromoCampaignId destroys the only record of the prior price, so
        // reverting has to happen first or the discount is stranded for good.
        assertDiscount(plainProductId, "0.00", "29.99");
        assertDiscount(discountedProductId, "10.00", "80.99");
    }

    @Test
    @DisplayName("re-scoping a live campaign releases its products before replacing the links")
    void updatingALiveCampaignRestoresPricesFirst() {
        Long campaignId = persistCampaign(30.0, minutesFromNow(-5), minutesFromNow(60), true);
        promoCampaignService.applyActiveCampaigns();

        // Same campaign, now only covering the plain product, at 50%.
        PromoCampaignDTO dto = new PromoCampaignDTO();
        dto.setName(tag + "-campaign");
        dto.setDiscountPercent(50.0);
        dto.setStartTime(minutesFromNow(-5).format(DTO_FORMAT));
        dto.setEndTime(minutesFromNow(60).format(DTO_FORMAT));
        dto.setActive(true);
        dto.setProductIds(List.of(plainProductId));
        promoCampaignService.updateCampaign(campaignId, dto);

        // Dropped from the campaign, so it must be back on its own terms.
        assertDiscount(discountedProductId, "10.00", "80.99");
        assertThat(appliedFlag(campaignId)).isFalse();

        // Still in scope, so the next pass re-applies it under the new percentage.
        promoCampaignService.applyActiveCampaigns();
        assertThat(appliedFlag(campaignId)).isTrue();
        assertDiscount(plainProductId, "50.00", "15.00");
        assertDiscount(discountedProductId, "10.00", "80.99");
    }

    // ---------- fixture helpers ----------

    private TransactionTemplate tx() {
        return new TransactionTemplate(txManager);
    }

    private LocalDateTime minutesFromNow(int minutes) {
        return LocalDateTime.now().plusMinutes(minutes).withSecond(0).withNano(0);
    }

    private Long persistProduct(Category category, String name,
                                BigDecimal price, BigDecimal discount, BigDecimal specialPrice) {
        Product product = new Product();
        product.setProductName(name);
        product.setDescription("promo sweep fixture");
        product.setQuantity(10);
        product.setPrice(price);
        product.setDiscount(discount);
        product.setSpecialPrice(specialPrice);
        product.setCategory(category);
        entityManager.persist(product);
        return product.getProductId();
    }

    private Long persistCampaign(double percent, LocalDateTime start, LocalDateTime end, boolean active) {
        return tx().execute(status -> {
            PromoCampaign campaign = new PromoCampaign();
            campaign.setName(tag + "-campaign");
            campaign.setDiscountPercent(percent);
            campaign.setStartTime(start);
            campaign.setEndTime(end);
            campaign.setActive(active);
            campaign.setApplied(false);
            entityManager.persist(campaign);

            for (Long productId : List.of(plainProductId, discountedProductId)) {
                PromoCampaignProduct link = new PromoCampaignProduct();
                link.setPromoCampaign(campaign);
                link.setProduct(entityManager.getReference(Product.class, productId));
                entityManager.persist(link);
            }
            return campaign.getId();
        });
    }

    private void endCampaignNow(Long campaignId) {
        tx().executeWithoutResult(status -> entityManager
                .createNativeQuery("UPDATE promo_campaigns SET end_time = now() - INTERVAL '1 minute' "
                                 + "WHERE campaign_id = :id")
                .setParameter("id", campaignId).executeUpdate());
    }

    // ---------- assertions read through the database, not a stale context ----------

    private void assertDiscount(Long productId, String expectedDiscount, String expectedSpecialPrice) {
        tx().executeWithoutResult(status -> {
            entityManager.clear();
            Object[] row = (Object[]) entityManager
                    .createNativeQuery("SELECT discount, special_price FROM products WHERE product_id = :id")
                    .setParameter("id", productId).getSingleResult();
            assertThat((BigDecimal) row[0])
                    .as("discount on product %s", productId)
                    .isEqualByComparingTo(expectedDiscount);
            assertThat((BigDecimal) row[1])
                    .as("special price on product %s", productId)
                    .isEqualByComparingTo(expectedSpecialPrice);
        });
    }

    private boolean appliedFlag(Long campaignId) {
        return tx().execute(status -> (Boolean) entityManager
                .createNativeQuery("SELECT applied FROM promo_campaigns WHERE campaign_id = :id")
                .setParameter("id", campaignId).getSingleResult());
    }

    @SuppressWarnings("unchecked")
    private List<BigDecimal> originalDiscounts(Long campaignId) {
        return tx().execute(status -> entityManager
                .createNativeQuery("SELECT original_discount FROM promo_campaign_products "
                                 + "WHERE campaign_id = :id")
                .setParameter("id", campaignId).getResultList());
    }

    private long version(Long productId) {
        return tx().execute(status -> ((Number) entityManager
                .createNativeQuery("SELECT version FROM products WHERE product_id = :id")
                .setParameter("id", productId).getSingleResult()).longValue());
    }

    private void alignIdentitySequences() {
        String[][] pk = {
                {"categories", "category_id"}, {"products", "product_id"},
                {"promo_campaigns", "campaign_id"}, {"promo_campaign_products", "id"}
        };
        tx().executeWithoutResult(status -> {
            for (String[] tp : pk) {
                entityManager.createNativeQuery(
                        "SELECT setval(pg_get_serial_sequence('" + tp[0] + "', '" + tp[1] + "'), "
                        + "GREATEST((SELECT COALESCE(MAX(" + tp[1] + "), 0) FROM " + tp[0] + "), 1))")
                        .getResultList();
            }
        });
    }

    @AfterEach
    void cleanUp() {
        tx().executeWithoutResult(status -> {
            entityManager.createNativeQuery(
                    "DELETE FROM promo_campaign_products WHERE campaign_id IN "
                    + "(SELECT campaign_id FROM promo_campaigns WHERE name LIKE :t)")
                    .setParameter("t", tag + "%").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM promo_campaigns WHERE name LIKE :t")
                    .setParameter("t", tag + "%").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM products WHERE product_name LIKE :t")
                    .setParameter("t", tag + "%").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM categories WHERE category_name LIKE :t")
                    .setParameter("t", tag + "%").executeUpdate();
        });
    }
}
