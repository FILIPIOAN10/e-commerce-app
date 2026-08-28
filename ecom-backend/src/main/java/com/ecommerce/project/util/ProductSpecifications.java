package com.ecommerce.project.util;

import com.ecommerce.project.model.Product;
import com.ecommerce.project.service.search.ProductFacetDimension;
import com.ecommerce.project.service.search.ProductFilter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> withKeyword(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }
        String likeKeyword = "%" + keyword.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("productName")), likeKeyword),
                cb.like(cb.lower(root.get("description")), likeKeyword),
                cb.like(cb.lower(root.get("tags")), likeKeyword)
        );
    }

    public static Specification<Product> withCategory(String category) {
        if (category == null || category.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.like(root.get("category").get("categoryName"), category);
    }

    public static Specification<Product> withTerms(List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> {
            List<Predicate> termPredicates = new ArrayList<>();
            for (String term : terms) {
                String likeTerm = "%" + term.toLowerCase() + "%";
                termPredicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("productName")), likeTerm),
                                cb.like(cb.lower(root.get("description")), likeTerm),
                                cb.like(cb.lower(root.get("tags")), likeTerm),
                                cb.like(cb.lower(root.get("category").get("categoryName")), likeTerm)
                        ));
            }
            return cb.or(termPredicates.toArray(Predicate[]::new));
        };
    }

    // ── facet dimensions ────────────────────────────────────────────────────

    /**
     * Bounds on the price the customer is actually charged.
     *
     * <p>{@code specialPrice}, not {@code price}: banding by a number nobody pays
     * would put a discounted product in the wrong bracket and then fail to return
     * it when the customer clicks that bracket.
     */
    public static Specification<Product> priceBetween(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            List<Predicate> bounds = new ArrayList<>();
            if (min != null) {
                bounds.add(cb.ge(root.get("specialPrice"), min));
            }
            if (max != null) {
                bounds.add(cb.le(root.get("specialPrice"), max));
            }
            return bounds.isEmpty() ? cb.conjunction() : cb.and(bounds.toArray(Predicate[]::new));
        };
    }

    public static Specification<Product> categoryIn(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> root.get("category").get("categoryId").in(categoryIds);
    }

    /** Reads the denormalised average — see {@code Product.averageRating}. */
    public static Specification<Product> minRating(Double minRating) {
        if (minRating == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.ge(root.get("averageRating"), minRating);
    }

    /**
     * v1 of availability: on-hand quantity, ignoring checkout reservations. A
     * product with its last unit reserved by someone mid-checkout still counts as
     * in stock here — reservations live in Redis and cannot be joined against.
     * The alternative is a facet count that changes as strangers browse.
     */
    public static Specification<Product> inStock(Boolean inStock) {
        if (inStock == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> {
            var onHand = cb.coalesce(root.<Integer>get("quantity"), 0);
            return inStock ? cb.greaterThan(onHand, 0) : cb.lessThanOrEqualTo(onHand, 0);
        };
    }

    /**
     * The whole filter as one specification, optionally leaving out some
     * dimensions.
     *
     * <p>This is the single source of truth for "what does this search match",
     * used both for the result page and for every facet count. A facet query
     * excludes its own dimension and nothing else, which is what makes the counts
     * drill-down rather than global — and because both paths build their
     * predicates here, the counts cannot disagree with the results they describe.
     */
    public static Specification<Product> forFilter(ProductFilter filter,
                                                   Set<ProductFacetDimension> excluded) {
        Set<ProductFacetDimension> skip = excluded == null
                ? EnumSet.noneOf(ProductFacetDimension.class) : excluded;

        Specification<Product> spec = withKeyword(filter.keyword());
        if (!skip.contains(ProductFacetDimension.CATEGORY)) {
            spec = spec.and(categoryIn(filter.categoryIds()));
        }
        if (!skip.contains(ProductFacetDimension.PRICE)) {
            spec = spec.and(priceBetween(filter.minPrice(), filter.maxPrice()));
        }
        if (!skip.contains(ProductFacetDimension.RATING)) {
            spec = spec.and(minRating(filter.minRating()));
        }
        if (!skip.contains(ProductFacetDimension.AVAILABILITY)) {
            spec = spec.and(inStock(filter.inStock()));
        }
        return spec;
    }

    public static Specification<Product> forFilter(ProductFilter filter) {
        return forFilter(filter, EnumSet.noneOf(ProductFacetDimension.class));
    }
}
