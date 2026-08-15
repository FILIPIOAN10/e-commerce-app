package com.ecommerce.project.payload;

import lombok.Data;

@Data
public class UserActivityLogDTO {
    private Long id;
    private String username;
    private String action;
    private String details;
    private String createdAt;
}
