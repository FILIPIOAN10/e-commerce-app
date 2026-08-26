package com.ecommerce.project.repository;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.model.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class OrderRepositoryNPlusOneTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private User customer;
    private User seller;
    private Product product;

    @BeforeEach
    void setUp() {
        customer = entityManager.find(User.class, 2L);
        seller = entityManager.find(User.class, 3L);

        Category category = persist(newCategory("Electronics"));
        product = persist(newProduct(category, seller));
        Address address = persist(newAddress(customer));

        Payment payment1 = persist(newPayment("pi_1"));
        Payment payment2 = persist(newPayment("pi_2"));

        Order order1 = persist(newOrder(customer, address, payment1, "user1@example.com"));
        Order order2 = persist(newOrder(customer, address, payment2, "user1@example.com"));

        persist(newOrderItem(order1, product, 1));
        persist(newOrderItem(order2, product, 2));

        Cart cart = persist(newCart(customer));
        persist(newCartItem(cart, product));
    }

    private Category newCategory(String name) {
        Category category = new Category();
        category.setCategoryName(name);
        return category;
    }

    private Product newProduct(Category category, User seller) {
        Product product = new Product();
        product.setProductName("Wireless Headphones");
        product.setDescription("Noise cancelling headphones");
        product.setQuantity(10);
        product.setPrice(100.0);
        product.setDiscount(0.0);
        product.setSpecialPrice(100.0);
        product.setCategory(category);
        product.setUser(seller);
        return product;
    }

    private Address newAddress(User user) {
        Address address = new Address("Street One", "Building A", "Bucuresti", "Bucuresti", "Romania", "010101");
        address.setUser(user);
        return address;
    }

    private Payment newPayment(String pgPaymentId) {
        return new Payment("STRIPE", pgPaymentId, "succeeded", "OK", "Stripe");
    }

    private Order newOrder(User user, Address address, Payment payment, String email) {
        Order order = new Order();
        order.setEmail(email);
        order.setOrderDate(LocalDate.now());
        order.setOrderStatus("Placed");
        order.setTotalAmount(200.0);
        order.setDiscountAmount(0.0);
        order.setShippingCost(5.0);
        order.setAppliedCoupons("");
        order.setAddress(address);
        order.setPayment(payment);
        return order;
    }

    private OrderItem newOrderItem(Order order, Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setDiscount(0.0);
        item.setOrderedProductPrice(product.getSpecialPrice());
        return item;
    }

    private Cart newCart(User user) {
        Cart cart = new Cart();
        cart.setUser(user);
        cart.setTotalPrice(200.0);
        return cart;
    }

    private CartItem newCartItem(Cart cart, Product product) {
        CartItem cartItem = new CartItem();
        cartItem.setCart(cart);
        cartItem.setProduct(product);
        cartItem.setQuantity(2);
        cartItem.setDiscount(0.0);
        cartItem.setProductPrice(100.0);
        return cartItem;
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        entityManager.flush();
        return entity;
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private void assertAssociationsLoaded(Order order) {
        assertThat(Hibernate.isInitialized(order.getOrderItems())).isTrue();
        assertThat(Hibernate.isInitialized(order.getAddress())).isTrue();
        assertThat(Hibernate.isInitialized(order.getPayment())).isTrue();

        for (OrderItem item : order.getOrderItems()) {
            assertThat(Hibernate.isInitialized(item.getProduct())).isTrue();
            assertThat(Hibernate.isInitialized(item.getProduct().getCategory())).isTrue();
            assertThat(Hibernate.isInitialized(item.getProduct().getUser())).isTrue();
        }
    }

    @Test
    @DisplayName("findAllWithDetails loads orders and nested associations with at most 2 queries")
    void findAllWithDetails_avoidsNPlusOne() {
        Statistics stats = statistics();
        stats.clear();

        Page<Order> page = orderRepository.findAllWithDetails(PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2);
        assertThat(stats.getQueryExecutionCount()).isLessThanOrEqualTo(2);
        page.getContent().forEach(this::assertAssociationsLoaded);
    }

    @Test
    @DisplayName("findByEmailWithDetails loads orders and nested associations with at most 2 queries")
    void findByEmailWithDetails_avoidsNPlusOne() {
        Statistics stats = statistics();
        stats.clear();

        Page<Order> page = orderRepository.findByEmailWithDetails("user1@example.com", PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2);
        assertThat(stats.getQueryExecutionCount()).isLessThanOrEqualTo(2);
        page.getContent().forEach(this::assertAssociationsLoaded);
    }

    @Test
    @DisplayName("findOrdersBySellerIdWithDetails loads filtered orders and nested associations with at most 2 queries")
    void findOrdersBySellerIdWithDetails_avoidsNPlusOne() {
        Statistics stats = statistics();
        stats.clear();

        Page<Order> page = orderRepository.findOrdersBySellerIdWithDetails(seller.getUserId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2);
        assertThat(stats.getQueryExecutionCount()).isLessThanOrEqualTo(2);
        page.getContent().forEach(this::assertAssociationsLoaded);
    }

    @Test
    @DisplayName("findByIdWithDetails loads a single order and all nested associations with at most 1 query")
    void findByIdWithDetails_avoidsNPlusOne() {
        Order first = orderRepository.findAllWithDetails(PageRequest.of(0, 1)).getContent().get(0);

        Statistics stats = statistics();
        stats.clear();

        Order order = orderRepository.findByIdWithDetails(first.getId()).orElseThrow();

        assertThat(stats.getQueryExecutionCount()).isLessThanOrEqualTo(1);
        assertAssociationsLoaded(order);
    }

    @Test
    @DisplayName("two-phase pagination loads only the requested page, not the whole table")
    void twoPhasePagination_loadsOnlyRequestedPage() {
        // Add more orders so a page smaller than the total is meaningful.
        Address address = persist(newAddress(customer));
        for (int i = 3; i <= 8; i++) {
            Payment payment = persist(newPayment("pi_page_" + i));
            Order order = persist(newOrder(customer, address, payment, "user1@example.com"));
            persist(newOrderItem(order, product, 1));
        }
        entityManager.clear();

        Statistics stats = statistics();
        stats.clear();

        Page<Long> idPage = orderRepository.findAllIds(PageRequest.of(0, 3, Sort.by("id")));

        // Phase 1 must return exactly the page size, never the whole table.
        assertThat(idPage.getContent()).hasSize(3);
        assertThat(idPage.getTotalElements()).isGreaterThanOrEqualTo(8);

        List<Order> orders = orderRepository.findByIdInWithDetails(idPage.getContent());

        assertThat(orders).hasSize(3);
        // One ID query + one count query + one graph fetch.
        assertThat(stats.getQueryExecutionCount()).isLessThanOrEqualTo(3);
        orders.forEach(this::assertAssociationsLoaded);
    }

    @Test
    @DisplayName("findIdsByEmail paginates in SQL and only returns matching orders")
    void findIdsByEmail_paginatesInSql() {
        Page<Long> idPage = orderRepository.findIdsByEmail("user1@example.com", PageRequest.of(0, 1, Sort.by("id")));

        assertThat(idPage.getContent()).hasSize(1);
        assertThat(idPage.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("findCartByEmail loads cart, items and products with at most 1 query")
    void findCartByEmail_avoidsNPlusOne() {
        Statistics stats = statistics();
        stats.clear();

        Cart cart = cartRepository.findCartByEmail("user1@example.com");

        assertThat(cart).isNotNull();
        assertThat(Hibernate.isInitialized(cart.getCartItems())).isTrue();
        assertThat(stats.getQueryExecutionCount()).isLessThanOrEqualTo(1);

        for (CartItem item : cart.getCartItems()) {
            assertThat(Hibernate.isInitialized(item.getProduct())).isTrue();
        }
    }
}
