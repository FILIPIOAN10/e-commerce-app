package com.ecommerce.project.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@Table(name = "order_items")
@AllArgsConstructor
@NoArgsConstructor
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long orderItemId;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    // Back-reference: excluded from toString so Order <-> OrderItem does not
    // recurse into a StackOverflowError (e.g. when a test prints a list of orders).
    @ToString.Exclude
    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    private Integer quantity;

    /** Percent off at the time of ordering, e.g. 25.00 for 25%. */
    @Column(precision = 12, scale = 2)
    private BigDecimal discount;

    /** Unit price paid, frozen at checkout. See Order for why not double. */
    @Column(precision = 12, scale = 2)
    private BigDecimal orderedProductPrice;
}
