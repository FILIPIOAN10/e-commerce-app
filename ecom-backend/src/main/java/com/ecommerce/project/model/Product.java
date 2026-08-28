package com.ecommerce.project.model;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "products")
@ToString
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
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
    private double price; // 100
    private double discount; // 25
    private double specialPrice; // 75

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

    @ManyToOne
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
