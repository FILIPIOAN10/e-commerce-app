package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.InvalidCredentialsException;
import com.ecommerce.project.exception.UserNotFoundException;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.AuthenticationResult;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.security.jwt.JwtUtils;
import com.ecommerce.project.security.services.UserDetailsImpl;
import com.ecommerce.project.service.TotpService;
import com.ecommerce.project.service.TwoFactorService;
import com.ecommerce.project.util.UserInfoMapper;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TwoFactorServiceImpl implements TwoFactorService {

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final JwtUtils jwtUtils;

    public TwoFactorServiceImpl(UserRepository userRepository, TotpService totpService, JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.totpService = totpService;
        this.jwtUtils = jwtUtils;
    }

    @Override
    public GoogleAuthenticatorKey generate2FASecret(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        GoogleAuthenticatorKey key = totpService.generateSecret();
        user.setTwoFactorSecret(key.getKey());
        userRepository.save(user);
        return key;
    }

    @Override
    public boolean validate2FACode(Long userId, int code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return totpService.verifyCode(user.getTwoFactorSecret(), code);
    }

    @Override
    public void enable2FA(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setTwoFactorEnabled(true);
        userRepository.save(user);
    }

    @Override
    public void disable2FA(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        userRepository.save(user);
    }

    private boolean verify2FALogin(String jwtToken, int code) {
        if (!jwtUtils.validateTwoFactorToken(jwtToken)) {
            return false;
        }
        String username = jwtUtils.getUserNameFromJWTToken(jwtToken);
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return validate2FACode(user.getUserId(), code);
    }

    @Override
    public AuthenticationResult complete2FALogin(String jwtToken, int code) {
        if (!verify2FALogin(jwtToken, code)) {
            throw new InvalidCredentialsException("Invalid 2FA code");
        }
        String username = jwtUtils.getUserNameFromJWTToken(jwtToken);
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        UserDetailsImpl userDetails = UserDetailsImpl.build(user);
        ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails);
        return new AuthenticationResult(UserInfoMapper.toUserInfoResponse(user), jwtCookie, false, null);
    }
}
