package com.ecommerce.project.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

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
}
