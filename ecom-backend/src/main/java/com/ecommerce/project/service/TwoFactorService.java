package com.ecommerce.project.service;

import com.ecommerce.project.payload.AuthenticationResult;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;

public interface TwoFactorService {

    GoogleAuthenticatorKey generate2FASecret(Long userId);

    boolean validate2FACode(Long userId, int code);

    void enable2FA(Long userId);

    void disable2FA(Long userId);

    AuthenticationResult complete2FALogin(String jwtToken, int code);
}
