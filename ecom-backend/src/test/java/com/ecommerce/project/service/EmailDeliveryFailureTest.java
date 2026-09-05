package com.ecommerce.project.service;

import com.ecommerce.project.exception.EmailDeliveryException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;


import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A send that fails reaches the caller instead of being logged and forgotten.
 *
 * <p>Five of these methods caught {@code Exception}, logged it and returned
 * normally, so the endpoints above them answered "check your email" for a
 * message that was never sent — invisible to the user, who simply waited. The
 * contact endpoint was the clearest case: its controller already had a "failed
 * to send, please try again" branch that could never run.
 *
 * <p>Signup is the deliberate exception. {@code AuthServiceImpl} is
 * {@code @Transactional}, so letting the failure out would roll the
 * registration back and the account would silently not exist. An unverified
 * account is a state the domain already models and has a resend endpoint for.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Email delivery failures surface")
class EmailDeliveryFailureTest {

    @Mock private JavaMailSender mailSender;
    @Mock private InvoiceService invoiceService;
    @Mock private EmailTemplateService emailTemplateService;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, invoiceService, emailTemplateService);
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@example.com");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://shop.example.com");

        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
        when(emailTemplateService.render(anyString(), any())).thenReturn("<html>body</html>");
        // SMTP is down.
        doThrow(new MailSendException("connection refused")).when(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("a failed password reset email is not reported as sent")
    void passwordResetFailureIsVisible() {
        assertThatThrownBy(() -> emailService.sendPasswordResetEmail("user@example.com", "tok"))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("user@example.com");
    }

    @Test
    @DisplayName("a failed verification email is not reported as sent")
    void verificationFailureIsVisible() {
        assertThatThrownBy(() -> emailService.sendVerificationEmail("user@example.com", "tok"))
                .isInstanceOf(EmailDeliveryException.class);
    }

    @Test
    @DisplayName("a failed GDPR erasure link is not reported as sent")
    void gdprErasureFailureIsVisible() {
        // requestErasure answers "we sent a link that will permanently delete
        // your account"; silence here means Art. 17 cannot be exercised at all.
        assertThatThrownBy(() -> emailService.sendGdprErasureConfirmationEmail(
                "user@example.com", "User", "https://shop.example.com/gdpr/erase?token=t", 60))
                .isInstanceOf(EmailDeliveryException.class);
    }

    @Test
    @DisplayName("a failed contact message is not reported as sent")
    void contactFailureIsVisible() {
        assertThatThrownBy(() -> emailService.sendContactMessage("Ana", "ana@example.com", "hello"))
                .isInstanceOf(EmailDeliveryException.class);
    }

    @Test
    @DisplayName("a failed unlock request is not reported as sent")
    void unlockRequestFailureIsVisible() {
        assertThatThrownBy(() -> emailService.sendUnlockRequestEmail("ana"))
                .isInstanceOf(EmailDeliveryException.class);
    }

}
