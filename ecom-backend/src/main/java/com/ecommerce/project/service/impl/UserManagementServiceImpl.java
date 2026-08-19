package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.UserNotFoundException;
import com.ecommerce.project.model.AppRole;
import com.ecommerce.project.model.Role;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.UserDTO;
import com.ecommerce.project.payload.UserResponse;
import com.ecommerce.project.repository.RoleRepository;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.security.response.MessageResponse;
import com.ecommerce.project.service.AdminAuditLogService;
import com.ecommerce.project.service.UserManagementService;
import com.ecommerce.project.util.AuthUtil;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserManagementServiceImpl implements UserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ModelMapper modelMapper;
    private final AuthUtil authUtil;
    private final AdminAuditLogService adminAuditLogService;

    public UserManagementServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
                                     ModelMapper modelMapper, AuthUtil authUtil,
                                     AdminAuditLogService adminAuditLogService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.modelMapper = modelMapper;
        this.authUtil = authUtil;
        this.adminAuditLogService = adminAuditLogService;
    }

    @Override
    public UserResponse getAllSellers(Pageable pageable) {
        Page<User> allUsers = userRepository.findByRoleName(AppRole.ROLE_SELLER, pageable);
        List<UserDTO> userDTOs = allUsers.getContent()
                .stream()
                .map(p -> modelMapper.map(p, UserDTO.class))
                .collect(Collectors.toList());
        UserResponse response = new UserResponse();
        response.setContent(userDTOs);
        response.setPageNumber(allUsers.getNumber());
        response.setTotalElements(allUsers.getTotalElements());
        response.setTotalPages(allUsers.getTotalPages());
        response.setLastPage(allUsers.isLast());
        return response;
    }

    @Override
    public ResponseEntity<?> getPasswordHint(String username) {
        Optional<User> userOpt = userRepository.findByUserName(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("User not found"));
        }
        String hint = userOpt.get().getPasswordHint();
        if (hint == null || hint.isEmpty()) {
            return ResponseEntity.ok(new MessageResponse("No hint available"));
        }
        return ResponseEntity.ok(new MessageResponse(hint));
    }

    @Override
    public UserResponse getAllUsers(Pageable pageable) {
        Page<User> allUsers = userRepository.findAll(pageable);
        List<UserDTO> userDTOs = allUsers.getContent()
                .stream()
                .map(p -> modelMapper.map(p, UserDTO.class))
                .collect(Collectors.toList());
        UserResponse response = new UserResponse();
        response.setContent(userDTOs);
        response.setPageNumber(allUsers.getNumber());
        response.setTotalElements(allUsers.getTotalElements());
        response.setTotalPages(allUsers.getTotalPages());
        response.setLastPage(allUsers.isLast());
        return response;
    }

    @Override
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
        userRepository.delete(user);
    }

    @Override
    public void updateUserRole(Long userId, String roleName) {
        AppRole appRole;
        try {
            appRole = AppRole.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            throw new APIException("Invalid role: " + roleName);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        String oldRoles = user.getRoles().stream()
                .map(r -> r.getRoleName().name())
                .collect(Collectors.joining(","));

        Role role = roleRepository.findByRoleName(appRole)
                .orElseThrow(() -> new APIException("Role not found: " + roleName));

        user.setRoles(new java.util.HashSet<>(java.util.Set.of(role)));
        userRepository.save(user);

        User admin = authUtil.loggedInUser();
        adminAuditLogService.logRoleChange(
                admin.getUserId(),
                admin.getUserName(),
                userId,
                oldRoles,
                roleName
        );
    }
}
