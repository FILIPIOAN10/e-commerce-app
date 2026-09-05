package com.ecommerce.project.model;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long productId;

    @Version
    private Long version;

    @NotBlank
    @Size(min = 3, message = "Product name must contain at least 3 characters")
    private String productName;
    private String image;
    @NotBlank
    @Size(min = 6, message = "Product description must contain at least 3 characters")
    private String description;
    private String tags;
    private Integer quantity;
    private Integer lowStockThreshold = 10;
    // Money is BigDecimal at scale 2, never double — see Order for the reasoning
    // and V25 for the columns. discount is a percentage, held at the same scale
    // so a 12.5% promotion is expressible.
    @Column(precision = 12, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    /** What the customer is actually charged: price less discount. */
    @Column(precision = 12, scale = 2)
    private BigDecimal specialPrice = BigDecimal.ZERO;

    /**
     * Denormalised from the reviews table so rating can be filtered, bucketed and
     * sorted with an index instead of an aggregate per row. Recomputed — never
     * incremented — by {@code ProductRepository.refreshRatingAggregate}, so it
     * cannot drift away from the reviews it summarises.
     */
    @Column(name = "average_rating", nullable = false)
    private double averageRating = 0.0;

    @Column(name = "review_count", nullable = false)
    private int reviewCount = 0;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    /**
     * The seller. LAZY because nothing that renders a product reads it:
     * {@code ProductDTO} has no seller field, so ModelMapper never traverses
     * here, and the three ownership checks that do use it only call
     * {@code getUserId()}, which a proxy answers without a query.
     *
     * <p>It was EAGER, and {@code User.roles} is EAGER too, so a page of twenty
     * products issued a select per distinct seller and then a select per
     * seller's roles — around forty queries to render a list that needs one,
     * each of them hydrating a full {@code User} including its password hash,
     * only for the mapper to discard it.
     *
     * <p>{@code Order.withDetails} names this attribute explicitly, so the
     * order path still fetches it in the same query it always did.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private User user;

    @OneToMany(mappedBy = "product", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    private List<CartItem> products = new ArrayList<>();

    // Product lists (catalogue, wishlist, bundle) render image URLs but rarely
    // fetch-join this collection. @BatchSize turns the resulting one-query-per-
    // product into one query per 50 products.
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "product", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE}, fetch = FetchType.LAZY)
    private List<ProductImage> productImages = new ArrayList<>();
}
