package com.ecommerce.project.service.email;

import java.util.ArrayList;
import java.util.List;

/**
 * One outbound email, assembled fluently and immutable once built. The sender
 * address is not on here — {@code EmailService.send} stamps the single
 * configured {@code from} on every message.
 *
 * <p>Required fields ({@code to}, {@code subject}) are arguments to
 * {@link #to(String, String)}; everything else is an optional fluent step with a
 * sensible default. Validation happens once, in {@link Builder#build()}.
 *
 * <pre>{@code
 * EmailMessage.to(customer, "Order confirmed - #" + id)
 *         .html(renderedBody)
 *         .attach("invoice-" + id + ".pdf", pdfBytes)
 *         .build();
 * }</pre>
 */
public record EmailMessage(
        String to,
        String subject,
        String body,
        boolean html,
        String replyTo,
        List<Attachment> attachments) {

    /** A file to hang off the message. {@code content} is the raw bytes. */
    public record Attachment(String filename, byte[] content) {
    }

    public static Builder to(String to, String subject) {
        return new Builder(to, subject);
    }

    public static final class Builder {
        private final String to;
        private final String subject;
        private String body = "";
        private boolean html = true;
        private String replyTo;
        private final List<Attachment> attachments = new ArrayList<>();

        private Builder(String to, String subject) {
            this.to = to;
            this.subject = subject;
        }

        /** HTML body (the default content type). */
        public Builder html(String body) {
            this.body = body;
            this.html = true;
            return this;
        }

        /** Plain-text body — for the admin-facing notices that are not styled. */
        public Builder text(String body) {
            this.body = body;
            this.html = false;
            return this;
        }

        public Builder replyTo(String replyTo) {
            this.replyTo = replyTo;
            return this;
        }

        public Builder attach(String filename, byte[] content) {
            this.attachments.add(new Attachment(filename, content));
            return this;
        }

        public EmailMessage build() {
            if (to == null || to.isBlank()) {
                throw new IllegalStateException("email recipient (to) is required");
            }
            if (subject == null || subject.isBlank()) {
                throw new IllegalStateException("email subject is required");
            }
            return new EmailMessage(to, subject, body, html, replyTo, List.copyOf(attachments));
        }
    }
}
