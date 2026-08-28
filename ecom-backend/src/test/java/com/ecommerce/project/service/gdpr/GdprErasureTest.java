package com.ecommerce.project.service.gdpr;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.exception.InvalidCredentialsException;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.User;
import com.ecommerce.project.repository.AdminAuditLogRepository;
import com.ecommerce.project.repository.CartRepository;
import com.ecommerce.project.repository.NotificationRepository;
import com.ecommerce.project.repository.OrderRepository;
import com.ecommerce.project.repository.PaymentRepository;
import com.ecommerce.project.repository.QuestionRepository;
import com.ecommerce.project.repository.ReviewRepository;
import com.ecommerce.project.repository.UserActivityLogRepository;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.repository.WishlistRepository;
import com.ecommerce.project.security.request.LoginRequest;
import com.ecommerce.project.service.AuthService;
import com.ecommerce.project.service.EmailService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

/**
 * §3.1 (GDPR Art. 17): a user can be forgotten — as far as the tax authority
 * allows.
 *
 * <p>The point of these tests is the seam between the two halves: what must
 * vanish, and what must survive stripped of its owner. Getting either side wrong
 * is a compliance failure in one direction or an accounting hole in the other.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class GdprErasureTest {

    @Autowired private GdprService gdprService;
    @Autowired private AuthService authService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private WishlistRepository wishlistRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserActivityLogRepository userActivityLogRepository;
    @Autowired private AdminAuditLogRepository adminAuditLogRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager txManager;

    @MockitoBean private EmailService emailService;

    private static final String RAW_PASSWORD = "correct-horse-battery";

    private final String tag = "ge" + Long.toUnsignedString(System.nanoTime(), 36);
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
    @DisplayName("erasure deletes personal content, anonymises the order history, and closes the account")
    void erasureDeletesWhatItCanAndAnonymisesTheRest() {
        GdprFixture.Customer customer = newCustomer();
        eraseFully(customer);

        User erased = userRepository.findById(customer.userId()).orElseThrow();

        // Deleted outright.
        assertThat(cartRepository.findByUserUserIdOrderByCartIdAsc(customer.userId())).isEmpty();
        assertThat(wishlistRepository.findByUserOrderByIdAsc(erased)).isEmpty();
        assertThat(reviewRepository.findByUserOrderByIdAsc(erased)).isEmpty();
        assertThat(questionRepository.findByUserOrderByIdAsc(erased)).isEmpty();
        assertThat(notificationRepository.findByRecipientEmailOrderByIdAsc(customer.email())).isEmpty();
        assertThat(userActivityLogRepository.findByUsernameOrderByCreatedAtDesc(customer.username())).isEmpty();

        // Kept, because tax law says so — but no longer about a person.
        Order order = orderRepository.findByIdWithDetails(customer.orderId()).orElseThrow();
        assertThat(order.getEmail())
                .as("the order survives with an unusable address")
                .isNotEqualTo(customer.email())
                .startsWith("deleted-")
                .endsWith("@anonymised.invalid");
        assertThat(order.getTotalAmount()).as("the amount is not personal data").isEqualTo(25.0);
        assertThat(order.getOrderStatus()).isEqualTo("Delivered");
        assertThat(order.getOrderItems()).as("the lines are intact").hasSize(1);
        assertThat(order.getAddress().getStreet()).isEqualTo("REDACTED");
        assertThat(order.getAddress().getUser()).isNull();
        assertThat(paymentRepository.findById(customer.paymentId()).orElseThrow().getPgResponseMessage())
                .isEmpty();

        // The tombstone.
        assertThat(erased.isErased()).isTrue();
        assertThat(erased.getErasedAt()).isNotNull();
        assertThat(erased.getUserName()).isNotEqualTo(customer.username()).startsWith("deleted-");
        assertThat(erased.getEmail()).isNotEqualTo(customer.email()).endsWith("@anonymised.invalid");
        assertThat(erased.getPhone()).isNull();
        assertThat(erased.getTwoFactorSecret()).isNull();
        assertThat(erased.isMarketingOptIn()).isFalse();
        assertThat(passwordEncoder.matches(RAW_PASSWORD, erased.getPassword()))
                .as("the old password no longer opens the account").isFalse();
    }

    @Test
    @DisplayName("an erased account cannot sign in again")
    void erasedAccountCannotLogIn() {
        GdprFixture.Customer customer = newCustomer();

        LoginRequest login = new LoginRequest();
        login.setUsername(customer.username());
        login.setPassword(RAW_PASSWORD);
        assertThat(authService.login(login)).as("sanity: the credentials work before erasure").isNotNull();

        eraseFully(customer);

        assertThatThrownBy(() -> authService.login(login))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("the wrong password does not even start an erasure")
    void wrongPasswordIsRejectedBeforeAnythingHappens() {
        GdprFixture.Customer customer = newCustomer();
        User user = userRepository.findById(customer.userId()).orElseThrow();

        assertThatThrownBy(() -> gdprService.requestErasure(user, "not-the-password"))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(userRepository.findById(customer.userId()).orElseThrow().isErased()).isFalse();
        assertThat(cartRepository.findByUserUserIdOrderByCartIdAsc(customer.userId()))
                .as("nothing was touched").isNotEmpty();
    }

    @Test
    @DisplayName("the confirmation link works once, and erasure is idempotent")
    void confirmationTokenIsSingleUseAndErasureIsIdempotent() {
        GdprFixture.Customer customer = newCustomer();
        String token = requestErasureAndCaptureToken(customer);

        gdprService.confirmErasure(token);

        assertThatThrownBy(() -> gdprService.confirmErasure(token))
                .as("the spent link cannot be replayed")
                .isInstanceOf(RuntimeException.class);

        // The service itself must tolerate a repeat: the account is already gone.
        assertThat(userRepository.findById(customer.userId()).orElseThrow().isErased()).isTrue();
    }

    @Test
    @DisplayName("the audit trail records the erasure without naming the person")
    void auditTrailNamesNobody() {
        GdprFixture.Customer customer = newCustomer();
        eraseFully(customer);

        var entries = adminAuditLogRepository.findByActionOrderByCreatedAtDesc("GDPR_ERASURE");
        assertThat(entries).isNotEmpty();
        assertThat(entries).noneSatisfy(entry -> {
            assertThat(String.valueOf(entry.getAdminUsername()) + entry.getDetails())
                    .contains(customer.username());
        });
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private GdprFixture.Customer newCustomer() {
        return fixture.createFullyPopulatedCustomer(passwordEncoder.encode(RAW_PASSWORD), RAW_PASSWORD);
    }

    private void eraseFully(GdprFixture.Customer customer) {
        gdprService.confirmErasure(requestErasureAndCaptureToken(customer));
    }

    private String requestErasureAndCaptureToken(GdprFixture.Customer customer) {
        User user = userRepository.findById(customer.userId()).orElseThrow();
        gdprService.requestErasure(user, RAW_PASSWORD);

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendGdprErasureConfirmationEmail(
                anyString(), anyString(), link.capture(), anyLong());

        String url = link.getValue();
        return url.substring(url.indexOf("token=") + "token=".length());
    }
}
