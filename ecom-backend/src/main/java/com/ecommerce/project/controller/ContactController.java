package com.ecommerce.project.controller;

import com.ecommerce.project.service.EmailService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ContactController {

    private final EmailService emailService;

    public ContactController(EmailService emailService) {
        this.emailService = emailService;
    }

    @Tag(name = "Contact")
    @PostMapping("/public/contact")
    public ResponseEntity<?> submitContact(@RequestBody Map<String, String> body) {
        try {
            String name = body.get("name");
            String email = body.get("email");
            String message = body.get("message");

            if (name == null || name.trim().isEmpty() ||
                email == null || email.trim().isEmpty() ||
                message == null || message.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "All fields are required"));
            }

            emailService.sendContactMessage(name, email, message);
            return ResponseEntity.ok(Map.of("message", "Your message has been sent successfully!"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Failed to send message. Please try again later."));
        }
    }
}
