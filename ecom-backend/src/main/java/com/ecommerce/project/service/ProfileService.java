package com.ecommerce.project.service;

import com.ecommerce.project.security.request.ChangePasswordRequest;
import com.ecommerce.project.security.request.UpdateProfileRequest;
import com.ecommerce.project.security.response.UserInfoResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

public interface ProfileService {

    UserInfoResponse getCurrentUserDetails(Authentication authentication);

    UserInfoResponse updateProfile(UpdateProfileRequest request, Authentication authentication);

    void changePassword(ChangePasswordRequest request, Authentication authentication);

    String uploadAvatar(MultipartFile file, Authentication authentication);
}
