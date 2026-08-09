package com.ecommerce.project.service;

import com.ecommerce.project.payload.AuthenticationResult;
import com.ecommerce.project.payload.UserResponse;
import com.ecommerce.project.security.request.ChangePasswordRequest;
import com.ecommerce.project.security.request.LoginRequest;
import com.ecommerce.project.security.request.SignupRequest;
import com.ecommerce.project.security.request.UpdateProfileRequest;
import com.ecommerce.project.security.response.MessageResponse;
import com.ecommerce.project.security.response.UserInfoResponse;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

public interface AuthService {
    AuthenticationResult login(LoginRequest loginRequest);

    ResponseEntity<MessageResponse> register(SignupRequest signupRequest);

    UserInfoResponse getCurrentUserDetails(Authentication authentication);

    ResponseCookie logoutUser();

    UserResponse getAllSellers(Pageable pageDetails);

    ResponseEntity<?> getPasswordHint(String username);

    GoogleAuthenticatorKey generate2FASecret(Long userId);

    boolean validate2FACode(Long userId, int code);

    void enable2FA(Long userId);

    void disable2FA(Long userId);

    boolean verify2FALogin(String jwtToken, int code);
    UserInfoResponse complete2FALogin(String jwtToken, int code);
    void initiatePasswordReset(String email);
    void resetPassword(String token, String newPassword);
    void verifyEmail(String token);
    void resendVerificationEmail(String email);
    UserInfoResponse updateProfile(UpdateProfileRequest request, Authentication authentication);
    void changePassword(ChangePasswordRequest request, Authentication authentication);
    String uploadAvatar(org.springframework.web.multipart.MultipartFile file, Authentication authentication);

    UserResponse getAllUsers(Pageable pageDetails);

    void deleteUser(Long userId);
}
