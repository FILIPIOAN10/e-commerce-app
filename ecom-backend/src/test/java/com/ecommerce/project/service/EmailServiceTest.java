package com.ecommerce.project.service;

import com.ecommerce.project.payload.OrderDTO;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Template-Method skeleton: every {@code send*Email} funnels through one
 * {@code send(EmailMessage)} that stamps the from address, sets the subject and
 * recipient, chooses the body content type, and attaches files. These assert the
 * envelope the skeleton produces, not the copy inside each template.
 */
class EmailServiceTest {

    private JavaMailSenderImpl mailSender;
    private InvoiceService invoiceService;
    private EmailTemplateService templateService;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        // A real sender so createMimeMessage() returns a usable MimeMessage; send() is stubbed.
        mailSender = spy(new JavaMailSenderImpl());
        doNothing().when(mailSender).send(any(MimeMessage.class));

        invoiceService = mock(InvoiceService.class);
        templateService = mock(EmailTemplateService.class);
        when(templateService.render(any(), any())).thenReturn("<p>rendered</p>");

        emailService = new EmailService(mailSender, invoiceService, templateService);
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@shop.example.com");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://shop.example.com");
    }

    private MimeMessage captureSent() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void stampsFromSubjectAndRecipient() throws Exception {
        emailService.sendPasswordResetEmail("user@example.com", "tok123");

        MimeMessage sent = captureSent();
        assertThat(sent.getSubject()).isEqualTo("Password Reset Request");
        assertThat(sent.getAllRecipients()).extracting(Object::toString).containsExactly("user@example.com");
        assertThat(sent.getFrom()).extracting(Object::toString).containsExactly("noreply@shop.example.com");
    }

    @Test
    void contactMessageRepliesToTheSender() throws Exception {
        emailService.sendContactMessage("Ana", "ana@example.com", "hello there");

        MimeMessage sent = captureSent();
        assertThat(sent.getReplyTo()).extracting(Object::toString).containsExactly("ana@example.com");
        // routed to the shop inbox, not the person who filled the form
        assertThat(sent.getRecipients(Message.RecipientType.TO)).extracting(Object::toString)
                .containsExactly("noreply@shop.example.com");
    }

    @Test
    void orderConfirmationCarriesTheInvoicePdfAsAnAttachment() throws Exception {
        when(invoiceService.generateInvoicePdf(42L)).thenReturn(new byte[]{1, 2, 3, 4});

        OrderDTO order = new OrderDTO();
        order.setOrderId(42L);
        order.setTotalAmount(new BigDecimal("19.99"));

        emailService.sendOrderConfirmationEmail("buyer@example.com", order);

        MimeMessage sent = captureSent();
        assertThat(sent.getSubject()).isEqualTo("Order Confirmation - #42");

        assertThat(sent.getContent()).isInstanceOf(MimeMultipart.class);
        MimeMultipart body = (MimeMultipart) sent.getContent();
        boolean hasInvoice = false;
        for (int i = 0; i < body.getCount(); i++) {
            String fileName = body.getBodyPart(i).getFileName();
            if ("invoice-42.pdf".equals(fileName)) {
                hasInvoice = true;
            }
        }
        assertThat(hasInvoice).as("invoice PDF attached").isTrue();
        verify(invoiceService).generateInvoicePdf(42L);
    }
}
