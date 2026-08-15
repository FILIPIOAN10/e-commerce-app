package com.ecommerce.project.payload;

import lombok.Data;

import java.util.List;

@Data
public class UserActivityLogResponse {
    private List<UserActivityLogDTO> content;
    private Long totalElements;
}
