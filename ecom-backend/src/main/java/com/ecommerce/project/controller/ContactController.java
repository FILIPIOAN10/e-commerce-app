package com.ecommerce.project.controller;

import com.ecommerce.project.service.EmailService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import com.ecommerce.project.payload.request.ContactRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ContactController {

    private final EmailService emailService;

    public ContactController(EmailService emailService) {
        this.emailService = emailService;
    }

    @Tag(name = "Contact")
    @PostMapping("/public/contact")
    public ResponseEntity<?> submitContact(@Valid @RequestBody ContactRequest body) {
        // Presence and shape are enforced by the record's constraints, which
        // answer 400 with the offending field rather than one blanket message.
        try {
            emailService.sendContactMessage(body.name(), body.email(), body.message());
            return ResponseEntity.ok(Map.of("message", "Your message has been sent successfully!"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Failed to send message. Please try again later."));
        }
    }
}
