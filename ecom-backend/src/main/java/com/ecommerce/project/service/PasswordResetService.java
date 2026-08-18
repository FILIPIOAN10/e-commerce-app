package com.ecommerce.project.service;

public interface PasswordResetService {

    void initiatePasswordReset(String email);

    void resetPassword(String token, String newPassword);
}
