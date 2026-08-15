package com.ecommerce.project.service;

import com.ecommerce.project.model.UserActivityLog;
import com.ecommerce.project.payload.UserActivityLogResponse;
import org.springframework.data.domain.Pageable;

public interface UserActivityLogService {
    UserActivityLog log(String username, String action, String details);
    UserActivityLogResponse getLogs(Pageable pageable);
}
