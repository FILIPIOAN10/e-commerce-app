package com.ecommerce.project.service;

import com.ecommerce.project.exception.EmailDeliveryException;
import com.ecommerce.project.model.AppRole;
import com.ecommerce.project.model.Role;
import com.ecommerce.project.repository.RoleRepository;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.security.redis.EmailVerificationService;
import com.ecommerce.project.security.request.SignupRequest;
import com.ecommerce.project.security.response.MessageResponse;
import com.ecommerce.project.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * Signup is the one place a failed email must not become the response.
 *
 * <p>Everywhere else {@link EmailService} now lets an
 * {@link EmailDeliveryException} out, because the alternative is telling a user
 * to check an inbox nothing was sent to. Here it is caught:
 * {@code AuthServiceImpl} is {@code @Transactional}, so letting it out would
 * roll the registration back and the account would silently not exist, while
 * the user was told signup failed. An unverified account is a state the domain
 * already models — {@code verified = false} with a resend endpoint — so the
 * account is kept and the response says what happened.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Signup survives a verification-email failure")
class SignupMailFailureTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder encoder;
    @Mock private EmailService emailService;
    @Mock private EmailVerificationService emailVerificationService;

    @InjectMocks private AuthServiceImpl authService;

    private SignupRequest signupRequest() {
        SignupRequest request = new SignupRequest();
        request.setUsername("ana");
        request.setEmail("ana@example.com");
        request.setPassword("Str0ng!Passw0rd");
        return request;
    }

    @Test
    @DisplayName("the account is kept and the response points at resend")
    void registrationSurvivesMailFailure() {
        ReflectionTestUtils.setField(authService, "skipVerificationEmail", false);
        when(userRepository.existsByUserName(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(encoder.encode(anyString())).thenReturn("hashed");
        when(roleRepository.findByRoleName(AppRole.ROLE_USER))
                .thenReturn(Optional.of(new Role(AppRole.ROLE_USER)));
        when(emailVerificationService.generateVerificationToken(anyString())).thenReturn("tok");
        doThrow(new EmailDeliveryException("verification email", new RuntimeException("smtp down")))
                .when(emailService).sendVerificationEmail(anyString(), anyString());

        MessageResponse response = authService.register(signupRequest());

        // Discarding a valid signup over an SMTP hiccup would leave the user
        // told it failed with no account to show for it — and unable to retry,
        // since the username and email would only be free after a rollback that
        // the user cannot observe either way.
        assertThat(response.getMessage()).contains("registered successfully");
        assertThat(response.getMessage()).contains("resend verification");
        verify(userRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("a healthy send still gets the ordinary message")
    void registrationReportsNormallyWhenMailWorks() {
        ReflectionTestUtils.setField(authService, "skipVerificationEmail", false);
        when(userRepository.existsByUserName(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(encoder.encode(anyString())).thenReturn("hashed");
        when(roleRepository.findByRoleName(AppRole.ROLE_USER))
                .thenReturn(Optional.of(new Role(AppRole.ROLE_USER)));
        when(emailVerificationService.generateVerificationToken(anyString())).thenReturn("tok");

        MessageResponse response = authService.register(signupRequest());

        assertThat(response.getMessage()).contains("check your email");
    }
}
