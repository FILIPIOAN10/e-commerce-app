package com.ecommerce.project.service.search;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.FacetBucket;
import com.ecommerce.project.payload.FacetedProductResponse;
import com.ecommerce.project.payload.ProductDTO;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §3.2: faceted search.
 *
 * <p>The property that matters is agreement: a facet count is a promise about
 * what happens when the customer clicks it. Several tests here apply a facet and
 * assert the result size equals the number that was displayed — if the counting
 * query and the filtering query ever drift apart, that is where it shows.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class FacetedProductSearchServiceTest {

    @Autowired private FacetedProductSearchService searchService;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager txManager;

    private final String tag = "fs" + Long.toUnsignedString(System.nanoTime(), 36);
    private TransactionTemplate tx;

    /** Category name → id, for building filters. */
    private final Map<String, Long> categoryIds = new HashMap<>();

    @BeforeEach
    void seed() {
        tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(status -> {
            Category gadgets = category("gadgets");
            Category books = category("books");

            //       name        category   price  rating  stock
            product("cheap-pen",  books,      "9.99",   4.5,   40);
            product("notebook",   books,     "29.99",   3.0,   12);
            product("headphones", gadgets,   "79.99",   4.8,    5);
            product("keyboard",   gadgets,  "149.00",   2.5,    0);
            product("monitor",    gadgets,  "349.00",   4.2,    3);
            product("workstation",gadgets,  "1499.0",   5.0,    1);
        });
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM products WHERE product_name LIKE :t")
                    .setParameter("t", tag + "%").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM categories WHERE category_name LIKE :t")
                    .setParameter("t", tag + "%").executeUpdate();
        });
    }

    @Test
    @DisplayName("an unfiltered search reports every dimension across the whole catalogue")
    void facetsCoverEveryDimension() {
        FacetedProductResponse response = search(ProductFilter.empty());

        assertThat(response.facets().categories())
                .extracting(FacetBucket::label)
                .contains(tag + "-gadgets", tag + "-books");
        assertThat(response.facets().priceRanges())
                .as("five bands from four thresholds").hasSize(5);
        assertThat(response.facets().ratings())
                .extracting(FacetBucket::value).containsExactly("4", "3", "2", "1");
        assertThat(response.facets().availability())
                .extracting(FacetBucket::value).containsExactly("true", "false");
    }

    @Test
    @DisplayName("clicking a category returns exactly as many products as its count promised")
    void categoryCountMatchesWhatClickingItReturns() {
        FacetedProductResponse unfiltered = search(ProductFilter.empty());
        FacetBucket gadgets = bucket(unfiltered.facets().categories(), tag + "-gadgets");

        FacetedProductResponse filtered = search(new ProductFilter(
                null, List.of(Long.valueOf(gadgets.value())), null, null, null, null));

        assertThat(filtered.totalElements()).isEqualTo(gadgets.count());
        assertThat(names(filtered))
                .containsExactlyInAnyOrder(name("headphones"), name("keyboard"),
                        name("monitor"), name("workstation"));
    }

    @Test
    @DisplayName("clicking a price band returns exactly as many products as its count promised")
    void priceBandCountMatchesWhatClickingItReturns() {
        FacetedProductResponse unfiltered = search(ProductFilter.empty());
        FacetBucket band = bucket(unfiltered.facets().priceRanges(), "50 – 100");

        FacetedProductResponse filtered = search(new ProductFilter(
                null, null, new BigDecimal("50"), new BigDecimal("100"), null, null));

        assertThat(filtered.totalElements()).isEqualTo(band.count());
        assertThat(names(filtered)).containsExactly(name("headphones"));
    }

    @Test
    @DisplayName("clicking a rating band returns exactly as many products as its count promised")
    void ratingCountMatchesWhatClickingItReturns() {
        FacetedProductResponse unfiltered = search(ProductFilter.empty());
        FacetBucket fourPlus = bucket(unfiltered.facets().ratings(), "4 stars & up");

        FacetedProductResponse filtered = search(new ProductFilter(
                null, null, null, null, 4.0, null));

        assertThat(filtered.totalElements()).isEqualTo(fourPlus.count());
        assertThat(names(filtered))
                .containsExactlyInAnyOrder(name("cheap-pen"), name("headphones"),
                        name("monitor"), name("workstation"));
    }

    @Test
    @DisplayName("in-stock filtering excludes the sold-out product")
    void inStockExcludesSoldOut() {
        FacetedProductResponse inStock = search(new ProductFilter(null, null, null, null, null, true));

        assertThat(names(inStock)).doesNotContain(name("keyboard"));
        assertThat(inStock.totalElements()).isEqualTo(5);

        FacetedProductResponse outOfStock = search(new ProductFilter(null, null, null, null, null, false));
        assertThat(names(outOfStock)).containsExactly(name("keyboard"));
    }

    @Test
    @DisplayName("counts are drill-down: each dimension is counted with the other filters applied")
    void countsReflectTheOtherActiveFilters() {
        // Gadgets only. The price and rating facets must now describe gadgets,
        // while the category facet still shows every category — otherwise the
        // customer could never widen their selection.
        FacetedProductResponse gadgetsOnly = search(new ProductFilter(
                null, List.of(categoryIds.get("gadgets")), null, null, null, null));

        assertThat(bucket(gadgetsOnly.facets().priceRanges(), "Under 50").count())
                .as("the sub-50 book is not counted while gadgets are selected").isZero();
        assertThat(bucket(gadgetsOnly.facets().ratings(), "4 stars & up").count())
                .as("only the gadgets rated 4+").isEqualTo(3);
        assertThat(bucket(gadgetsOnly.facets().availability(), "Out of stock").count())
                .isEqualTo(1);

        assertThat(gadgetsOnly.facets().categories())
                .as("the selected dimension still lists its alternatives")
                .extracting(FacetBucket::label)
                .contains(tag + "-books");
    }

    @Test
    @DisplayName("combined filters intersect, and the facets keep agreeing with the results")
    void combinedFiltersIntersect() {
        ProductFilter filter = new ProductFilter(
                null, List.of(categoryIds.get("gadgets")), new BigDecimal("50"),
                new BigDecimal("500"), 4.0, true);

        FacetedProductResponse response = search(filter);

        assertThat(names(response)).containsExactlyInAnyOrder(name("headphones"), name("monitor"));
        assertThat(response.totalElements()).isEqualTo(2);

        // Widening only the rating, as the rating facet's own count promises.
        FacetBucket twoPlus = bucket(response.facets().ratings(), "2 stars & up");
        FacetedProductResponse widened = search(new ProductFilter(
                filter.keyword(), filter.categoryIds(), filter.minPrice(), filter.maxPrice(),
                2.0, filter.inStock()));
        assertThat(widened.totalElements()).isEqualTo(twoPlus.count());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private FacetedProductResponse search(ProductFilter filter) {
        // Keyword-scoped to this test's rows: the catalogue is shared with the
        // seeded demo products, and a facet count over those would be noise.
        ProductFilter scoped = new ProductFilter(
                tag, filter.categoryIds(), filter.minPrice(), filter.maxPrice(),
                filter.minRating(), filter.inStock());
        return searchService.search(scoped, 0, 50, "productId", "asc");
    }

    private List<String> names(FacetedProductResponse response) {
        return response.content().stream().map(ProductDTO::getProductName).toList();
    }

    private FacetBucket bucket(List<FacetBucket> buckets, String label) {
        return buckets.stream()
                .filter(b -> b.label().equals(label))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no bucket labelled '" + label + "' in " + buckets));
    }

    private String name(String suffix) {
        return tag + "-" + suffix;
    }

    private Category category(String name) {
        Category category = new Category();
        category.setCategoryName(tag + "-" + name);
        entityManager.persist(category);
        categoryIds.put(name, category.getCategoryId());
        return category;
    }

    private void product(String name, Category category, String price, double rating, int quantity) {
        Product product = new Product();
        product.setProductName(tag + "-" + name);
        // The keyword filter matches name/description/tags; the tag in the name
        // is what scopes every search in this class to these six rows.
        product.setDescription("faceted search fixture product");
        product.setTags(name);
        product.setPrice(new BigDecimal(price));
        product.setSpecialPrice(new BigDecimal(price));
        product.setDiscount(new BigDecimal("0.0"));
        product.setQuantity(quantity);
        product.setCategory(category);
        // Set directly rather than through reviews: this class is about the
        // facet arithmetic, and ReviewServiceImpl's upkeep of the column has its
        // own test.
        product.setAverageRating(rating);
        product.setReviewCount(rating > 0 ? 3 : 0);
        entityManager.persist(product);
    }
}
