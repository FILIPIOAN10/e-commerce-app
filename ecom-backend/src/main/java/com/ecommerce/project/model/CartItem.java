package com.ecommerce.project.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Data
@Table(name = "cart_items")
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cartItemId;

    @ManyToOne
    @JoinColumn(name = "cart_id")
    private Cart cart;


    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    private Integer quantity;

    /** Percent off, frozen when the item entered the cart. */
    @Column(precision = 12, scale = 2)
    private BigDecimal discount;

    /** Unit price the customer will be charged for this line. */
    @Column(precision = 12, scale = 2)
    private BigDecimal productPrice;
    private Boolean savedForLater = false;
}
