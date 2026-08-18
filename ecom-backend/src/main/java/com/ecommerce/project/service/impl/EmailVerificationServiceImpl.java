package com.ecommerce.project.service.impl;

import com.ecommerce.project.model.User;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.service.EmailService;
import com.ecommerce.project.service.EmailVerificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private final UserRepository userRepository;
    private final com.ecommerce.project.security.redis.EmailVerificationService tokenService;
    private final EmailService emailService;

    public EmailVerificationServiceImpl(UserRepository userRepository,
                                        com.ecommerce.project.security.redis.EmailVerificationService tokenService,
                                        EmailService emailService) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.emailService = emailService;
    }

    @Override
    public void verifyEmail(String token) {
        String email = tokenService.extractEmailFromToken(token);

        if (!tokenService.validateVerificationToken(token, email)) {
            throw new RuntimeException("Invalid or expired verification token");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setVerified(true);
        userRepository.save(user);

        tokenService.invalidateToken(email);
    }

    @Override
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        if (user.isVerified()) {
            throw new RuntimeException("Account is already verified");
        }

        String token = tokenService.generateVerificationToken(email);
        emailService.sendVerificationEmail(email, token);
    }
}
