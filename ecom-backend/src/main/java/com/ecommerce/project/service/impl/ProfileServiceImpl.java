package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.EmailAlreadyExistsException;
import com.ecommerce.project.exception.InvalidCredentialsException;
import com.ecommerce.project.model.User;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.security.request.ChangePasswordRequest;
import com.ecommerce.project.security.request.UpdateProfileRequest;
import com.ecommerce.project.security.response.UserInfoResponse;
import com.ecommerce.project.service.ProfileService;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.UserInfoMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@Transactional
public class ProfileServiceImpl implements ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final AuthUtil authUtil;

    @Value("${image.base.url}")
    private String imageBaseUrl;

    public ProfileServiceImpl(UserRepository userRepository, PasswordEncoder encoder, AuthUtil authUtil) {
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.authUtil = authUtil;
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
    public String uploadAvatar(MultipartFile file, Authentication authentication) {
        User user = authUtil.loggedInUser();

        try {
            String uploadDir = "images/avatars/";
            java.nio.file.Path uploadPath = java.nio.file.Paths.get(uploadDir);
            if (!java.nio.file.Files.exists(uploadPath)) {
                java.nio.file.Files.createDirectories(uploadPath);
            }

            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
            }

            List<String> allowedExtensions = List.of(".jpg", ".jpeg", ".png", ".gif", ".webp");
            if (!allowedExtensions.contains(fileExtension)) {
                throw new RuntimeException("Invalid file type. Allowed: jpg, jpeg, png, gif, webp");
            }

            byte[] fileBytes = file.getBytes();
            if (!isValidImageContent(fileBytes, fileExtension)) {
                throw new RuntimeException("Invalid file content. File does not match the declared image type.");
            }

            String fileName = "avatar_" + user.getUserId() + "_" + System.currentTimeMillis() + fileExtension;
            java.nio.file.Path filePath = uploadPath.resolve(fileName);
            java.nio.file.Files.write(filePath, fileBytes);

            String avatarUrl = imageBaseUrl + "/avatars/" + fileName;
            user.setAvatarUrl(avatarUrl);
            userRepository.save(user);

            return avatarUrl;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload avatar: " + e.getMessage());
        }
    }

    private boolean isValidImageContent(byte[] bytes, String extension) {
        if (bytes == null || bytes.length < 8) {
            return false;
        }

        boolean matchesMagic = false;
        if (startsWith(bytes, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            matchesMagic = true; // JPEG
        } else if (startsWith(bytes, new byte[]{0x47, 0x49, 0x46, 0x38})) {
            matchesMagic = true; // GIF
        } else if (startsWith(bytes, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            matchesMagic = true; // PNG
        } else if (startsWith(bytes, new byte[]{0x52, 0x49, 0x46, 0x46}) && bytes.length >= 12) {
            // WEBP: RIFF....WEBP
            matchesMagic = bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50;
        }

        if (!matchesMagic) {
            return false;
        }

        // Optional: double check that the declared extension matches the magic type
        return switch (extension) {
            case ".jpg", ".jpeg" -> startsWith(bytes, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case ".png" -> startsWith(bytes, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            case ".gif" -> startsWith(bytes, new byte[]{0x47, 0x49, 0x46, 0x38});
            case ".webp" -> startsWith(bytes, new byte[]{0x52, 0x49, 0x46, 0x46});
            default -> false;
        };
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
