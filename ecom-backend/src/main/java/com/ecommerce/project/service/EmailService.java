package com.ecommerce.project.service;

import com.ecommerce.project.exception.EmailDeliveryException;

import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderItemDTO;
import com.ecommerce.project.service.email.EmailMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
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

    // ------------------------------------------------------------------
    // The one place a MimeMessage is built and sent. Every public method
    // below assembles an EmailMessage and hands it here. A send that fails
    // always leaves as an EmailDeliveryException naming the recipient, so a
    // caller that told the user "check your email" can react rather than lie.
    // ------------------------------------------------------------------
    private void send(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            boolean multipart = !message.attachments().isEmpty();
            MimeMessageHelper helper = new MimeMessageHelper(mime, multipart, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.body(), message.html());
            if (message.replyTo() != null && !message.replyTo().isBlank()) {
                helper.setReplyTo(message.replyTo());
            }
            for (EmailMessage.Attachment attachment : message.attachments()) {
                helper.addAttachment(attachment.filename(), new ByteArrayResource(attachment.content()));
            }
            mailSender.send(mime);
            log.info("Email '{}' handed off to SMTP for {}", message.subject(), message.to());
        } catch (Exception e) {
            log.error("Failed to send email '{}' to {}", message.subject(), message.to(), e);
            throw new EmailDeliveryException("email to " + message.to() + " (" + message.subject() + ")", e);
        }
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        // The caller answers "check your email". Swallowing a failure made that a
        // lie the user had no way to detect: they wait for a link that was never
        // sent. The token is already stored, so a retry works.
        send(EmailMessage.to(toEmail, "Password Reset Request")
                .html(emailTemplateService.render("reset-password", Map.of(
                        "expires", "15",
                        "link", frontendUrl + "/reset-password?token=" + token)))
                .build());
    }

    public void sendVerificationEmail(String toEmail, String token) {
        send(EmailMessage.to(toEmail, "Verify Your Email Address")
                .html(emailTemplateService.render("verify-email", Map.of(
                        "expires", "60",
                        "link", frontendUrl + "/verify-email?token=" + token)))
                .build());
    }

    public void sendOrderConfirmationEmail(String toEmail, OrderDTO order) {
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
                "trackLink", frontendUrl + "/orders/" + order.getOrderId()));

        // Rethrows (via send) so the outbox dispatcher backs off and retries
        // rather than losing the confirmation.
        send(EmailMessage.to(toEmail, "Order Confirmation - #" + order.getOrderId())
                .html(html)
                .attach("invoice-" + order.getOrderId() + ".pdf",
                        invoiceService.generateInvoicePdf(order.getOrderId()))
                .build());
    }

    public void sendOrderStatusUpdateEmail(String toEmail, OrderDTO order) {
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
                "trackLink", frontendUrl + "/orders/" + order.getOrderId()));

        send(EmailMessage.to(toEmail,
                        "Order Status Update - #" + order.getOrderId() + " - " + order.getOrderStatus())
                .html(html)
                .build());
    }

    public void sendCartRecoveryEmail(String toEmail, String name, int itemCount, BigDecimal cartTotal, String recoveryUrl) {
        String subject = "You left " + (itemCount == 1 ? "an item" : itemCount + " items") + " in your cart";
        send(EmailMessage.to(toEmail, subject)
                .html(emailTemplateService.render("cart-recovery", Map.of(
                        "name", name != null && !name.isBlank() ? name : "there",
                        "itemCount", String.valueOf(itemCount),
                        "cartTotal", String.format("%.2f", cartTotal),
                        "link", recoveryUrl)))
                .build());
    }

    /**
     * Tells the customer their Art. 15 archive is ready. Throws on failure so the
     * outbox retries: a built export nobody was told about is a request we
     * silently failed to answer.
     */
    public void sendGdprExportReadyEmail(String toEmail, String name, String downloadUrl, long expiryDays) {
        send(EmailMessage.to(toEmail, "Your data export is ready")
                .html(emailTemplateService.render("gdpr-export-ready", Map.of(
                        "name", name != null && !name.isBlank() ? name : "there",
                        "expiryDays", String.valueOf(expiryDays),
                        "link", downloadUrl)))
                .build());
    }

    /**
     * The second factor of account deletion. {@code requestErasure} answers "we
     * sent a link that will permanently delete your account", so a silent failure
     * would leave Art. 17 unexercisable with nothing to explain why — hence this
     * surfaces like the others.
     */
    public void sendGdprErasureConfirmationEmail(String toEmail, String name, String confirmUrl, long expiryMinutes) {
        send(EmailMessage.to(toEmail, "Confirm deleting your account")
                .html(emailTemplateService.render("gdpr-erase-confirm", Map.of(
                        "name", name != null && !name.isBlank() ? name : "there",
                        "expires", String.valueOf(expiryMinutes),
                        "link", confirmUrl)))
                .build());
    }

    public void sendUnlockRequestEmail(String username) {
        send(EmailMessage.to(fromEmail, "Account Unlock Request - " + username)
                .text("User \"" + username + "\" has requested their account be unlocked "
                        + "after too many failed login attempts. Please review and unlock the account "
                        + "from the Admin Panel if appropriate.")
                .build());
    }

    public void sendSubscriptionPaymentFailedEmail(String toEmail, String planName) {
        send(EmailMessage.to(toEmail, "Action needed: payment failed for " + planName)
                .html("<p>We couldn't take payment to renew your <strong>" + planName + "</strong> subscription.</p>"
                        + "<p>We'll try again automatically over the next few days. To avoid an interruption, "
                        + "update your card here: <a href=\"" + frontendUrl + "/my-subscriptions\">"
                        + frontendUrl + "/my-subscriptions</a>.</p>")
                .build());
    }

    public void sendSubscriptionEndedEmail(String toEmail, String planName) {
        send(EmailMessage.to(toEmail, "Your " + planName + " subscription has ended")
                .html("<p>Your <strong>" + planName + "</strong> subscription has ended and will no longer renew.</p>"
                        + "<p>You can start it again any time here: <a href=\"" + frontendUrl + "/subscriptions\">"
                        + frontendUrl + "/subscriptions</a>.</p>")
                .build());
    }

    public void sendContactMessage(String name, String email, String userMessage) {
        // ContactController answers "failed to send, please try again" on the
        // exception — reply-to the sender so a hit "Reply" reaches the customer.
        send(EmailMessage.to(fromEmail, "Contact Form Message from " + name)
                .text("Name: " + name + "\nEmail: " + email + "\n\nMessage:\n" + userMessage)
                .replyTo(email)
                .build());
    }
}
