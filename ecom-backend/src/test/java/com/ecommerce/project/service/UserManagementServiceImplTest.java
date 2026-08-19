package com.ecommerce.project.service;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.AppRole;
import com.ecommerce.project.model.Role;
import com.ecommerce.project.model.User;
import com.ecommerce.project.repository.RoleRepository;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.service.impl.UserManagementServiceImpl;
import com.ecommerce.project.util.AuthUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private ModelMapper modelMapper;
    @Mock private AuthUtil authUtil;
    @Mock private AdminAuditLogService adminAuditLogService;

    @InjectMocks
    private UserManagementServiceImpl userManagementService;

    @Test
    void updateUserRole_changesRoleAndLogsAudit() {
        User user = new User();
        user.setUserId(10L);
        user.setUserName("john");
        Role userRole = new Role(AppRole.ROLE_USER);
        user.setRoles(Set.of(userRole));

        User admin = new User();
        admin.setUserId(1L);
        admin.setUserName("admin");

        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(roleRepository.findByRoleName(AppRole.ROLE_SELLER))
                .thenReturn(Optional.of(new Role(AppRole.ROLE_SELLER)));
        when(authUtil.loggedInUser()).thenReturn(admin);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userManagementService.updateUserRole(10L, "ROLE_SELLER");

        assertEquals(1, user.getRoles().size());
        assertTrue(user.getRoles().stream()
                .anyMatch(r -> r.getRoleName() == AppRole.ROLE_SELLER));
        verify(adminAuditLogService).logRoleChange(1L, "admin", 10L, "ROLE_USER", "ROLE_SELLER");
    }

    @Test
    void updateUserRole_throwsForInvalidRole() {
        assertThrows(APIException.class, () -> userManagementService.updateUserRole(10L, "ROLE_UNKNOWN"));
    }
}
