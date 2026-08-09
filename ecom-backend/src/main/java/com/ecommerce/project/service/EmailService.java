package com.ecommerce.project.service;

import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderItemDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final InvoiceService invoiceService;

    @Value("${spring.mail.username:noreply.ecomapp@gmail.com}")
    private String fromEmail;

    @Value("${app.password-reset.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public void sendPasswordResetEmail(String toEmail, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Password Reset Request");
        message.setText("You requested a password reset.\n\n"
                + "Click the link below to reset your password (valid for 15 minutes):\n"
                + frontendUrl + "/reset-password?token=" + token + "\n\n"
                + "If you did not request this, please ignore this email.");

        mailSender.send(message);
    }
    public void sendVerificationEmail(String toEmail, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Verify Your Email Address");
        message.setText("Welcome! Please verify your email address to activate your account.\n\n"
                + "Click the link below (valid for 60 minutes):\n"
                + frontendUrl + "/verify-email?token=" + token + "\n\n"
                + "If you did not create an account, please ignore this email.");

        mailSender.send(message);
    }

    public void sendOrderConfirmationEmail(String toEmail, OrderDTO order) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Order Confirmation - #" + order.getOrderId());

            StringBuilder text = new StringBuilder();
            text.append("Thank you for your order!\n\n");
            text.append("Order ID: #").append(order.getOrderId()).append("\n");
            text.append("Order Date: ").append(order.getOrderDate()).append("\n");
            text.append("Total Amount: $").append(String.format("%.2f", order.getTotalAmount())).append("\n");
            text.append("Payment Method: ").append(order.getPayment() != null ? order.getPayment().getPaymentMethod() : "N/A").append("\n\n");
            text.append("Items:\n");
            if (order.getItems() != null) {
                for (OrderItemDTO item : order.getItems()) {
                    text.append("  - ")
                            .append(item.getProduct() != null ? item.getProduct().getProductName() : "Unknown Product")
                            .append(" x").append(item.getQuantity())
                            .append(" - $").append(String.format("%.2f", item.getOrderedProductPrice()))
                            .append("\n");
                }
            }
            text.append("\nWe'll notify you when your order status changes.\n");
            text.append("Track your order at: ").append(frontendUrl).append("/orders/").append(order.getOrderId()).append("\n");
            text.append("\nYour invoice is attached to this email.\n");

            helper.setText(text.toString());

            byte[] invoicePdf = invoiceService.generateInvoicePdf(order.getOrderId());
            helper.addAttachment("invoice-" + order.getOrderId() + ".pdf", new ByteArrayResource(invoicePdf));

            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            log.error("Failed to send order confirmation email with invoice to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendOrderStatusUpdateEmail(String toEmail, OrderDTO order) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Order Status Update - #" + order.getOrderId() + " - " + order.getOrderStatus());

        StringBuilder text = new StringBuilder();
        text.append("Your order status has been updated.\n\n");
        text.append("Order ID: #").append(order.getOrderId()).append("\n");
        text.append("New Status: ").append(order.getOrderStatus()).append("\n\n");

        switch (order.getOrderStatus()) {
            case "Packed" -> text.append("Your order has been packed and is being prepared for shipment.\n");
            case "Shipped" -> {
                text.append("Great news! Your order has been shipped and is on its way.\n");
                text.append("Estimated delivery: 3-5 business days.\n");
            }
            case "Delivered" -> text.append("Your order has been delivered. Enjoy your purchase!\n");
            case "Cancelled" -> text.append("Your order has been cancelled. If you did not request this, please contact support.\n");
            default -> text.append("Your order status is now: ").append(order.getOrderStatus()).append("\n");
        }

        text.append("\nTrack your order at: ").append(frontendUrl).append("/orders/").append(order.getOrderId()).append("\n");

        message.setText(text.toString());
        mailSender.send(message);
    }

    public void sendContactMessage(String name, String email, String userMessage) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(fromEmail);
        message.setSubject("Contact Form Message from " + name);
        message.setText("Name: " + name + "\nEmail: " + email + "\n\nMessage:\n" + userMessage);
        mailSender.send(message);
    }
}
