package com.ecommerce.project.service.gdpr;

import com.ecommerce.project.model.AppNotification;
import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.CartItem;
import com.ecommerce.project.model.CartReminder;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.OrderItem;
import com.ecommerce.project.model.Payment;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.ProductQuestion;
import com.ecommerce.project.model.ReturnRequest;
import com.ecommerce.project.model.Review;
import com.ecommerce.project.model.User;
import com.ecommerce.project.model.UserActivityLog;
import com.ecommerce.project.model.UserSubscription;
import com.ecommerce.project.model.Wishlist;
import com.ecommerce.project.repository.AddressRepository;
import com.ecommerce.project.repository.CartItemRepository;
import com.ecommerce.project.repository.CartReminderRepository;
import com.ecommerce.project.repository.CartRepository;
import com.ecommerce.project.repository.NotificationRepository;
import com.ecommerce.project.repository.OrderRepository;
import com.ecommerce.project.repository.QuestionRepository;
import com.ecommerce.project.repository.ReturnRequestRepository;
import com.ecommerce.project.repository.ReviewRepository;
import com.ecommerce.project.repository.UserActivityLogRepository;
import com.ecommerce.project.repository.UserSubscriptionRepository;
import com.ecommerce.project.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects everything the store holds about one user into {@link GdprExportData}
 * sections (Art. 15).
 *
 * <p>One method per domain, each named after the table it reads, so that adding a
 * table to the schema and forgetting to add it here is visible as an absence
 * rather than hidden inside a loop. The section list returned by
 * {@link #assemble(User)} is the contract the archive writer and the tests both
 * work against.
 *
 * <p>Everything is keyed off the account as it stands <em>now</em>: orders,
 * notifications and returns are found by the current email, activity by the
 * current username. That is the same identity the rest of the app uses, and it
 * is why erasure has to export-then-anonymise, never the reverse.
 */
@Component
@RequiredArgsConstructor
public class GdprExportAssembler {

    private final AddressRepository addressRepository;
    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;
    private final QuestionRepository questionRepository;
    private final WishlistRepository wishlistRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CartReminderRepository cartReminderRepository;
    private final NotificationRepository notificationRepository;
    private final UserActivityLogRepository userActivityLogRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final ReturnRequestRepository returnRequestRepository;

    /** The archive's contents, in the order they are written. */
    @Transactional(readOnly = true)
    public List<GdprExportData.Section> assemble(User user) {
        List<GdprExportData.Section> sections = new ArrayList<>();
        sections.add(new GdprExportData.Section("account.json", account(user)));
        sections.add(new GdprExportData.Section("addresses.json", addresses(user)));
        sections.add(new GdprExportData.Section("orders.json", orders(user)));
        sections.add(new GdprExportData.Section("reviews.json", reviews(user)));
        sections.add(new GdprExportData.Section("questions.json", questions(user)));
        sections.add(new GdprExportData.Section("wishlist.json", wishlist(user)));
        sections.add(new GdprExportData.Section("carts.json", carts(user)));
        sections.add(new GdprExportData.Section("notifications.json", notifications(user)));
        sections.add(new GdprExportData.Section("activity-log.json", activityLog(user)));
        sections.add(new GdprExportData.Section("subscriptions.json", subscriptions(user)));
        sections.add(new GdprExportData.Section("returns.json", returnRequests(user)));
        return sections;
    }

    private GdprExportData.Account account(User user) {
        return new GdprExportData.Account(
                user.getUserId(),
                user.getUserName(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getProvider(),
                user.isVerified(),
                user.isTwoFactorEnabled(),
                user.isMarketingOptIn(),
                user.getRoles().stream().map(role -> role.getRoleName().name()).sorted().toList());
    }

    private List<GdprExportData.Address> addresses(User user) {
        return addressRepository.findByUserUserIdOrderByAddressIdAsc(user.getUserId()).stream()
                .map(this::toAddress)
                .toList();
    }

    private List<GdprExportData.Order> orders(User user) {
        return orderRepository.findAllByEmailIgnoreCaseWithDetails(user.getEmail()).stream()
                .map(this::toOrder)
                .toList();
    }

    private List<GdprExportData.Review> reviews(User user) {
        return reviewRepository.findByUserOrderByIdAsc(user).stream()
                .map(this::toReview)
                .toList();
    }

    private List<GdprExportData.Question> questions(User user) {
        return questionRepository.findByUserOrderByIdAsc(user).stream()
                .map(this::toQuestion)
                .toList();
    }

    private List<GdprExportData.WishlistEntry> wishlist(User user) {
        return wishlistRepository.findByUserOrderByIdAsc(user).stream()
                .map(this::toWishlistEntry)
                .toList();
    }

    private List<GdprExportData.Cart> carts(User user) {
        return cartRepository.findByUserUserIdOrderByCartIdAsc(user.getUserId()).stream()
                .map(this::toCart)
                .toList();
    }

    private List<GdprExportData.Notification> notifications(User user) {
        return notificationRepository.findByRecipientEmailOrderByIdAsc(user.getEmail()).stream()
                .map(this::toNotification)
                .toList();
    }

    private List<GdprExportData.ActivityEntry> activityLog(User user) {
        return userActivityLogRepository.findByUsernameOrderByCreatedAtDesc(user.getUserName()).stream()
                .map(this::toActivityEntry)
                .toList();
    }

    private List<GdprExportData.Subscription> subscriptions(User user) {
        return userSubscriptionRepository.findByEmailOrderByCreatedAtDesc(user.getEmail()).stream()
                .map(this::toSubscription)
                .toList();
    }

    private List<GdprExportData.ReturnRequestEntry> returnRequests(User user) {
        return returnRequestRepository.findByUserEmailOrderByIdAsc(user.getEmail()).stream()
                .map(this::toReturnRequestEntry)
                .toList();
    }

    // ── row → record ────────────────────────────────────────────────────────

    private GdprExportData.Address toAddress(com.ecommerce.project.model.Address address) {
        if (address == null) {
            return null;
        }
        return new GdprExportData.Address(
                address.getAddressId(),
                address.getStreet(),
                address.getBuildingName(),
                address.getCity(),
                address.getState(),
                address.getCountry(),
                address.getPincode());
    }

    private GdprExportData.Order toOrder(Order order) {
        return new GdprExportData.Order(
                order.getId(),
                order.getOrderDate(),
                order.getOrderStatus(),
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getShippingCost(),
                order.getAppliedCoupons(),
                toAddress(order.getAddress()),
                toPayment(order.getPayment()),
                order.getOrderItems().stream().map(this::toOrderLine).toList());
    }

    /**
     * The gateway's own response message is left out on purpose: it is the
     * processor's free-text field, not the customer's data, and it is the one
     * payment column erasure blanks.
     */
    private GdprExportData.Payment toPayment(Payment payment) {
        if (payment == null) {
            return null;
        }
        return new GdprExportData.Payment(
                payment.getPaymentMethod(),
                payment.getPgName(),
                payment.getPgPaymentId(),
                payment.getPgStatus());
    }

    private GdprExportData.OrderLine toOrderLine(OrderItem item) {
        return new GdprExportData.OrderLine(
                item.getOrderItemId(),
                productId(item.getProduct()),
                productName(item.getProduct()),
                item.getQuantity(),
                item.getOrderedProductPrice(),
                item.getDiscount());
    }

    private GdprExportData.Review toReview(Review review) {
        return new GdprExportData.Review(
                review.getId(),
                productId(review.getProduct()),
                productName(review.getProduct()),
                review.getRating(),
                review.getComment(),
                Boolean.TRUE.equals(review.getVerifiedPurchase()),
                review.getCreatedAt());
    }

    private GdprExportData.Question toQuestion(ProductQuestion question) {
        return new GdprExportData.Question(
                question.getId(),
                productId(question.getProduct()),
                productName(question.getProduct()),
                question.getQuestion(),
                question.getAnswer(),
                question.getCreatedAt(),
                question.getAnsweredAt());
    }

    private GdprExportData.WishlistEntry toWishlistEntry(Wishlist wishlist) {
        return new GdprExportData.WishlistEntry(
                wishlist.getId(),
                productId(wishlist.getProduct()),
                productName(wishlist.getProduct()),
                wishlist.getCreatedAt());
    }

    private GdprExportData.Cart toCart(Cart cart) {
        List<GdprExportData.CartLine> lines = cartItemRepository.findByCartCartId(cart.getCartId()).stream()
                .map(this::toCartLine)
                .toList();
        List<GdprExportData.Reminder> reminders =
                cartReminderRepository.findByCartCartIdOrderBySentAtAsc(cart.getCartId()).stream()
                        .map(this::toReminder)
                        .toList();
        return new GdprExportData.Cart(
                cart.getCartId(), cart.getTotalPrice(), cart.getLastActivityAt(), lines, reminders);
    }

    private GdprExportData.CartLine toCartLine(CartItem item) {
        return new GdprExportData.CartLine(
                item.getCartItemId(),
                productId(item.getProduct()),
                productName(item.getProduct()),
                item.getQuantity(),
                item.getProductPrice(),
                item.getSavedForLater());
    }

    private GdprExportData.Reminder toReminder(CartReminder reminder) {
        return new GdprExportData.Reminder(
                reminder.getStage() == null ? null : reminder.getStage().name(),
                reminder.getSentAt(),
                reminder.getRecoveredAt());
    }

    private GdprExportData.Notification toNotification(AppNotification notification) {
        return new GdprExportData.Notification(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getType(),
                notification.isRead(),
                notification.getCreatedAt());
    }

    private GdprExportData.ActivityEntry toActivityEntry(UserActivityLog log) {
        return new GdprExportData.ActivityEntry(
                log.getId(), log.getAction(), log.getDetails(), log.getCreatedAt());
    }

    private GdprExportData.Subscription toSubscription(UserSubscription subscription) {
        return new GdprExportData.Subscription(
                subscription.getId(),
                subscription.getPlan() == null ? null : subscription.getPlan().getName(),
                subscription.getStatus(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getCreatedAt(),
                subscription.getCanceledAt());
    }

    private GdprExportData.ReturnRequestEntry toReturnRequestEntry(ReturnRequest request) {
        return new GdprExportData.ReturnRequestEntry(
                request.getId(),
                request.getOrderId(),
                request.getReason(),
                request.getStatus(),
                request.getRequestedAt(),
                request.getProcessedAt(),
                request.getRefundAmount());
    }

    /** A product deleted since the row was written leaves the reference null. */
    private Long productId(Product product) {
        return product == null ? null : product.getProductId();
    }

    private String productName(Product product) {
        return product == null ? null : product.getProductName();
    }
}
