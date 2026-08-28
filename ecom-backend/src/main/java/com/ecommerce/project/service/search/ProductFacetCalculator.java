package com.ecommerce.project.service.search;

import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.FacetBucket;
import com.ecommerce.project.payload.ProductFacets;
import com.ecommerce.project.util.ProductSpecifications;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes the counts shown beside each filter option.
 *
 * <p>Four grouped queries, one per dimension, each built from the <em>same</em>
 * {@link ProductSpecifications#forFilter} used for the result page but with its
 * own dimension left out. That exclusion is what makes the numbers drill-down
 * counts — "click this and you get 12" rather than "your current selection
 * contains 12" — and sharing the predicate builder is what stops the counts and
 * the results drifting apart as filters are added.
 *
 * <p>Each dimension is one query, not one query per bucket: the buckets are a
 * {@code CASE} expression that the query groups by, so five price bands cost the
 * same as one. The whole search is six statements — page, total, and four
 * aggregates — all served by the indexes added in V22.
 */
@Component
public class ProductFacetCalculator {

    /** Highest rating bucket offered, i.e. "4 stars & up" down to "1 star & up". */
    private static final int MAX_RATING_BUCKET = 5;

    private final EntityManager entityManager;
    private final List<BigDecimal> priceThresholds;

    public ProductFacetCalculator(EntityManager entityManager,
                                  @Value("${app.search.facets.price-buckets:50,100,200,500}")
                                  String priceBuckets) {
        this.entityManager = entityManager;
        this.priceThresholds = parseThresholds(priceBuckets);
    }

    @Transactional(readOnly = true)
    public ProductFacets calculate(ProductFilter filter) {
        return new ProductFacets(
                categoryFacet(filter),
                priceFacet(filter),
                ratingFacet(filter),
                availabilityFacet(filter));
    }

    /**
     * One bucket per category that has at least one matching product. Categories
     * with no match at all are absent rather than listed as zero — unlike the
     * other dimensions, the set of categories is open-ended, and listing every
     * empty one would bury the useful options.
     */
    private List<FacetBucket> categoryFacet(ProductFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Product> root = query.from(Product.class);
        Join<Product, Category> category = root.join("category", JoinType.INNER);

        query.multiselect(category.get("categoryId"), category.get("categoryName"), cb.count(root));
        applyFilter(query, root, cb, filter, ProductFacetDimension.CATEGORY);
        query.groupBy(category.get("categoryId"), category.get("categoryName"));
        query.orderBy(cb.asc(category.get("categoryName")));

        return entityManager.createQuery(query).getResultList().stream()
                .map(row -> new FacetBucket(
                        String.valueOf(row.get(0, Long.class)),
                        row.get(1, String.class),
                        row.get(2, Long.class)))
                .toList();
    }

    /**
     * Fixed price bands, from configuration. Dynamic bands (quantiles of the
     * current result set) would fit every search better but move under the
     * customer as they filter — the band they just clicked stops existing. Fixed
     * thresholds are the stabler trade.
     */
    private List<FacetBucket> priceFacet(ProductFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Product> root = query.from(Product.class);

        Expression<Integer> band = bandExpression(cb, root.get("specialPrice"), priceThresholds);
        query.multiselect(band, cb.count(root));
        applyFilter(query, root, cb, filter, ProductFacetDimension.PRICE);
        query.groupBy(band);

        Map<Integer, Long> counts = countsByBand(query);
        List<FacetBucket> buckets = new ArrayList<>();
        for (int i = 0; i <= priceThresholds.size(); i++) {
            BigDecimal from = i == 0 ? null : priceThresholds.get(i - 1);
            BigDecimal to = i == priceThresholds.size() ? null : priceThresholds.get(i);
            buckets.add(new FacetBucket(
                    bandValue(from, to), bandLabel(from, to), counts.getOrDefault(i, 0L)));
        }
        return buckets;
    }

    /**
     * Cumulative "N stars &amp; up" counts. The query groups by whole-star band;
     * the running total is done here rather than in five overlapping queries.
     */
    private List<FacetBucket> ratingFacet(ProductFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Product> root = query.from(Product.class);

        List<Double> starThresholds = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
        Expression<Integer> band = bandExpression(cb, root.get("averageRating"), starThresholds);
        query.multiselect(band, cb.count(root));
        applyFilter(query, root, cb, filter, ProductFacetDimension.RATING);
        query.groupBy(band);

        Map<Integer, Long> counts = countsByBand(query);
        List<FacetBucket> buckets = new ArrayList<>();
        // Band i holds products whose average is in [starThresholds[i-1], starThresholds[i]);
        // "4 & up" is therefore every band from 4 upwards.
        for (int stars = MAX_RATING_BUCKET - 1; stars >= 1; stars--) {
            long cumulative = 0;
            for (int band_ = stars; band_ <= starThresholds.size(); band_++) {
                cumulative += counts.getOrDefault(band_, 0L);
            }
            buckets.add(new FacetBucket(String.valueOf(stars), stars + " stars & up", cumulative));
        }
        return buckets;
    }

    private List<FacetBucket> availabilityFacet(ProductFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Product> root = query.from(Product.class);

        Expression<Integer> available = cb.<Integer>selectCase()
                .when(cb.greaterThan(cb.coalesce(root.<Integer>get("quantity"), 0), 0), 1)
                .otherwise(0);
        query.multiselect(available, cb.count(root));
        applyFilter(query, root, cb, filter, ProductFacetDimension.AVAILABILITY);
        query.groupBy(available);

        Map<Integer, Long> counts = countsByBand(query);
        return List.of(
                new FacetBucket("true", "In stock", counts.getOrDefault(1, 0L)),
                new FacetBucket("false", "Out of stock", counts.getOrDefault(0, 0L)));
    }

    // ── shared plumbing ─────────────────────────────────────────────────────

    /** Applies every filter except the dimension whose counts are being computed. */
    private void applyFilter(CriteriaQuery<?> query, Root<Product> root, CriteriaBuilder cb,
                             ProductFilter filter, ProductFacetDimension excluded) {
        Predicate predicate = ProductSpecifications
                .forFilter(filter, EnumSet.of(excluded))
                .toPredicate(root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }
    }

    /**
     * {@code CASE WHEN value < t0 THEN 0 WHEN value < t1 THEN 1 ... ELSE n END} —
     * one expression the database can both select and group by, so a dimension's
     * buckets cost a single query.
     */
    private Expression<Integer> bandExpression(CriteriaBuilder cb,
                                               Expression<? extends Number> value,
                                               List<? extends Number> thresholds) {
        CriteriaBuilder.Case<Integer> bands = cb.selectCase();
        for (int i = 0; i < thresholds.size(); i++) {
            bands = bands.when(cb.lt(value, thresholds.get(i)), i);
        }
        return bands.otherwise(thresholds.size());
    }

    private Map<Integer, Long> countsByBand(CriteriaQuery<Tuple> query) {
        Map<Integer, Long> counts = new HashMap<>();
        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            counts.merge(((Number) row.get(0)).intValue(), row.get(1, Long.class), Long::sum);
        }
        return counts;
    }

    /** {@code "50-100"}, {@code "-50"}, {@code "500-"} — parsed back by the controller. */
    private String bandValue(Number from, Number to) {
        return (from == null ? "" : trim(from)) + "-" + (to == null ? "" : trim(to));
    }

    private String bandLabel(Number from, Number to) {
        if (from == null) {
            return "Under " + trim(to);
        }
        if (to == null) {
            return trim(from) + " and above";
        }
        return trim(from) + " – " + trim(to);
    }

    /**
     * Band edges read back as a customer would write them: {@code 50}, not
     * {@code 50.0}. Prices arrive as decimals and ratings as doubles, and the
     * label has to look the same either way.
     */
    private String trim(Number value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        double amount = value.doubleValue();
        return amount == Math.rint(amount) ? String.valueOf((long) amount) : String.valueOf(amount);
    }

    /** Band edges come from configuration as text, so they are read as exact decimals. */
    private List<BigDecimal> parseThresholds(String configured) {
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(BigDecimal::new)
                .sorted()
                .toList();
    }
}
