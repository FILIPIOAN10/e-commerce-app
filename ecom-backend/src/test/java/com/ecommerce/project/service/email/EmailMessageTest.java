package com.ecommerce.project.service.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailMessageTest {

    @Test
    void buildsAnHtmlMessageByDefault() {
        EmailMessage message = EmailMessage.to("buyer@example.com", "Order confirmed")
                .html("<p>thanks</p>")
                .build();

        assertThat(message.to()).isEqualTo("buyer@example.com");
        assertThat(message.subject()).isEqualTo("Order confirmed");
        assertThat(message.body()).isEqualTo("<p>thanks</p>");
        assertThat(message.html()).isTrue();
        assertThat(message.replyTo()).isNull();
        assertThat(message.attachments()).isEmpty();
    }

    @Test
    void textTogglesOffHtml() {
        EmailMessage message = EmailMessage.to("admin@example.com", "Unlock request")
                .text("plain body")
                .build();

        assertThat(message.html()).isFalse();
        assertThat(message.body()).isEqualTo("plain body");
    }

    @Test
    void carriesReplyToAndAttachments() {
        EmailMessage message = EmailMessage.to("admin@example.com", "Contact form")
                .text("hi")
                .replyTo("customer@example.com")
                .attach("invoice.pdf", new byte[]{1, 2, 3})
                .build();

        assertThat(message.replyTo()).isEqualTo("customer@example.com");
        assertThat(message.attachments()).singleElement()
                .satisfies(a -> {
                    assertThat(a.filename()).isEqualTo("invoice.pdf");
                    assertThat(a.content()).containsExactly(1, 2, 3);
                });
    }

    @Test
    void attachmentListIsImmutable() {
        EmailMessage message = EmailMessage.to("a@example.com", "s").html("b")
                .attach("f", new byte[]{0})
                .build();

        assertThatThrownBy(() -> message.attachments().add(new EmailMessage.Attachment("x", new byte[]{})))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void recipientAndSubjectAreRequired() {
        assertThatThrownBy(() -> EmailMessage.to(null, "s").build())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EmailMessage.to("  ", "s").build())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EmailMessage.to("a@example.com", null).build())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EmailMessage.to("a@example.com", "").build())
                .isInstanceOf(IllegalStateException.class);
    }
}
