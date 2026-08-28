package com.ecommerce.project.service.outbox.payload;

/**
 * Outbox payload for an Art. 15 data export.
 *
 * <p>Only identifiers travel here — unlike the other payloads, the handler
 * deliberately re-reads the account, because building the archive <em>is</em>
 * the work being deferred. The recipient's email is carried anyway so the
 * notification still goes to the address that asked, even if the account's
 * email changed between request and dispatch.
 *
 * @param exportId       the {@code gdpr_export} row to fill in
 * @param userId         whose data to collect
 * @param recipientEmail where to send the download link
 * @param recipientName  display name for the greeting
 */
public record GdprExportOutboxPayload(
        Long exportId,
        Long userId,
        String recipientEmail,
        String recipientName) {
}
