package com.ecommerce.project.service;

import com.ecommerce.project.payload.UserResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

public interface UserManagementService {

    UserResponse getAllSellers(Pageable pageable);

    ResponseEntity<?> getPasswordHint(String username);

    UserResponse getAllUsers(Pageable pageable);

    void deleteUser(Long userId);
}
