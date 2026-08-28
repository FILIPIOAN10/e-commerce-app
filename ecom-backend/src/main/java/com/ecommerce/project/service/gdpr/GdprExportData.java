package com.ecommerce.project.service.gdpr;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The shape of an Art. 15 export, one nested record per domain.
 *
 * <p>Deliberately not the API DTOs: those are shaped for screens and change with
 * them, while this is a stable, human-readable rendering of "everything we hold
 * about you". Each section becomes one JSON file in the archive
 * (see {@link GdprExportArchiveWriter}).
 */
public final class GdprExportData {

    private GdprExportData() {
    }

    /** Section name → the file it is written to inside the ZIP. */
    public record Section(String fileName, Object content) {
    }

    public record Account(
            Long userId,
            String username,
            String email,
            String phone,
            String avatarUrl,
            String provider,
            boolean verified,
            boolean twoFactorEnabled,
            boolean marketingOptIn,
            List<String> roles) {
    }

    public record Address(
            Long addressId,
            String street,
            String buildingName,
            String city,
            String state,
            String country,
            String pincode) {
    }

    public record OrderLine(
            Long orderItemId,
            Long productId,
            String productName,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal discount) {
    }

    public record Payment(
            String paymentMethod,
            String gateway,
            String gatewayPaymentId,
            String gatewayStatus) {
    }

    public record Order(
            Long orderId,
            LocalDate orderDate,
            String status,
            BigDecimal totalAmount,
            BigDecimal discountAmount,
            BigDecimal shippingCost,
            String appliedCoupons,
            Address shippingAddress,
            Payment payment,
            List<OrderLine> items) {
    }

    public record Review(
            Long reviewId,
            Long productId,
            String productName,
            Integer rating,
            String comment,
            boolean verifiedPurchase,
            LocalDateTime createdAt) {
    }

    public record Question(
            Long questionId,
            Long productId,
            String productName,
            String question,
            String answer,
            LocalDateTime createdAt,
            LocalDateTime answeredAt) {
    }

    public record WishlistEntry(
            Long wishlistId,
            Long productId,
            String productName,
            LocalDateTime createdAt) {
    }

    public record CartLine(
            Long cartItemId,
            Long productId,
            String productName,
            Integer quantity,
            BigDecimal unitPrice,
            Boolean savedForLater) {
    }

    public record Reminder(
            String stage,
            Instant sentAt,
            Instant recoveredAt) {
    }

    public record Cart(
            Long cartId,
            BigDecimal totalPrice,
            Instant lastActivityAt,
            List<CartLine> items,
            List<Reminder> remindersSent) {
    }

    public record Notification(
            Long id,
            String title,
            String message,
            String type,
            boolean read,
            LocalDateTime createdAt) {
    }

    public record ActivityEntry(
            Long id,
            String action,
            String details,
            LocalDateTime createdAt) {
    }

    public record Subscription(
            Long id,
            String planName,
            String status,
            LocalDateTime currentPeriodStart,
            LocalDateTime currentPeriodEnd,
            LocalDateTime createdAt,
            LocalDateTime canceledAt) {
    }

    public record ReturnRequestEntry(
            Long id,
            Long orderId,
            String reason,
            String status,
            LocalDateTime requestedAt,
            LocalDateTime processedAt,
            Double refundAmount) {
    }

    /** A short note shipped alongside the data so the archive explains itself. */
    public record Manifest(
            Instant generatedAt,
            Long userId,
            String note,
            List<String> files) {
    }
}
