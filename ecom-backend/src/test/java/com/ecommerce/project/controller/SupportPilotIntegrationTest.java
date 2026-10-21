package com.ecommerce.project.controller;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.ReturnRequest;
import com.ecommerce.project.repository.OrderRepository;
import com.ecommerce.project.repository.ReturnRequestRepository;
import com.ecommerce.project.repository.SupportConversationRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The shop <-> SupportPilot boundary, both directions, against a real Postgres.
 *
 * <p>SupportPilot itself is a stub HTTP server here: what is under test is the
 * shop's half of the contract — who it says the customer is, which keys it
 * sends, how it maps SupportPilot's statuses, and what the agent's refund call
 * turns into in the shop's own tables.
 */
@SpringBootTest(properties = {
        "app.support.enabled=true",
        "app.support.gateway-api-key=test-gateway-key",
        "app.support.admin-api-key=test-admin-key",
        "app.support.internal-api-key=test-internal-key"
})
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@DisplayName("SupportPilot integration")
class SupportPilotIntegrationTest {

    private static final String INTERNAL_KEY = "test-internal-key";
    // data.sql: user_id 2 = user1 / user1@example.com, user_id 3 = another customer
    private static final String USER1_EMAIL = "user1@example.com";

    /** Requests the stub SupportPilot received, as "METHOD path | header=value ...". */
    private static final List<String> RECEIVED = new CopyOnWriteArrayList<>();
    /** Path -> [status, body] the stub answers with. */
    private static final Map<String, Object[]> ANSWERS = new ConcurrentHashMap<>();
    private static final HttpServer SUPPORTPILOT = startStub();

    @DynamicPropertySource
    static void supportPilotUrl(DynamicPropertyRegistry registry) {
        registry.add("app.support.base-url", () -> "http://localhost:" + SUPPORTPILOT.getAddress().getPort());
    }

    @AfterAll
    static void stopStub() {
        SUPPORTPILOT.stop(0);
    }

    @Autowired private WebApplicationContext context;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ReturnRequestRepository returnRequestRepository;
    @Autowired private SupportConversationRepository supportConversationRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        RECEIVED.clear();
        ANSWERS.clear();
    }

    private Order order(String email, String status, Instant deliveredAt) {
        Order o = new Order();
        o.setEmail(email);
        o.setOrderStatus(status);
        o.setOrderDate(LocalDate.now().minusDays(10));
        o.setTotalAmount(new BigDecimal("89.90"));
        o.setDeliveredAt(deliveredAt);
        return orderRepository.save(o);
    }

    @Nested
    @DisplayName("SupportPilot -> shop (/internal/support)")
    class Internal {

        @Test
        @DisplayName("no key, or a customer's session, is not enough")
        void requiresTheServiceKey() throws Exception {
            Order o = order(USER1_EMAIL, "Delivered", Instant.now());
            mockMvc.perform(get("/internal/support/orders/" + o.getId())).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/internal/support/orders/" + o.getId()).header("X-Api-Key", "wrong"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("an order comes back in SupportPilot's shape, owner as u-<userId>")
        void orderInAgentShape() throws Exception {
            Order o = order(USER1_EMAIL, "Delivered", Instant.now().minus(2, ChronoUnit.DAYS));
            mockMvc.perform(get("/internal/support/orders/" + o.getId()).header("X-Api-Key", INTERNAL_KEY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(String.valueOf(o.getId())))
                    .andExpect(jsonPath("$.customer_id").value("u-2"))
                    .andExpect(jsonPath("$.status").value("delivered"))
                    .andExpect(jsonPath("$.currency").value("USD"))
                    .andExpect(jsonPath("$.delivered_at").isNotEmpty());
            mockMvc.perform(get("/internal/support/orders/abc").header("X-Api-Key", INTERNAL_KEY))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("an approved refund becomes an APPROVED return, once, however often it is retried")
        void refundIsAnApprovedReturnAndIdempotent() throws Exception {
            Order o = order(USER1_EMAIL, "Delivered", Instant.now().minus(3, ChronoUnit.DAYS));
            String key = "conv-1:" + o.getId();
            for (int attempt = 0; attempt < 2; attempt++) {
                mockMvc.perform(post("/internal/support/orders/" + o.getId() + "/refunds")
                                .header("X-Api-Key", INTERNAL_KEY)
                                .header("Idempotency-Key", key)
                                .header("X-Customer-Id", "u-2")
                                .header("X-Conversation-Id", "conv-1")
                                .header("X-Approved-By", "admin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"arrived broken\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.refund_id").value(org.hamcrest.Matchers.startsWith("RR-")));
            }
            ReturnRequest r = returnRequestRepository.findBySupportIdempotencyKey(key).orElseThrow();
            assertThat(r.getStatus()).isEqualTo("APPROVED");
            assertThat(r.getSource()).isEqualTo(ReturnRequest.SOURCE_SUPPORTPILOT);
            assertThat(r.getSupportConversationId()).isEqualTo("conv-1");
            assertThat(r.getAdminNote()).contains("admin");
            assertThat(returnRequestRepository.findAll().stream().filter(x -> o.getId().equals(x.getOrderId())))
                    .hasSize(1);
            assertThat(orderRepository.findById(o.getId()).orElseThrow().getOrderStatus()).isEqualTo("Returned");
        }

        @Test
        @DisplayName("someone else's order is 404; a business refusal is 409 with a reason")
        void ownershipAndRules() throws Exception {
            Order mine = order(USER1_EMAIL, "Shipped", null);
            Order old = order(USER1_EMAIL, "Delivered", Instant.now().minus(45, ChronoUnit.DAYS));
            mockMvc.perform(refund(mine.getId(), "u-3", "k-other")).andExpect(status().isNotFound());
            mockMvc.perform(refund(mine.getId(), "u-2", "k-shipped"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").isNotEmpty());
            mockMvc.perform(refund(old.getId(), "u-2", "k-old")).andExpect(status().isConflict());
            assertThat(returnRequestRepository.findBySupportIdempotencyKey("k-shipped")).isEmpty();
        }

        private org.springframework.test.web.servlet.RequestBuilder refund(Long orderId, String customer, String key) {
            return post("/internal/support/orders/" + orderId + "/refunds")
                    .header("X-Api-Key", INTERNAL_KEY)
                    .header("Idempotency-Key", key)
                    .header("X-Customer-Id", customer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"x\"}");
        }
    }

    @Nested
    @DisplayName("shop -> SupportPilot (/api/support, /api/admin/support)")
    class Gateway {

        @Test
        @WithMockUser(username = "user1", roles = "USER")
        @DisplayName("chat speaks for the logged-in user with the gateway key, and remembers the conversation")
        void chatIsForwardedAsTheLoggedInUser() throws Exception {
            ANSWERS.put("POST /v1/chat", new Object[]{200,
                    "{\"conversation_id\":\"c-abc\",\"status\":\"open\",\"reply\":\"Order 5 is shipped.\"}"});
            mockMvc.perform(post("/api/support/chat").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"Where is my order 5?\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.conversationId").value("c-abc"))
                    .andExpect(jsonPath("$.reply").value("Order 5 is shipped."));
            assertThat(RECEIVED).anySatisfy(r -> assertThat(r)
                    .startsWith("POST /v1/chat").contains("x-api-key=test-gateway-key").contains("x-customer-id=u-2"));
            assertThat(supportConversationRepository.findById("c-abc")).get()
                    .satisfies(c -> assertThat(c.getUserId()).isEqualTo(2L));
        }

        @Test
        @WithMockUser(username = "user1", roles = "USER")
        @DisplayName("a conversation the user does not own is 404 before SupportPilot is even asked")
        void foreignConversationIsNotForwarded() throws Exception {
            mockMvc.perform(get("/api/support/conversations/not-mine")).andExpect(status().isNotFound());
            assertThat(RECEIVED).isEmpty();
        }

        @Test
        @WithMockUser(username = "user1", roles = "USER")
        @DisplayName("SupportPilot's 429 keeps its Retry-After; a dead SupportPilot is 503, not 500")
        void upstreamErrorsAreTranslated() throws Exception {
            ANSWERS.put("POST /v1/chat", new Object[]{429, "{\"detail\":\"slow down\"}"});
            mockMvc.perform(post("/api/support/chat").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string("Retry-After", "42"));
            ANSWERS.put("POST /v1/chat", new Object[]{500, "{\"detail\":\"boom\"}"});
            mockMvc.perform(post("/api/support/chat").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                    .andExpect(status().isServiceUnavailable());
        }

        @Test
        @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
        @DisplayName("the admin queue shows the customer's email and the order's real total")
        void adminQueueIsEnriched() throws Exception {
            Order o = order(USER1_EMAIL, "Delivered", Instant.now());
            ANSWERS.put("GET /v1/admin/approvals", new Object[]{200, """
                    [{"conversation_id":"c-1","customer_id":"u-2","requested_at":"2026-10-21T10:00:00.123000Z",
                      "actions":[{"name":"request_refund","args":{"order_id":"%d","reason":"broken"}}]}]
                    """.formatted(o.getId())});
            mockMvc.perform(get("/api/admin/support/approvals"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].customerEmail").value(USER1_EMAIL))
                    .andExpect(jsonPath("$[0].orderTotal").value(89.90))
                    .andExpect(jsonPath("$[0].reason").value("broken"));
            assertThat(RECEIVED).anySatisfy(r -> assertThat(r).contains("x-admin-key=test-admin-key"));
        }

        @Test
        @WithMockUser(username = "user1", roles = "USER")
        @DisplayName("a customer cannot reach the admin queue")
        void customersCannotDecide() throws Exception {
            mockMvc.perform(get("/api/admin/support/approvals")).andExpect(status().isForbidden());
        }
    }

    // --- a stand-in SupportPilot ----------------------------------------------------

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                String route = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
                StringBuilder seen = new StringBuilder(route + " |");
                exchange.getRequestHeaders().forEach((name, values) ->
                        seen.append(' ').append(name.toLowerCase()).append('=').append(String.join(",", values)));
                RECEIVED.add(seen.toString());
                Object[] answer = ANSWERS.getOrDefault(route, new Object[]{404, "{\"detail\":\"Not Found\"}"});
                byte[] body = ((String) answer[1]).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                if ((int) answer[0] == 429) {
                    exchange.getResponseHeaders().add("Retry-After", "42");
                }
                exchange.sendResponseHeaders((int) answer[0], body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
