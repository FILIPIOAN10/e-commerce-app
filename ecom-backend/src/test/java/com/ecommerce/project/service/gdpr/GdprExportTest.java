package com.ecommerce.project.service.gdpr;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.GdprExport;
import com.ecommerce.project.model.GdprExportStatus;
import com.ecommerce.project.model.User;
import com.ecommerce.project.repository.GdprExportRepository;
import com.ecommerce.project.repository.OutboxEventRepository;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.service.EmailService;
import com.ecommerce.project.service.outbox.OutboxProcessor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

/**
 * §3.1 (GDPR Art. 15): a user can take their data with them.
 *
 * <p>The request is only recorded synchronously; the archive is built by the
 * outbox handler, which the test drives directly so delivery is deterministic.
 * {@link EmailService} is mocked — there is no SMTP here, and the link it is
 * handed is exactly what the test needs to exercise the download.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class GdprExportTest {

    @Autowired private GdprService gdprService;
    @Autowired private GdprExportPurgeService purgeService;
    @Autowired private GdprExportRepository gdprExportRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private OutboxProcessor outboxProcessor;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager txManager;

    @MockitoBean private EmailService emailService;

    // Short enough for User.userName (max 20) and email (max 50); unique per run.
    private final String tag = "gx" + Long.toUnsignedString(System.nanoTime(), 36);
    private GdprFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new GdprFixture(entityManager, txManager, tag);
        fixture.alignIdentitySequences();
    }

    @AfterEach
    void tearDown() {
        fixture.cleanUp();
    }

    @Test
    @DisplayName("the archive holds every domain of the requester's data, and nothing of anyone else's")
    void archiveIsCompleteAndScopedToOneUser() {
        GdprFixture.Customer mine = fixture.createFullyPopulatedCustomer("hash", "pw");
        GdprFixture.Customer theirs = fixture.createFullyPopulatedCustomer("hash", "pw");

        Map<String, String> archive = requestAndBuildArchive(mine);

        assertThat(archive).containsKeys(
                "manifest.json", "account.json", "addresses.json", "orders.json", "reviews.json",
                "questions.json", "wishlist.json", "carts.json", "notifications.json",
                "activity-log.json", "subscriptions.json", "returns.json");

        assertThat(archive.get("account.json")).contains(mine.email(), mine.username());
        assertThat(archive.get("addresses.json")).contains("12 Privacy Lane");
        assertThat(archive.get("orders.json"))
                .contains("\"orderId\" : " + mine.orderId())
                .contains("Delivered")
                .as("order lines travel with the order").contains(tag + "-widget");
        assertThat(archive.get("reviews.json")).contains("Written by " + mine.username());
        assertThat(archive.get("questions.json")).contains("Asked by " + mine.username());
        assertThat(archive.get("wishlist.json")).contains("\"productId\" : " + mine.productId());
        assertThat(archive.get("carts.json")).contains("\"cartId\" : " + mine.cartId());
        assertThat(archive.get("notifications.json")).contains("Your order was delivered, " + mine.username());
        assertThat(archive.get("activity-log.json")).contains("signed in from the gdpr fixture");

        String everything = String.join("\n", archive.values());
        assertThat(everything)
                .as("no trace of the other customer anywhere in the archive")
                .doesNotContain(theirs.username())
                .doesNotContain(theirs.email());
    }

    @Test
    @DisplayName("the emailed link downloads the archive exactly once")
    void downloadLinkIsSingleUse() {
        GdprFixture.Customer customer = fixture.createFullyPopulatedCustomer("hash", "pw");
        String token = requestAndCaptureLinkToken(customer);

        GdprArchive archive = gdprService.downloadExport(token);
        assertThat(archive.fileName()).endsWith(".zip");
        assertThat(archive.content()).isNotEmpty();

        assertThatThrownBy(() -> gdprService.downloadExport(token))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("already used");
    }

    @Test
    @DisplayName("a second request while one is pending is answered, not queued")
    void concurrentRequestsDoNotQueueASecondBuild() {
        GdprFixture.Customer customer = fixture.createFullyPopulatedCustomer("hash", "pw");
        User user = userRepository.findById(customer.userId()).orElseThrow();

        gdprService.requestExport(user);
        long rowsAfterFirst = gdprExportRepository.count();

        assertThat(gdprService.requestExport(user)).contains("already being prepared");
        assertThat(gdprExportRepository.count())
                .as("no second archive row").isEqualTo(rowsAfterFirst);
    }

    @Test
    @DisplayName("an expired archive is purged and its link stops working")
    void expiredArchiveIsPurged() {
        GdprFixture.Customer customer = fixture.createFullyPopulatedCustomer("hash", "pw");
        String token = requestAndCaptureLinkToken(customer);

        GdprExport export = liveExportFor(customer);
        export.setExpiresAt(Instant.now().minusSeconds(60));
        gdprExportRepository.saveAndFlush(export);

        assertThat(purgeService.purgeExpired()).isGreaterThanOrEqualTo(1);

        GdprExport purged = gdprExportRepository.findById(export.getId()).orElseThrow();
        assertThat(purged.getStatus()).isEqualTo(GdprExportStatus.EXPIRED);
        assertThat(purged.getPayload()).as("the copy of their data is gone").isNull();
        assertThat(purged.getByteSize()).as("but the record that it was served remains").isNotNull();

        assertThatThrownBy(() -> gdprService.downloadExport(token))
                .isInstanceOf(APIException.class);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Requests an export, drains the outbox, and returns the ZIP's contents by file name. */
    private Map<String, String> requestAndBuildArchive(GdprFixture.Customer customer) {
        String token = requestAndCaptureLinkToken(customer);
        return unzip(gdprService.downloadExport(token).content());
    }

    /** Requests an export, drains the outbox, and digs the token out of the emailed link. */
    private String requestAndCaptureLinkToken(GdprFixture.Customer customer) {
        User user = userRepository.findById(customer.userId()).orElseThrow();
        gdprService.requestExport(user);

        assertThat(outboxProcessor.processBatch())
                .as("the request reached the outbox").isGreaterThanOrEqualTo(1);

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendGdprExportReadyEmail(
                anyString(), anyString(), link.capture(), anyLong());

        String url = link.getValue();
        return url.substring(url.indexOf("token=") + "token=".length());
    }

    private GdprExport liveExportFor(GdprFixture.Customer customer) {
        return gdprExportRepository.findLatestLiveForUser(customer.userId(), Instant.now()).orElseThrow();
    }

    private Map<String, String> unzip(byte[] zipBytes) {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return entries;
    }
}
