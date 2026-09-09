package com.ecommerce.project.service;

import com.ecommerce.project.config.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every SMTP timeout JavaMail understands defaults to <em>infinite</em>, and a
 * mail server that accepts the TCP connection and then stops answering is not a
 * hypothetical: a partial outage, a firewall that black-holes rather than
 * rejects, or a throttled sender all look the same from here. The send then
 * parks its thread for good — no exception to catch, no log line, because the
 * call never returns.
 *
 * <p>That thread belongs to the outbox dispatcher, which also carries refunds
 * and GDPR exports, so one stalled send stops the queue for everything and only
 * a restart clears it. These three properties are the whole defence, and they
 * are three easily-deleted lines in a properties file — hence this test.
 *
 * <p>It reads them off the {@link JavaMailSender} bean rather than out of the
 * environment on purpose: a misspelled key ({@code mail.smtp.readtimeout}, say)
 * would still be present as a property and still be silently ignored by
 * JavaMail. Asserting on what the sender actually carries is the difference
 * between checking that someone wrote a setting down and checking that it took
 * effect.
 *
 * <p>The {@code test} profile blanks the mail host and credentials but does not
 * touch the timeouts, so what this sees is what {@code application.properties}
 * ships to production.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@DisplayName("SMTP timeouts")
class SmtpTimeoutConfigurationTest {

    /** Anything longer than this is not a timeout, it is a stall with paperwork. */
    private static final int MAX_ACCEPTABLE_MS = 30_000;

    @Autowired
    private JavaMailSender mailSender;

    private Properties mailProperties() {
        assertThat(mailSender)
                .as("the configured JavaMailSender")
                .isInstanceOf(JavaMailSenderImpl.class);
        return ((JavaMailSenderImpl) mailSender).getJavaMailProperties();
    }

    /**
     * The timeout under {@code key}, in milliseconds, asserting on the way through
     * that it is present and parseable — so a missing property reports itself
     * rather than surfacing as a {@code NumberFormatException} on a null string.
     */
    private int timeoutMillis(String key) {
        String value = mailProperties().getProperty(key);

        assertThat(value)
                .as("%s is unset, so JavaMail will wait forever on a server that stops responding", key)
                .isNotNull()
                .isNotBlank()
                .as("%s must be a whole number of milliseconds", key)
                .matches("\\d+");

        return Integer.parseInt(value.trim());
    }

    @ParameterizedTest(name = "{0} is set and bounded")
    @ValueSource(strings = {
            "mail.smtp.connectiontimeout",
            "mail.smtp.timeout",
            "mail.smtp.writetimeout"
    })
    @DisplayName("each timeout is configured, positive, and short enough to matter")
    void everySmtpTimeoutIsBounded(String key) {
        assertThat(timeoutMillis(key))
                .as("%s must be a positive, bounded wait", key)
                .isPositive()
                .isLessThanOrEqualTo(MAX_ACCEPTABLE_MS);
    }

    @Test
    @DisplayName("connecting is given less time than the exchange that follows it")
    void connectTimeoutIsTheTightestOfTheThree() {
        int connect = timeoutMillis("mail.smtp.connectiontimeout");
        int read = timeoutMillis("mail.smtp.timeout");
        int write = timeoutMillis("mail.smtp.writetimeout");

        // A TCP handshake either happens quickly or is not going to happen;
        // delivering the message legitimately takes longer. Ordering them this
        // way means an unreachable host fails fast without cutting off a large
        // attachment that is genuinely still uploading.
        assertThat(connect)
                .as("connect timeout should be no longer than the read timeout")
                .isLessThanOrEqualTo(read);
        assertThat(connect)
                .as("connect timeout should be no longer than the write timeout")
                .isLessThanOrEqualTo(write);
    }
}
