package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.AccountLockedException;
import com.ecommerce.project.exception.InvalidCredentialsException;
import com.ecommerce.project.exception.UserNotFoundException;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.AuthenticationResult;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.security.jwt.JwtUtils;
import com.ecommerce.project.security.redis.RefreshTokenService;
import com.ecommerce.project.security.redis.TwoFactorAttemptService;
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
    private final RefreshTokenService refreshTokenService;
    private final TwoFactorAttemptService twoFactorAttemptService;

    public TwoFactorServiceImpl(UserRepository userRepository, TotpService totpService, JwtUtils jwtUtils,
                                RefreshTokenService refreshTokenService,
                                TwoFactorAttemptService twoFactorAttemptService) {
        this.userRepository = userRepository;
        this.totpService = totpService;
        this.jwtUtils = jwtUtils;
        this.refreshTokenService = refreshTokenService;
        this.twoFactorAttemptService = twoFactorAttemptService;
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

    /**
     * Completes a 2FA login, counting wrong codes so the six-digit space cannot
     * simply be walked. See {@link TwoFactorAttemptService} for why the counter
     * is keyed on the username rather than on the challenge token.
     */
    @Override
    public AuthenticationResult complete2FALogin(String jwtToken, int code) {
        // A rejected token and a rejected code answer identically on purpose:
        // telling them apart tells an attacker whether a challenge is still live.
        if (!jwtUtils.validateTwoFactorToken(jwtToken)) {
            throw new InvalidCredentialsException("Invalid 2FA code");
        }
        String username = jwtUtils.getUserNameFromJWTToken(jwtToken);

        if (twoFactorAttemptService.isLocked(username)) {
            throw new AccountLockedException(lockedMessage(username));
        }

        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (!totpService.verifyCode(user.getTwoFactorSecret(), code)) {
            twoFactorAttemptService.recordFailedAttempt(username);
            throw new InvalidCredentialsException(rejectedCodeMessage(username));
        }

        // Only a completed login clears the counter, so a run of wrong codes
        // that stops short of the cap still expires rather than being forgiven.
        twoFactorAttemptService.resetAttempts(username);

        UserDetailsImpl userDetails = UserDetailsImpl.build(user);
        ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails);
        String refreshToken = refreshTokenService.createRefreshToken(user.getUserName());
        ResponseCookie refreshCookie = refreshTokenService.generateRefreshCookie(refreshToken);
        return new AuthenticationResult(UserInfoMapper.toUserInfoResponse(user), jwtCookie, refreshCookie, false, null);
    }

    /**
     * Keeps the "Invalid 2FA code" prefix whatever the remaining count, so the
     * message is one string to match on and the count is advice, not a contract.
     */
    private String rejectedCodeMessage(String username) {
        int remaining = twoFactorAttemptService.getRemainingAttempts(username);
        return remaining > 0
                ? "Invalid 2FA code. " + remaining + " attempts remaining."
                : "Invalid 2FA code. " + lockedMessage(username);
    }

    private String lockedMessage(String username) {
        long minutesLeft = Math.max(twoFactorAttemptService.getLockTimeRemaining(username) / 60, 1);
        return "Too many incorrect 2FA codes. Try again in " + minutesLeft + " minutes.";
    }
}
