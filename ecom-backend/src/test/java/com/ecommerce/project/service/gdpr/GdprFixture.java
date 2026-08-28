package com.ecommerce.project.service.gdpr;

import com.ecommerce.project.model.Address;
import com.ecommerce.project.model.AppNotification;
import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.CartItem;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.OrderItem;
import com.ecommerce.project.model.Payment;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.ProductQuestion;
import com.ecommerce.project.model.Review;
import com.ecommerce.project.model.User;
import com.ecommerce.project.model.UserActivityLog;
import com.ecommerce.project.model.Wishlist;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds one fully-populated customer — an account with something in every table
 * the GDPR flows touch — so an export can be checked for completeness and an
 * erasure for thoroughness against the same shape.
 *
 * <p>Rows are committed (not rolled back), because both flows under test span
 * their own transactions; {@link #cleanUp()} removes them afterwards by tag.
 */
class GdprFixture {

    /** Everything created for one customer, by id, for assertions afterwards. */
    record Customer(Long userId, String username, String email, String rawPassword,
                    Long orderId, Long addressId, Long paymentId, Long cartId, Long productId) {
    }

    private final EntityManager entityManager;
    private final TransactionTemplate tx;
    private final String tag;
    private final AtomicInteger seq = new AtomicInteger();

    GdprFixture(EntityManager entityManager, PlatformTransactionManager txManager, String tag) {
        this.entityManager = entityManager;
        this.tx = new TransactionTemplate(txManager);
        this.tag = tag;
    }

    /**
     * ddl-auto=create-drop plus the seeded rows in data.sql leaves the identity
     * sequences behind the seeded ids; persisting a fresh row then collides on
     * the primary key.
     */
    void alignIdentitySequences() {
        String[][] pk = {
                {"users", "user_id"}, {"carts", "cart_id"}, {"categories", "category_id"},
                {"products", "product_id"}, {"cart_items", "cart_item_id"}, {"addresses", "address_id"},
                {"orders", "id"}, {"order_items", "order_item_id"}, {"payments", "payment_id"},
                {"reviews", "review_id"}, {"product_questions", "question_id"},
                {"wishlists", "wishlist_id"}, {"notifications", "id"}, {"user_activity_logs", "log_id"}
        };
        tx.executeWithoutResult(status -> {
            for (String[] tp : pk) {
                entityManager.createNativeQuery(
                                "SELECT setval(pg_get_serial_sequence('" + tp[0] + "', '" + tp[1] + "'), "
                                + "GREATEST((SELECT COALESCE(MAX(" + tp[1] + "), 0) FROM " + tp[0] + "), 1))")
                        .getResultList();
            }
        });
    }

    /**
     * A customer with an address, a delivered order (line + payment), a review, a
     * question, a wishlist entry, a live cart, a notification and an activity
     * entry.
     *
     * @param passwordHash bcrypt hash to store; pair it with the raw value so a
     *                     test can try to sign in afterwards
     */
    Customer createFullyPopulatedCustomer(String passwordHash, String rawPassword) {
        return tx.execute(status -> {
            int n = seq.incrementAndGet();
            String username = tag + n;
            String email = tag + n + "@e.co";

            User user = new User(username, email, passwordHash);
            user.setVerified(true);
            user.setPhone("0700000000");
            user.setMarketingOptIn(true);
            entityManager.persist(user);

            Category category = new Category();
            category.setCategoryName(tag + "-cat" + n);
            entityManager.persist(category);

            Product product = new Product();
            product.setProductName(tag + "-widget" + n);
            product.setDescription("a widget for the gdpr fixture");
            product.setQuantity(10);
            product.setPrice(new BigDecimal("25.0"));
            product.setSpecialPrice(new BigDecimal("25.0"));
            product.setDiscount(new BigDecimal("0.0"));
            product.setCategory(category);
            entityManager.persist(product);

            Address address = new Address("12 Privacy Lane", "Block GDPR", "Bucuresti",
                    "Bucuresti", "Romania", "010101");
            address.setUser(user);
            entityManager.persist(address);

            Payment payment = new Payment("CARD", "pi_" + tag + n, "succeeded",
                    "Approved by issuer", "STRIPE");
            entityManager.persist(payment);

            Order order = new Order();
            order.setEmail(email);
            order.setOrderDate(LocalDate.now());
            order.setOrderStatus("Delivered");
            order.setAddress(address);
            order.setPayment(payment);
            order.setTotalAmount(new BigDecimal("25.00"));
            order.setDiscountAmount(BigDecimal.ZERO);
            order.setShippingCost(BigDecimal.ZERO);
            entityManager.persist(order);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(1);
            orderItem.setDiscount(BigDecimal.ZERO);
            orderItem.setOrderedProductPrice(new BigDecimal("25.00"));
            entityManager.persist(orderItem);

            Review review = Review.builder()
                    .user(user).product(product).rating(5)
                    .comment("Written by " + username)
                    .verifiedPurchase(true).helpfulCount(0).unhelpfulCount(0)
                    .build();
            entityManager.persist(review);

            ProductQuestion question = ProductQuestion.builder()
                    .user(user).product(product)
                    .question("Asked by " + username)
                    .build();
            entityManager.persist(question);

            Wishlist wishlist = Wishlist.builder().user(user).product(product).build();
            entityManager.persist(wishlist);

            Cart cart = new Cart();
            cart.setUser(user);
            cart.setTotalPrice(new BigDecimal("50.0"));
            entityManager.persist(cart);

            CartItem cartItem = new CartItem();
            cartItem.setCart(cart);
            cartItem.setProduct(product);
            cartItem.setQuantity(2);
            cartItem.setDiscount(new BigDecimal("0.0"));
            cartItem.setProductPrice(new BigDecimal("25.0"));
            cartItem.setSavedForLater(false);
            entityManager.persist(cartItem);

            AppNotification notification = new AppNotification();
            notification.setRecipientEmail(email);
            notification.setTitle("Order delivered");
            notification.setMessage("Your order was delivered, " + username);
            notification.setType("ORDER");
            notification.setReferenceId(order.getId());
            notification.setRead(false);
            notification.setCreatedAt(LocalDateTime.now());
            entityManager.persist(notification);

            UserActivityLog activity = UserActivityLog.builder()
                    .username(username).action("LOGIN")
                    .details("signed in from the gdpr fixture")
                    .build();
            entityManager.persist(activity);

            return new Customer(user.getUserId(), username, email, rawPassword,
                    order.getId(), address.getAddressId(), payment.getPaymentId(),
                    cart.getCartId(), product.getProductId());
        });
    }

    /** Removes every row this fixture created, innermost foreign key first. */
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            String like = tag + "%";
            native_("DELETE FROM gdpr_export WHERE user_id IN (SELECT user_id FROM users WHERE username LIKE :t)", like);
            native_("DELETE FROM outbox_event WHERE event_type = 'GDPR_EXPORT_REQUESTED'", null);
            native_("DELETE FROM admin_audit_logs WHERE action = 'GDPR_ERASURE'", null);
            native_("DELETE FROM user_activity_logs WHERE username LIKE :t", like);
            native_("DELETE FROM notifications WHERE recipient_email LIKE :t OR recipient_email LIKE 'deleted-%'", like);
            native_("DELETE FROM cart_reminder WHERE cart_id IN (SELECT c.cart_id FROM carts c "
                    + "JOIN users u ON c.user_id = u.user_id WHERE u.username LIKE :t)", like);
            native_("DELETE FROM cart_items WHERE cart_id IN (SELECT c.cart_id FROM carts c "
                    + "JOIN users u ON c.user_id = u.user_id WHERE u.username LIKE :t)", like);
            native_("DELETE FROM carts WHERE user_id IN (SELECT user_id FROM users WHERE username LIKE :t)", like);
            native_("DELETE FROM wishlists WHERE product_id IN (SELECT product_id FROM products WHERE product_name LIKE :t)", like);
            native_("DELETE FROM reviews WHERE product_id IN (SELECT product_id FROM products WHERE product_name LIKE :t)", like);
            native_("DELETE FROM product_questions WHERE product_id IN (SELECT product_id FROM products WHERE product_name LIKE :t)", like);
            native_("DELETE FROM order_items WHERE product_id IN (SELECT product_id FROM products WHERE product_name LIKE :t)", like);
            native_("DELETE FROM return_requests WHERE user_email LIKE :t OR user_email LIKE 'deleted-%'", like);
            // Orders survive erasure with an anonymised email, so match on both.
            native_("DELETE FROM invoices WHERE order_id IN (SELECT id FROM orders WHERE email LIKE :t OR email LIKE 'deleted-%')", like);
            native_("DELETE FROM orders WHERE email LIKE :t OR email LIKE 'deleted-%'", like);
            // After the orders, which hold the foreign key. Matched on the
            // fixture's own gateway id, since the orders are already gone.
            native_("DELETE FROM payments WHERE pg_payment_id LIKE 'pi_' || :t", like);
            native_("DELETE FROM addresses WHERE user_id IN (SELECT user_id FROM users WHERE username LIKE :t) "
                    + "OR street = 'REDACTED'", like);
            native_("DELETE FROM stock_movement WHERE product_id IN (SELECT product_id FROM products WHERE product_name LIKE :t)", like);
            native_("DELETE FROM products WHERE product_name LIKE :t", like);
            native_("DELETE FROM categories WHERE category_name LIKE :t", like);
            native_("DELETE FROM users WHERE username LIKE :t OR username LIKE 'deleted-%'", like);
        });
    }

    private void native_(String sql, String tagLike) {
        var query = entityManager.createNativeQuery(sql);
        if (tagLike != null) {
            query.setParameter("t", tagLike);
        }
        query.executeUpdate();
    }
}
