package com.ecommerce.project.payload;

public record DeviceSessionResponse(
        String token,
        String deviceInfo,
        String ipAddress,
        long createdAt,
        long lastUsedAt,
        boolean current
) {
}
