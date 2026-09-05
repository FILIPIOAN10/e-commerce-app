package com.ecommerce.project.service;

import com.ecommerce.project.exception.EmailDeliveryException;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderItemDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final InvoiceService invoiceService;
    private final EmailTemplateService emailTemplateService;

    @Value("${spring.mail.username:noreply.ecomapp@gmail.com}")
    private String fromEmail;

    @Value("${frontend.url}")
    private String frontendUrl;

    public void sendPasswordResetEmail(String toEmail, String token) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Password Reset Request");

            String html = emailTemplateService.render("reset-password", Map.of(
                    "expires", "15",
                    "link", frontendUrl + "/reset-password?token=" + token
            ));
            helper.setText(html, true);
            mailSender.send(mimeMessage);
            log.info("Password reset email handed off to SMTP for {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}", toEmail, e);
        }
    }
    public void sendVerificationEmail(String toEmail, String token) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Verify Your Email Address");

            String html = emailTemplateService.render("verify-email", Map.of(
                    "expires", "60",
                    "link", frontendUrl + "/verify-email?token=" + token
            ));
            helper.setText(html, true);
            mailSender.send(mimeMessage);
            log.info("Verification email handed off to SMTP for {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}", toEmail, e);
        }
    }

    public void sendOrderConfirmationEmail(String toEmail, OrderDTO order) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Order Confirmation - #" + order.getOrderId());

            StringBuilder itemsHtml = new StringBuilder();
            if (order.getItems() != null) {
                for (OrderItemDTO item : order.getItems()) {
                    String name = item.getProduct() != null ? item.getProduct().getProductName() : "Unknown Product";
                    itemsHtml.append("<tr>")
                            .append("<td>").append(name).append("</td>")
                            .append("<td>").append(item.getQuantity()).append("</td>")
                            .append("<td>$").append(String.format("%.2f", item.getOrderedProductPrice())).append("</td>")
                            .append("</tr>");
                }
            }

            String html = emailTemplateService.render("order-confirmation", Map.of(
                    "orderId", String.valueOf(order.getOrderId()),
                    "orderDate", String.valueOf(order.getOrderDate()),
                    "paymentMethod", order.getPayment() != null ? order.getPayment().getPaymentMethod() : "N/A",
                    "total", String.format("%.2f", order.getTotalAmount()),
                    "items", itemsHtml.toString(),
                    "trackLink", frontendUrl + "/orders/" + order.getOrderId()
            ));
            helper.setText(html, true);

            byte[] invoicePdf = invoiceService.generateInvoicePdf(order.getOrderId());
            helper.addAttachment("invoice-" + order.getOrderId() + ".pdf", new ByteArrayResource(invoicePdf));

            mailSender.send(mimeMessage);
            log.info("Order confirmation email handed off to SMTP for {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send order confirmation email with invoice to {}", toEmail, e);
            // Rethrow so the outbox dispatcher backs off and retries rather than
            // losing the confirmation.
            throw new EmailDeliveryException("order confirmation email to " + toEmail, e);
        }
    }

    public void sendOrderStatusUpdateEmail(String toEmail, OrderDTO order) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Order Status Update - #" + order.getOrderId() + " - " + order.getOrderStatus());

            String statusMessage = switch (order.getOrderStatus()) {
                case "Packed" -> "Your order has been packed and is being prepared for shipment.";
                case "Shipped" -> "Great news! Your order has been shipped and is on its way. Estimated delivery: 3-5 business days.";
                case "Delivered" -> "Your order has been delivered. Enjoy your purchase!";
                case "Cancelled" -> "Your order has been cancelled. If you did not request this, please contact support.";
                default -> "Your order status is now: " + order.getOrderStatus();
            };

            String html = emailTemplateService.render("order-status-update", Map.of(
                    "orderId", String.valueOf(order.getOrderId()),
                    "status", order.getOrderStatus(),
                    "message", statusMessage,
                    "trackLink", frontendUrl + "/orders/" + order.getOrderId()
            ));
            helper.setText(html, true);
            mailSender.send(mimeMessage);
            log.info("Order status email handed off to SMTP for {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send order status email to {}", toEmail, e);
            throw new EmailDeliveryException("order status email to " + toEmail, e);
        }
    }

    public void sendCartRecoveryEmail(String toEmail, String name, int itemCount, BigDecimal cartTotal, String recoveryUrl) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("You left " + (itemCount == 1 ? "an item" : itemCount + " items") + " in your cart");

            String html = emailTemplateService.render("cart-recovery", Map.of(
                    "name", name != null && !name.isBlank() ? name : "there",
                    "itemCount", String.valueOf(itemCount),
                    "cartTotal", String.format("%.2f", cartTotal),
                    "link", recoveryUrl
            ));
            helper.setText(html, true);
            mailSender.send(mimeMessage);
            log.info("Cart recovery email handed off to SMTP for {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send cart recovery email to {}", toEmail, e);
            throw new EmailDeliveryException("cart recovery email to " + toEmail, e);
        }
    }

    /**
     * Tells the customer their Art. 15 archive is ready. Throws on failure so the
     * outbox retries: a built export nobody was told about is a request we
     * silently failed to answer.
     */
    public void sendGdprExportReadyEmail(String toEmail, String name, String downloadUrl, long expiryDays) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Your data export is ready");

            String html = emailTemplateService.render("gdpr-export-ready", Map.of(
                    "name", name != null && !name.isBlank() ? name : "there",
                    "expiryDays", String.valueOf(expiryDays),
                    "link", downloadUrl
            ));
            helper.setText(html, true);
            mailSender.send(mimeMessage);
            log.info("GDPR export email handed off to SMTP for {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send GDPR export email to {}", toEmail, e);
            throw new EmailDeliveryException("GDPR export email to " + toEmail, e);
        }
    }

    /**
     * The second factor of account deletion. Swallows failures like the password
     * reset does — the caller is already told to check their email, and surfacing
     * SMTP trouble here would only reveal which addresses exist.
     */
    public void sendGdprErasureConfirmationEmail(String toEmail, String name, String confirmUrl, long expiryMinutes) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Confirm deleting your account");

            String html = emailTemplateService.render("gdpr-erase-confirm", Map.of(
                    "name", name != null && !name.isBlank() ? name : "there",
                    "expires", String.valueOf(expiryMinutes),
                    "link", confirmUrl
            ));
            helper.setText(html, true);
            mailSender.send(mimeMessage);
            log.info("GDPR erasure confirmation email handed off to SMTP for {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send GDPR erasure confirmation email to {}", toEmail, e);
        }
    }

    public void sendUnlockRequestEmail(String username) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(fromEmail);
            helper.setSubject("Account Unlock Request - " + username);
            helper.setText("User \"" + username + "\" has requested their account be unlocked "
                    + "after too many failed login attempts. Please review and unlock the account "
                    + "from the Admin Panel if appropriate.", false);
            mailSender.send(mimeMessage);
            log.info("Unlock request email handed off to SMTP for user {}", username);
        } catch (Exception e) {
            log.error("Failed to send unlock request email for user {}", username, e);
        }
    }

    public void sendContactMessage(String name, String email, String userMessage) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(fromEmail);
            helper.setSubject("Contact Form Message from " + name);
            helper.setText("Name: " + name + "\nEmail: " + email + "\n\nMessage:\n" + userMessage, false);
            mailSender.send(mimeMessage);
            log.info("Contact message handed off to SMTP from {}", email);
        } catch (Exception e) {
            log.error("Failed to send contact message", e);
        }
    }
}
