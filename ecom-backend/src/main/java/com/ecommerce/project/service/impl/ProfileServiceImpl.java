package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.EmailAlreadyExistsException;
import com.ecommerce.project.exception.InvalidCredentialsException;
import com.ecommerce.project.model.User;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.security.request.ChangePasswordRequest;
import com.ecommerce.project.security.request.UpdateProfileRequest;
import com.ecommerce.project.security.response.UserInfoResponse;
import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.service.ProfileService;
import com.ecommerce.project.service.media.ImageUploadValidator;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.UserInfoMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class ProfileServiceImpl implements ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final AuthUtil authUtil;
    private final ImageUploadValidator imageValidator;

    @Value("${image.base.url}")
    private String imageBaseUrl;

    public ProfileServiceImpl(UserRepository userRepository, PasswordEncoder encoder,
                              AuthUtil authUtil, ImageUploadValidator imageValidator) {
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.authUtil = authUtil;
        this.imageValidator = imageValidator;
    }

    @Override
    public UserInfoResponse getCurrentUserDetails(Authentication authentication) {
        User user = authUtil.loggedInUser();
        return UserInfoMapper.toUserInfoResponse(user);
    }

    @Override
    public UserInfoResponse updateProfile(UpdateProfileRequest request, Authentication authentication) {
        User user = authUtil.loggedInUser();

        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            if (!request.getEmail().equals(user.getEmail())
                    && userRepository.existsByEmail(request.getEmail())) {
                throw new EmailAlreadyExistsException("Email is already in use by another account");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        userRepository.save(user);

        return UserInfoMapper.toUserInfoResponse(user);
    }

    @Override
    public void changePassword(ChangePasswordRequest request, Authentication authentication) {
        User user = authUtil.loggedInUser();

        if (!encoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        user.setPassword(encoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    /**
     * Validation and I/O now sit in separate paths so a bad request maps to 400,
     * not 500. The old code wrapped everything in {@code try/catch(Exception)}
     * and rethrew as {@code RuntimeException}, which the global handler treated
     * as a server error — a caller uploading a {@code .txt} saw a 500 with the
     * message "Failed to upload avatar: Invalid file type...", and legitimate
     * disk I/O failures were indistinguishable from client mistakes.
     *
     * <p>{@link ImageUploadValidator#validate} throws {@link APIException} for
     * every client-side failure (missing file, disallowed extension, bytes not
     * matching the declared type) — mapped to 400 by the global handler. The
     * try/catch below is now scoped to the actual disk write, where an
     * {@code IOException} is a legitimate 500.
     */
    public String uploadAvatar(MultipartFile file, Authentication authentication) {
        User user = authUtil.loggedInUser();
        ImageUploadValidator.Validated validated = imageValidator.validate(file);

        try {
            String uploadDir = "images/avatars/";
            java.nio.file.Path uploadPath = java.nio.file.Paths.get(uploadDir);
            if (!java.nio.file.Files.exists(uploadPath)) {
                java.nio.file.Files.createDirectories(uploadPath);
            }

            String fileName = "avatar_" + user.getUserId() + "_"
                    + System.currentTimeMillis() + validated.extension();
            java.nio.file.Path filePath = uploadPath.resolve(fileName);
            java.nio.file.Files.write(filePath, validated.bytes());

            String avatarUrl = imageBaseUrl + "/avatars/" + fileName;
            user.setAvatarUrl(avatarUrl);
            userRepository.save(user);

            return avatarUrl;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to save avatar to disk", e);
        }
    }

}
