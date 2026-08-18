package com.ecommerce.project.service.impl;

import com.ecommerce.project.model.User;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.service.EmailService;
import com.ecommerce.project.service.PasswordResetService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final com.ecommerce.project.security.redis.PasswordResetService tokenService;
    private final EmailService emailService;
    private final PasswordEncoder encoder;

    public PasswordResetServiceImpl(UserRepository userRepository,
                                    com.ecommerce.project.security.redis.PasswordResetService tokenService,
                                    EmailService emailService,
                                    PasswordEncoder encoder) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.encoder = encoder;
    }

    @Override
    public void initiatePasswordReset(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        String token = tokenService.generateResetToken(email);
        emailService.sendPasswordResetEmail(email, token);
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        String email = tokenService.extractEmailFromToken(token);

        if (!tokenService.validateResetToken(token, email)) {
            throw new RuntimeException("Invalid or expired reset token");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPassword(encoder.encode(newPassword));
        userRepository.save(user);

        tokenService.invalidateToken(email);
    }
}
