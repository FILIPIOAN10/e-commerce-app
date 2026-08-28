package com.ecommerce.project.model;


// represent order placed by a customer

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@NamedEntityGraph(
        name = "Order.withDetails",
        attributeNodes = {
                @NamedAttributeNode(value = "orderItems", subgraph = "orderItemGraph"),
                @NamedAttributeNode("payment"),
                @NamedAttributeNode("address")
        },
        subgraphs = {
                @NamedSubgraph(
                        name = "orderItemGraph",
                        attributeNodes = @NamedAttributeNode(value = "product", subgraph = "productGraph")
                ),
                @NamedSubgraph(
                        name = "productGraph",
                        attributeNodes = {
                                @NamedAttributeNode("category"),
                                @NamedAttributeNode("user")
                        }
                )
        }
)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Email
    @Column(nullable = false)
    private String email;


    @OneToMany(mappedBy = "order", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<OrderItem> orderItems = new ArrayList<>();

    private LocalDate orderDate;

    @OneToOne
    @JoinColumn(name = "payment_id")
    private Payment payment;

    // Money is BigDecimal at scale 2, never double: an order total is a count of
    // cents, and double cannot hold one exactly (84.99 * 100 != 8499). The
    // column is NUMERIC(12,2) so the database cannot widen it back — see V24.
    @Column(precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal shippingCost = BigDecimal.ZERO;
    private String appliedCoupons;
    private String orderStatus;

    @ManyToOne
    @JoinColumn(name = "address_id")
    private Address address;
}
