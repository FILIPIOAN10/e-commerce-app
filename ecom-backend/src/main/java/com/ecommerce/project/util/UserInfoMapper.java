package com.ecommerce.project.util;

import com.ecommerce.project.model.User;
import com.ecommerce.project.security.response.UserInfoResponse;

import java.util.List;
import java.util.stream.Collectors;

public final class UserInfoMapper {

    private UserInfoMapper() {
    }

    public static UserInfoResponse toUserInfoResponse(User user) {
        if (user == null) {
            return null;
        }
        List<String> roles = user.getRoles().stream()
                .map(role -> role.getRoleName().name())
                .collect(Collectors.toList());
        return new UserInfoResponse(
                user.getUserId(),
                user.getUserName(),
                roles,
                user.getEmail(),
                user.getPhone(),
                user.getAvatarUrl()
        );
    }
}
