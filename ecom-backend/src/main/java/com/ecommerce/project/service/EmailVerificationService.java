package com.ecommerce.project.service;

public interface EmailVerificationService {

    void verifyEmail(String token);

    void resendVerificationEmail(String email);
}
