# SupportPilot + DocuAsk inside EcommerceHub — integration guide

Built and verified on 2026-10-21 against `e-commerce-app` `origin/main` @ `4dff0ca` and the SupportPilot repo
(`roadmaps/ai-engineer/supportpilot`, `FILIPIOAN10/support-pilot`) @ `54f8097`. DocuAsk needs **no code change**.

Every code block below is copied from code that compiled and passed its tests (see [§5](#5-what-was-verified)).
The changes are committed on branch `feat/supportpilot-integration` in `e-commerce-app` and in the SupportPilot repo.

---

## 1. What the three projects are today

| | DocuAsk | SupportPilot | e-commerce-app |
|---|---|---|---|
| Language / framework | Python 3.12, FastAPI, LangChain (LCEL) | Python 3.12, FastAPI, LangChain `create_agent` on LangGraph | Java 21, Spring Boot 4.1, React 19 + Vite + Tailwind/MUI |
| Data | Postgres 16 + pgvector: `documents`, `chunks` (vector(1536) + tsvector), Alembic | MongoDB: LangGraph checkpoints (the transcript), `conversations`, `approvals`; Redis rate limit | Postgres 16 + pgvector, Flyway V1–V36; Redis (cache, rate limit, refresh tokens) |
| API | `POST /v1/ask` → `{answer, refused, reason, citations}`, documents CRUD, `/v1/search` | `POST /v1/chat`, `GET /v1/conversations/{id}`, `GET/POST /v1/admin/approvals[/{id}]` | `/api/**` (JWT in HttpOnly cookie + CSRF), `/api/admin/**` = ROLE_ADMIN |
| Auth | `X-API-Key` (list, rotatable) | gateway key + `X-Customer-Id` (customer), admin key + `X-Admin-User` | JWT cookie, OAuth2, 2FA; Actuator has its own chain |
| Talks to | an LLM provider (fake / OpenAI / Azure) | **Orders API** (only a mock: `orders_mock.py`), DocuAsk, LLM | Stripe, SMTP, frankfurter.app |

What matters for the integration:

- **SupportPilot was designed for exactly this seam.** Its only knowledge of the shop is `OrdersClient`
  (`GET /orders/{id}`, `POST /orders/{id}/refunds` with `Idempotency-Key`), pointed at `orders_mock.py`. Point
  `ORDERS_BASE_URL` at a real implementation of that contract and the agent works on real orders.
- **The shop already has a human-approved money pipeline.** A `ReturnRequest` goes
  `REQUESTED → APPROVED → SHIPPED_BACK → REFUNDED`; "refunded" writes a `Refund` row and an outbox event, and
  `RefundHandler` calls Stripe. So SupportPilot must *not* move money itself: its `request_refund` becomes "file an
  approved return", and the existing pipeline pays when the parcel is back.
- `proiect-1-docuask-cod` / `proiect-2-supportpilot-cod` are the reference copies; your repos `docuask/` and
  `supportpilot/` hold the same code (only `ruff format` line wrapping differs) plus the UI page. **Integrate the repos.**

Mismatches found (each one is handled below):

| SupportPilot expects | The shop has | Resolution |
|---|---|---|
| customer id matching `^[A-Za-z0-9_-]{1,64}$` | orders keyed by **email** | customer id = `u-<userId>`; the shop maps it to the email. The email never reaches SupportPilot or the model. |
| `status: "delivered"` | `"Delivered"`, `"Return Requested"` | lower-case + `_` in the internal API |
| `delivered_at` for the 30-day window | only `orderDate` (a `LocalDate`) | **V37** adds `orders.delivered_at`, set on the move to Delivered |
| 30-day window (returns.md) | not enforced anywhere | enforced by the shop on the agent's path (`app.support.refund-window-days`) |
| "refund created" | return first, money after the parcel | agent files an APPROVED return, money moves later |
| shop-docs: PayPal, bank transfer, EUR, free over 50 EUR | Stripe + cash on delivery, USD, free over 100 USD (`ShippingCalculator`) | new `support/policies/*.md` written from the shop's code |

---

## 2. Architecture

**Recommendation: keep both Python services, make the Spring backend their gateway and their system of record.**

```
Browser (React)
  │  JWT cookie + CSRF, same origin
  ▼
Spring Boot  /api/support/**        (customer)   ── who is this? u-<userId>; owns this conversation?
             /api/admin/support/**  (ROLE_ADMIN) ── enrich the queue with email + real order total
  │  X-Api-Key=gateway, X-Customer-Id=u-42         X-Admin-Key, X-Admin-User=<admin username>
  ▼
SupportPilot :8000  (FastAPI, LangGraph agent, Mongo checkpoints, Redis rate limit)
  │  get_order / check_refund_eligibility / request_refund        │ search_policies
  │  X-Api-Key=internal, Idempotency-Key, X-Customer-Id, ...       ▼
  ▼                                                          DocuAsk :8000 (pgvector RAG over support/policies/*.md)
Spring Boot  /internal/support/**   (ROLE_SUPPORT_SERVICE, own filter chain, not proxied by nginx)
  │  ReturnService.requestReturn + approveReturn (all existing rules)
  ▼
return_requests (APPROVED, source=SUPPORTPILOT) → admin marks refunded → Refund + outbox → Stripe
```

Why this and not the alternatives:

- **Rewrite the agent in Spring AI inside the monolith** — throws away 58 + 61 tested Python tests, LangGraph's
  interrupt/resume (pauses survive restarts), and Spring AI is already switched off in the `local` profile because of
  Boot 4.1 incompatibilities. Two runtimes is the price; failure isolation (chat down ≠ shop down) is the gain.
- **Browser talks to SupportPilot directly** — the gateway key would sit in the browser and `X-Customer-Id` would be
  forgeable. The shop must be the one that says who the customer is.

**One human gate, not two.** The admin approves in the new *AI Refunds* screen; that resumes the paused LangGraph run,
the tool files the return **already APPROVED**, and the agent tells the customer in the same conversation. The return
then follows the normal path (customer ships it back, admin marks it refunded, outbox calls Stripe). Approving the AI
request never moves money.

*Simpler alternative you could choose instead:* remove `HumanInTheLoopMiddleware` and let the tool file a `REQUESTED`
return into the existing Returns page. Fewer moving parts, but the customer never gets the decision in the chat.

**Who stores what**

| Data | Where | Why |
|---|---|---|
| Transcript (messages, tool calls) | SupportPilot → Mongo (LangGraph checkpoint) | The paused run resumes from it; a copy in Postgres would be a second truth. |
| Pending AI refund approvals | SupportPilot → Mongo `approvals` | Atomic claim (two admins → one wins) already tested there. |
| Conversation ↔ user, status | **Shop → `support_conversations` (V37)** | Ownership check before proxying, resume on any device, GDPR erasure. |
| Approved returns | **Shop → `return_requests` (+ `source`, `support_conversation_id`, `support_idempotency_key`)** | Money lives with the shop. |
| Delivery time | **Shop → `orders.delivered_at`** | The 30-day window. |
| Policies | DocuAsk → its own pgvector DB | Unchanged project 1. |

---

## 3. Apply it

Already applied: branch `feat/supportpilot-integration` in **both** `e-commerce-app` (off `main`) and
`roadmaps/ai-engineer/supportpilot` holds everything below. DocuAsk has no branch because it has no change.

The steps below are the same changes in reading order. Paths are relative to `e-commerce-app/` unless the step says
SupportPilot.

---

## 4. Step by step

### Step 1 — Database: migration V37

New file `ecom-backend/src/main/resources/db/migration/V37__supportpilot_integration.sql`:

`ecom-backend/src/main/resources/db/migration/V37__supportpilot_integration.sql`

```sql
-- SupportPilot (the AI support agent) integration.

-- 1. When an order was delivered. The shop's return window (30 days) is counted
--    from delivery, and until now the order only kept the day it was placed.
--    Set once by OrderServiceImpl.updateOrder on the move to Delivered.
ALTER TABLE orders ADD COLUMN delivered_at TIMESTAMP(6);

-- Orders already past delivery get their order date: the earliest the delivery
-- can have happened, so their window can only come out shorter, never longer.
UPDATE orders
   SET delivered_at = order_date::timestamp
 WHERE delivered_at IS NULL
   AND order_date IS NOT NULL
   AND order_status IN ('Delivered', 'Return Requested', 'Returned', 'Refunded');

-- 2. Where a return came from. A return the agent filed carries its conversation
--    (so an admin can read why) and the agent's idempotency key, so a retried
--    tool call finds the return it already created instead of failing on
--    "a return already exists".
ALTER TABLE return_requests ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER';
ALTER TABLE return_requests ADD COLUMN support_conversation_id VARCHAR(64);
ALTER TABLE return_requests ADD COLUMN support_idempotency_key VARCHAR(100);
ALTER TABLE return_requests
    ADD CONSTRAINT ck_return_requests_source CHECK (source IN ('CUSTOMER', 'SUPPORTPILOT'));
CREATE UNIQUE INDEX uk_return_requests_support_key
    ON return_requests (support_idempotency_key)
    WHERE support_idempotency_key IS NOT NULL;

-- 3. Which support conversations belong to which account. The transcript itself
--    stays in SupportPilot: the LangGraph checkpoint IS the history, and it has
--    to live where the paused agent resumes from. This row is what lets the shop
--    check ownership before proxying, resume the latest conversation on another
--    device, and find everything to erase under GDPR.
CREATE TABLE support_conversations (
    conversation_id VARCHAR(64)  PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users (user_id),
    status          VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_support_conversations_status CHECK (status IN ('open', 'awaiting_approval'))
);
CREATE INDEX idx_support_conversations_user ON support_conversations (user_id, updated_at DESC);
```

`TIMESTAMP(6)` + `Instant` matches what V31 (`refunds`) already does, so `ddl-auto=validate` stays happy. The
`(user_id, updated_at)` index also covers the new foreign key, which `ForeignKeyIndexTest` checks.

### Step 2 — Entities, DTO, repository

`Order` gets `deliveredAt`, set once when an admin/seller moves the order to Delivered:

`ecom-backend/src/main/java/com/ecommerce/project/model/Order.java`

```diff
@@ -13,6 +13,7 @@ import lombok.EqualsAndHashCode;
 import lombok.ToString;
 
 import java.math.BigDecimal;
+import java.time.Instant;
 import java.time.LocalDate;
 import java.util.ArrayList;
 import java.util.List;
@@ -105,6 +106,12 @@ public class Order {
     private String appliedCoupons;
     private String orderStatus;
 
+    // When the order first reached Delivered: the return window starts here.
+    // Set once by OrderServiceImpl.updateOrder; a rejected return that puts the
+    // order back to Delivered does not restart the clock. See V37.
+    @Column(name = "delivered_at")
+    private Instant deliveredAt;
+
     @ManyToOne
     @JoinColumn(name = "address_id")
     private Address address;
```

`ecom-backend/src/main/java/com/ecommerce/project/service/impl/OrderServiceImpl.java`

```diff
@@ -36,6 +36,7 @@ import org.springframework.data.domain.Pageable;
 import org.springframework.stereotype.Service;
 
 import java.math.BigDecimal;
+import java.time.Instant;
 import java.time.LocalDate;
 import java.util.ArrayList;
 import java.util.Collections;
@@ -169,6 +170,9 @@ public class OrderServiceImpl implements OrderService {
             restockOrderItems(order, status);
         }
 
+        if (OrderStatus.DELIVERED.equals(status) && order.getDeliveredAt() == null) {
+            order.setDeliveredAt(Instant.now());
+        }
         order.setOrderStatus(status);
         orderRepository.save(order);
         OrderDTO orderDTO = orderDtoAssembler.forStatusUpdate(order);
```

`ReturnRequest` learns where it came from (existing returns get `CUSTOMER` from the column default):

`ecom-backend/src/main/java/com/ecommerce/project/model/ReturnRequest.java`

```diff
@@ -59,4 +59,19 @@ public class ReturnRequest {
     // NUMERIC(12,2), so this is too rather than a Double that widens it. See V30.
     @Column(precision = 12, scale = 2)
     private BigDecimal refundAmount;
+
+    /** CUSTOMER (the My Orders button) or SUPPORTPILOT (filed by the AI agent after an admin approved it). */
+    @Column(nullable = false, length = 20)
+    private String source = SOURCE_CUSTOMER;
+
+    /** The SupportPilot conversation that asked for this return, so an admin can read why. */
+    @Column(name = "support_conversation_id", length = 64)
+    private String supportConversationId;
+
+    /** The agent's Idempotency-Key: a retried tool call finds this row instead of creating a second. Unique (partial), see V37. */
+    @Column(name = "support_idempotency_key", length = 100)
+    private String supportIdempotencyKey;
+
+    public static final String SOURCE_CUSTOMER = "CUSTOMER";
+    public static final String SOURCE_SUPPORTPILOT = "SUPPORTPILOT";
 }
```

`ecom-backend/src/main/java/com/ecommerce/project/payload/ReturnRequestDTO.java`

```diff
@@ -25,4 +25,6 @@ public class ReturnRequestDTO {
     private String trackingStatus;
     private LocalDateTime lastTrackedAt;
     private BigDecimal refundAmount;
+    private String source;
+    private String supportConversationId;
 }
```

`ecom-backend/src/main/java/com/ecommerce/project/service/impl/ReturnServiceImpl.java`

```diff
@@ -291,6 +291,8 @@ public class ReturnServiceImpl implements ReturnService {
         dto.setTrackingStatus(r.getTrackingStatus());
         dto.setLastTrackedAt(r.getLastTrackedAt());
         dto.setRefundAmount(r.getRefundAmount() != null ? r.getRefundAmount() : orderTotal);
+        dto.setSource(r.getSource());
+        dto.setSupportConversationId(r.getSupportConversationId());
         return dto;
     }
```

`ecom-backend/src/main/java/com/ecommerce/project/repository/ReturnRequestRepository.java`

```diff
@@ -19,6 +19,8 @@ public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Lo
 
     boolean existsByOrderIdAndStatus(Long orderId, String status);
 
+    Optional<ReturnRequest> findBySupportIdempotencyKey(String supportIdempotencyKey);
+
     /** Every return this user has raised — read by the GDPR export. */
     java.util.List<ReturnRequest> findByUserEmailOrderByIdAsc(String email);
 }
```

New `model/SupportConversation.java` and `repository/SupportConversationRepository.java`:

`ecom-backend/src/main/java/com/ecommerce/project/model/SupportConversation.java`

```java
package com.ecommerce.project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * One SupportPilot conversation, as the shop knows it: whose it is and whether
 * it is paused for an admin. The messages themselves stay in SupportPilot (the
 * LangGraph checkpoint is the transcript), so this row holds no chat text.
 */
@Entity
@Table(name = "support_conversations")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class SupportConversation {

    @Id
    @Column(name = "conversation_id", length = 64)
    @EqualsAndHashCode.Include
    @ToString.Include
    private String conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** open | awaiting_approval — SupportPilot's own two states. */
    @Column(nullable = false, length = 20)
    @ToString.Include
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SupportConversation(String conversationId, Long userId, String status) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.status = status;
    }
}
```

`ecom-backend/src/main/java/com/ecommerce/project/repository/SupportConversationRepository.java`

```java
package com.ecommerce.project.repository;

import com.ecommerce.project.model.SupportConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupportConversationRepository extends JpaRepository<SupportConversation, String> {

    Optional<SupportConversation> findByConversationIdAndUserId(String conversationId, Long userId);

    Optional<SupportConversation> findFirstByUserIdOrderByUpdatedAtDesc(Long userId);

    /** GDPR erasure: the transcripts are deleted in SupportPilot; this drops the shop's index of them. */
    @Modifying
    @Query("DELETE FROM SupportConversation c WHERE c.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
```

### Step 3 — Configuration, environment variables

New `config/SupportPilotProperties.java`:

`ecom-backend/src/main/java/com/ecommerce/project/config/SupportPilotProperties.java`

```java
package com.ecommerce.project.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * The AI support agent, bound from {@code app.support.*}.
 *
 * <p>SupportPilot is a separate Python service. The shop talks to it with two
 * keys it holds ({@link #gatewayApiKey} for customer traffic, {@link #adminApiKey}
 * for approvals), and SupportPilot talks back to the shop's
 * {@code /internal/support/**} API with a third ({@link #internalApiKey}). Three
 * keys, so a leak of one role's key does not hand over the others.
 *
 * <p>{@link #readTimeout} is long on purpose: one chat turn is a loop of model
 * calls plus a DocuAsk lookup, and is measured in tens of seconds, not millis.
 */
@Getter
@Setter
@ConfigurationProperties("app.support")
public class SupportPilotProperties {

    private boolean enabled = false;
    private String baseUrl = "http://localhost:8001";
    private String gatewayApiKey = "";
    private String adminApiKey = "";
    private String internalApiKey = "";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(90);

    /** Days after delivery during which the agent may file a return (the policy in returns.md). */
    private int refundWindowDays = 30;
}
```

`ecom-backend/src/main/java/com/ecommerce/project/config/AppConfig.java`

```diff
@@ -11,7 +11,7 @@ import org.springframework.context.annotation.Bean;
 import org.springframework.context.annotation.Configuration;
 
 @Configuration
-@EnableConfigurationProperties({TaxProperties.class, CurrencyProperties.class})
+@EnableConfigurationProperties({TaxProperties.class, CurrencyProperties.class, SupportPilotProperties.class})
 public class AppConfig {
     @Bean
     public ModelMapper modelMapper() {
```

`ecom-backend/src/main/resources/application.properties`

```diff
@@ -310,4 +310,18 @@ management.metrics.tags.application=${spring.application.name}
 # --- GraphQL (admin dashboards, optional alongside REST) ---
 spring.graphql.path=/graphql
 spring.graphql.graphiql.enabled=false
-spring.graphql.websocket.enabled=false
\ No newline at end of file
+spring.graphql.websocket.enabled=false
+
+# --- AI customer support: SupportPilot (agent) + DocuAsk (policy RAG) --------------
+# Separate Python services. Off by default: the shop runs unchanged without them.
+# Three keys, three roles: gateway (customer traffic), admin (refund approvals),
+# internal (SupportPilot calling back into /internal/support/**).
+app.support.enabled=${SUPPORT_ENABLED:false}
+app.support.base-url=${SUPPORTPILOT_URL:http://localhost:8001}
+app.support.gateway-api-key=${SUPPORTPILOT_GATEWAY_KEY:}
+app.support.admin-api-key=${SUPPORTPILOT_ADMIN_KEY:}
+app.support.internal-api-key=${SUPPORT_INTERNAL_API_KEY:}
+app.support.connect-timeout=${SUPPORTPILOT_CONNECT_TIMEOUT:2s}
+# One turn = several model calls + a DocuAsk lookup. Keep above SupportPilot's own worst case.
+app.support.read-timeout=${SUPPORTPILOT_READ_TIMEOUT:90s}
+app.support.refund-window-days=${SUPPORT_REFUND_WINDOW_DAYS:30}
```

`.env.example`

```diff
@@ -103,3 +103,15 @@ SPRING_AI_MODEL_AUDIO_SPEECH=none
 SPRING_AI_MODEL_AUDIO_TRANSCRIPTION=none
 SPRING_AI_VECTORSTORE_PGVECTOR_INITIALIZE_SCHEMA=true
 SPRING_AI_VECTORSTORE_PGVECTOR_ID_TYPE=text
+
+# ── AI customer support (docker-compose.support.yml) ─────────────
+# Three different random values - generate each with: openssl rand -hex 32
+SUPPORTPILOT_GATEWAY_KEY=
+SUPPORTPILOT_ADMIN_KEY=
+SUPPORT_INTERNAL_API_KEY=
+DOCUASK_API_KEY=
+# fake = scripted offline model (no key needed). openai = real model, needs OPENAI_API_KEY above.
+SUPPORT_LLM_PROVIDER=fake
+# Where the two Python repos live, relative to this folder
+SUPPORTPILOT_DIR=../roadmaps/ai-engineer/supportpilot
+DOCUASK_DIR=../roadmaps/ai-engineer/docuask
```

No new Maven or npm dependency: `RestClient` is in `spring-web`, the widget uses `axios` and `react-icons` that are
already there.

### Step 4 — The shop as SupportPilot's order system (`/internal/support/**`)

A second security chain, ordered before the main one, like the Actuator chain. Service key in, `ROLE_SUPPORT_SERVICE`
out; no cookies so no CSRF; nginx only proxies `/api/`, so the path is not reachable from outside.

`ecom-backend/src/main/java/com/ecommerce/project/security/WebSecurityConfig.java`

```diff
@@ -3,6 +3,7 @@ package com.ecommerce.project.security;
 
 
 import com.ecommerce.project.config.OAuth2LoginSuccessHandler;
+import com.ecommerce.project.config.SupportPilotProperties;
 import com.ecommerce.project.ratelimit.RateLimitFilter;
 import com.ecommerce.project.security.jwt.AuthEntryPointJwt;
 import com.ecommerce.project.security.jwt.AuthTokenFilter;
@@ -31,6 +32,7 @@ import org.springframework.security.crypto.password.PasswordEncoder;
 import org.springframework.security.web.SecurityFilterChain;
 import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
 import com.ecommerce.project.security.filter.CsrfCookieFilter;
+import com.ecommerce.project.security.filter.ServiceApiKeyFilter;
 
 import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
 import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
@@ -144,6 +146,30 @@ public class WebSecurityConfig {
         return new ProviderManager(provider);
     }
 
+    /**
+     * SupportPilot's way back into the shop: {@code /internal/support/**}.
+     *
+     * <p>A service, not a person — no cookie, so no CSRF, and no session. It
+     * proves itself with {@code app.support.internal-api-key} and gets
+     * {@code ROLE_SUPPORT_SERVICE}, which no user account can hold, so a stolen
+     * customer JWT opens nothing here and the service key opens nothing else.
+     * nginx only proxies {@code /api/}, so this is reachable on the internal
+     * network alone.
+     */
+    @Bean
+    @Order(2)
+    public SecurityFilterChain supportInternalFilterChain(HttpSecurity http, SupportPilotProperties support)
+            throws Exception {
+        http.securityMatcher("/internal/support/**")
+                .csrf(AbstractHttpConfigurer::disable)
+                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
+                .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
+                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("SUPPORT_SERVICE"))
+                .addFilterBefore(new ServiceApiKeyFilter(support.getInternalApiKey(), "supportpilot", "SUPPORT_SERVICE"),
+                        UsernamePasswordAuthenticationFilter.class);
+        return http.build();
+    }
+
     @Bean
     public SecurityFilterChain filterChain(HttpSecurity http, AuthTokenFilter authTokenFilter,
                                            RateLimitFilter rateLimitFilter,
```

New `security/filter/ServiceApiKeyFilter.java`:

`ecom-backend/src/main/java/com/ecommerce/project/security/filter/ServiceApiKeyFilter.java`

```java
package com.ecommerce.project.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates a calling service by a shared key in {@code X-Api-Key}, as
 * {@code ROLE_<role>}. Constant-time comparison, so response timing cannot be
 * used to guess the key one character at a time.
 *
 * <p>Deliberately not a {@code @Component}: Spring Boot registers every Filter
 * bean as a servlet filter for ALL requests. This one belongs to one chain only.
 * A blank configured key authenticates nobody — an unconfigured deployment fails
 * closed.
 */
public class ServiceApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Api-Key";

    private final byte[] expected;
    private final String principal;
    private final String role;

    public ServiceApiKeyFilter(String expectedKey, String principal, String role) {
        this.expected = expectedKey == null ? new byte[0] : expectedKey.getBytes(StandardCharsets.UTF_8);
        this.principal = principal;
        this.role = role;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        if (expected.length > 0 && supplied != null
                && MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8), expected)) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
```

The wire contract, in one place — new `service/support/SupportPilotApi.java`:

`ecom-backend/src/main/java/com/ecommerce/project/service/support/SupportPilotApi.java`

```java
package com.ecommerce.project.service.support;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * The JSON both sides of the SupportPilot boundary speak, in SupportPilot's own
 * snake_case. Kept in one place so a contract change is one diff, and kept apart
 * from the shop's camelCase API in {@code payload.support}, which the browser sees.
 */
public final class SupportPilotApi {

    private SupportPilotApi() {
    }

    // --- shop -> SupportPilot (/v1/...) ---------------------------------------------

    public record ChatIn(String message, @JsonProperty("conversation_id") String conversationId) {
    }

    /** status is "open" or "awaiting_approval". */
    public record ChatOut(@JsonProperty("conversation_id") String conversationId, String status, String reply) {
    }

    /** role is "customer" or "assistant"; tool calls never leave SupportPilot. */
    public record MessageOut(String role, String content) {
    }

    public record ConversationOut(@JsonProperty("conversation_id") String conversationId,
                                  String status,
                                  List<MessageOut> messages) {
    }

    /** One gated tool call waiting for a person: {name: "request_refund", args: {order_id, reason}}. */
    public record ActionOut(String name, Map<String, Object> args) {
    }

    public record ApprovalOut(@JsonProperty("conversation_id") String conversationId,
                              @JsonProperty("customer_id") String customerId,
                              @JsonProperty("requested_at") OffsetDateTime requestedAt,
                              List<ActionOut> actions) {
    }

    public record DecisionIn(String decision, String note) {
    }

    // --- SupportPilot -> shop (/internal/support/...) -------------------------------

    /**
     * An order as SupportPilot's tools expect it (its {@code orders_mock.py} is the
     * reference). {@code customer_id} is what the tool compares with the customer
     * of the conversation; dates are ISO-8601 strings because Python parses them
     * with {@code datetime.fromisoformat}.
     */
    public record OrderView(String id,
                            @JsonProperty("customer_id") String customerId,
                            String status,
                            List<String> items,
                            BigDecimal total,
                            String currency,
                            String carrier,
                            String eta,
                            @JsonProperty("placed_at") String placedAt,
                            @JsonProperty("delivered_at") String deliveredAt) {
    }

    public record RefundIn(String reason) {
    }

    public record RefundOut(@JsonProperty("refund_id") String refundId, String status) {
    }
}
```

New `service/support/SupportCustomerIds.java`:

`ecom-backend/src/main/java/com/ecommerce/project/service/support/SupportCustomerIds.java`

```java
package com.ecommerce.project.service.support;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * How a shop user is named on the SupportPilot side: {@code u-<userId>}.
 *
 * <p>Not the email: SupportPilot only accepts {@code [A-Za-z0-9_-]{1,64}}, and an
 * opaque id means the customer's email never reaches the agent service or, from
 * there, the model provider.
 */
public final class SupportCustomerIds {

    private static final Pattern FORMAT = Pattern.compile("^u-(\\d{1,18})$");

    private SupportCustomerIds() {
    }

    public static String of(Long userId) {
        return "u-" + userId;
    }

    public static Optional<Long> userIdOf(String customerId) {
        if (customerId == null) {
            return Optional.empty();
        }
        var matcher = FORMAT.matcher(customerId);
        return matcher.matches() ? Optional.of(Long.parseLong(matcher.group(1))) : Optional.empty();
    }
}
```

The heart of it — new `service/support/SupportOrderService.java`. `request_refund` → `requestReturn` +
`approveReturn` in one transaction, idempotent on the agent's key, with the shop re-checking ownership and the window:

`ecom-backend/src/main/java/com/ecommerce/project/service/support/SupportOrderService.java`

```java
package com.ecommerce.project.service.support;

import com.ecommerce.project.config.SupportPilotProperties;
import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.OrderItem;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.ReturnRequest;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.ReturnRequestDTO;
import com.ecommerce.project.repository.OrderRepository;
import com.ecommerce.project.repository.ReturnRequestRepository;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.service.ReturnService;
import com.ecommerce.project.service.support.SupportPilotApi.OrderView;
import com.ecommerce.project.service.support.SupportPilotApi.RefundOut;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * What SupportPilot's tools see of the shop: one order, and one way to act on it.
 *
 * <p>SupportPilot's {@code request_refund} tool runs only after an admin approved
 * it in the AI queue. Here it becomes an ordinary return, created and approved
 * through {@link ReturnService} so every existing rule (own order, Delivered,
 * no open return) still applies. Money is not touched: it moves later, when an
 * admin marks the returned parcel refunded and the outbox calls Stripe.
 *
 * <p>The shop re-checks everything the agent already checked. SupportPilot is a
 * client; the rules that guard money live with the money.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportOrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnService returnService;
    private final SupportPilotProperties properties;

    @Transactional(readOnly = true)
    public Optional<OrderView> findOrder(String rawOrderId) {
        return parseOrderId(rawOrderId)
                .flatMap(orderRepository::findByIdWithDetails)
                .map(this::toView);
    }

    /**
     * Files and approves a return for an order the agent's customer owns.
     * Idempotent on {@code idempotencyKey}: the same key returns the same return.
     *
     * @throws ResourceNotFoundException the order does not exist or is not this customer's (same answer for both)
     * @throws APIException              the order cannot be returned (status, window, an open return)
     */
    @Transactional
    public RefundOut createApprovedReturn(String rawOrderId, String customerId, String reason,
                                          String idempotencyKey, String conversationId, String approvedBy) {
        Optional<ReturnRequest> replay = returnRequestRepository.findBySupportIdempotencyKey(idempotencyKey);
        if (replay.isPresent()) {
            return refundOut(replay.get());
        }

        Order order = parseOrderId(rawOrderId)
                .flatMap(orderRepository::findById)
                .filter(o -> ownedBy(o, customerId))
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", rawOrderId));

        assertInsideWindow(order);

        // requestReturn re-checks ownership, Delivered and "no open return".
        ReturnRequestDTO filed = returnService.requestReturn(order.getId(), order.getEmail(), truncate(reason, 500));

        ReturnRequest created = returnRequestRepository.findById(filed.getId()).orElseThrow();
        created.setSource(ReturnRequest.SOURCE_SUPPORTPILOT);
        created.setSupportConversationId(conversationId);
        created.setSupportIdempotencyKey(idempotencyKey);
        returnRequestRepository.save(created);

        returnService.approveReturn(created.getId(),
                truncate("Approved by " + approvedBy + " in the AI support queue (conversation " + conversationId + ")", 255));

        log.info("support return created return={} order={} conversation={} approvedBy={}",
                created.getId(), order.getId(), conversationId, approvedBy);
        return refundOut(created);
    }

    private void assertInsideWindow(Order order) {
        Instant deliveredAt = order.getDeliveredAt();
        Duration window = Duration.ofDays(properties.getRefundWindowDays());
        if (deliveredAt == null || Instant.now().isAfter(deliveredAt.plus(window))) {
            throw new APIException("The order was delivered more than " + properties.getRefundWindowDays()
                    + " days ago, or has not been delivered");
        }
    }

    private boolean ownedBy(Order order, String customerId) {
        return SupportCustomerIds.userIdOf(customerId)
                .flatMap(userRepository::findById)
                .map(User::getEmail)
                .filter(email -> email.equals(order.getEmail()))
                .isPresent();
    }

    private OrderView toView(Order order) {
        // Orders are keyed by email; a guest order with no account maps to no
        // customer, and SupportPilot's ownership check then treats it as not found.
        String customerId = userRepository.findByEmail(order.getEmail())
                .map(u -> SupportCustomerIds.of(u.getUserId()))
                .orElse(null);
        List<String> items = order.getOrderItems().stream()
                .map(OrderItem::getProduct)
                .filter(Objects::nonNull)
                .map(Product::getProductName)
                .toList();
        // Stored in USD; shown in the currency the customer paid in, at the rate they were quoted.
        BigDecimal total = order.getTotalAmount() == null ? null
                : order.getTotalAmount().multiply(order.getExchangeRate()).setScale(2, RoundingMode.HALF_UP);
        return new OrderView(
                String.valueOf(order.getId()),
                customerId,
                agentStatus(order.getOrderStatus()),
                items,
                total,
                order.getCurrencyCode(),
                null, // outbound shipments have no carrier/ETA in this shop yet
                null,
                order.getOrderDate() == null ? null : order.getOrderDate().toString(),
                order.getDeliveredAt() == null ? null : order.getDeliveredAt().atOffset(ZoneOffset.UTC).toString());
    }

    /** "Return Requested" -> "return_requested": SupportPilot's tools compare with lower-case "delivered". */
    static String agentStatus(String shopStatus) {
        if (shopStatus == null) {
            return "unknown";
        }
        return shopStatus.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static RefundOut refundOut(ReturnRequest r) {
        // Not "refunded": the money moves after the parcel is back. The status word is what the agent repeats.
        return new RefundOut("RR-" + r.getId(), "return_approved_refund_after_parcel_arrives");
    }

    private static Optional<Long> parseOrderId(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String digits = raw.trim().replaceFirst("^#", "");
        if (!digits.matches("\\d{1,18}")) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(digits));
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return "No reason provided";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
```

New `controller/SupportInternalController.java` (404 = not yours / not found, 409 = refused with a reason):

`ecom-backend/src/main/java/com/ecommerce/project/controller/SupportInternalController.java`

```java
package com.ecommerce.project.controller;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.service.support.SupportOrderService;
import com.ecommerce.project.service.support.SupportPilotApi.OrderView;
import com.ecommerce.project.service.support.SupportPilotApi.RefundIn;
import com.ecommerce.project.service.support.SupportPilotApi.RefundOut;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The shop as SupportPilot's "order system": the exact contract of its
 * {@code orders_mock.py}, so SupportPilot only changes {@code ORDERS_BASE_URL}.
 *
 * <p>Service-to-service only: guarded by the {@code /internal/support/**} filter
 * chain (an API key, no cookies, no CSRF) and never routed by nginx, which
 * proxies {@code /api/} alone. Hidden from the public OpenAPI document.
 *
 * <p>Errors are SupportPilot-shaped ({@code {"detail": ...}}): 404 is "no such
 * order for this customer", 409 is a business refusal the agent should explain
 * — never retried by its client, and never tripping its circuit breaker.
 */
@Hidden
@RestController
@RequestMapping("/internal/support")
@RequiredArgsConstructor
public class SupportInternalController {

    private final SupportOrderService supportOrderService;

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderView> order(@PathVariable String orderId) {
        return supportOrderService.findOrder(orderId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping("/orders/{orderId}/refunds")
    public ResponseEntity<RefundOut> refund(@PathVariable String orderId,
                                            @RequestBody RefundIn body,
                                            @RequestHeader("Idempotency-Key") String idempotencyKey,
                                            @RequestHeader("X-Customer-Id") String customerId,
                                            @RequestHeader(value = "X-Conversation-Id", required = false) String conversationId,
                                            @RequestHeader(value = "X-Approved-By", required = false) String approvedBy) {
        RefundOut out = supportOrderService.createApprovedReturn(orderId, customerId,
                body == null ? null : body.reason(), idempotencyKey, conversationId,
                approvedBy == null || approvedBy.isBlank() ? "support agent" : approvedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "no such order"));
    }

    @ExceptionHandler(APIException.class)
    public ResponseEntity<Map<String, String>> refused(APIException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("detail", e.getMessage()));
    }
}
```

### Step 5 — The shop as the browser's gateway (`/api/support/**`, `/api/admin/support/**`)

New `service/support/SupportPilotClient.java` — the only class that knows SupportPilot's URLs and keys; forwards the
request id; turns every failure into a status + a sentence for the user:

`ecom-backend/src/main/java/com/ecommerce/project/service/support/SupportPilotClient.java`

```java
package com.ecommerce.project.service.support;

import com.ecommerce.project.config.SupportPilotProperties;
import com.ecommerce.project.exception.SupportPilotException;
import com.ecommerce.project.security.filter.RequestCorrelationFilter;
import com.ecommerce.project.service.support.SupportPilotApi.ApprovalOut;
import com.ecommerce.project.service.support.SupportPilotApi.ChatIn;
import com.ecommerce.project.service.support.SupportPilotApi.ChatOut;
import com.ecommerce.project.service.support.SupportPilotApi.ConversationOut;
import com.ecommerce.project.service.support.SupportPilotApi.DecisionIn;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

/**
 * HTTP client for SupportPilot (the Python agent service). The only class that
 * knows its URLs, headers and keys.
 *
 * <p>The shop is SupportPilot's trusted gateway: it authenticates the customer
 * from the JWT cookie, then speaks for them with {@code X-Customer-Id}. That
 * header is believed only because the gateway key comes with it, so the gateway
 * key must never reach a browser.
 *
 * <p>Every failure comes out as a {@link SupportPilotException} carrying the
 * status and the sentence the caller should see.
 */
@Slf4j
@Component
public class SupportPilotClient {

    private static final String UNAVAILABLE = "The support assistant is unavailable right now. Please try again in a few minutes.";

    private final SupportPilotProperties properties;
    private final RestClient restClient;

    public SupportPilotClient(SupportPilotProperties properties) {
        this.properties = properties;
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(properties.getConnectTimeout(), properties.getReadTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                // One request id across both services' logs.
                .requestInterceptor((request, body, execution) -> {
                    String requestId = MDC.get(RequestCorrelationFilter.MDC_KEY);
                    if (requestId != null) {
                        request.getHeaders().set("X-Request-ID", requestId);
                    }
                    return execution.execute(request, body);
                })
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw translate(response);
                })
                .build();
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    // --- customer side (gateway key + X-Customer-Id) -------------------------------

    public ChatOut chat(String customerId, String message, String conversationId) {
        return call(() -> restClient.post()
                .uri("/v1/chat")
                .headers(h -> customerHeaders(h, customerId))
                .body(new ChatIn(message, conversationId))
                .retrieve()
                .body(ChatOut.class));
    }

    public ConversationOut conversation(String customerId, String conversationId) {
        return call(() -> restClient.get()
                .uri("/v1/conversations/{id}", conversationId)
                .headers(h -> customerHeaders(h, customerId))
                .retrieve()
                .body(ConversationOut.class));
    }

    // --- admin side (admin key + X-Admin-User) --------------------------------------

    public List<ApprovalOut> pendingApprovals() {
        return call(() -> restClient.get()
                .uri("/v1/admin/approvals")
                .headers(h -> h.set("X-Admin-Key", properties.getAdminApiKey()))
                .retrieve()
                .body(new ParameterizedTypeReference<List<ApprovalOut>>() { }));
    }

    public ChatOut decide(String conversationId, String decision, String note, String adminUser) {
        return call(() -> restClient.post()
                .uri("/v1/admin/approvals/{id}", conversationId)
                .headers(h -> {
                    h.set("X-Admin-Key", properties.getAdminApiKey());
                    h.set("X-Admin-User", adminUser);
                })
                .body(new DecisionIn(decision, note))
                .retrieve()
                .body(ChatOut.class));
    }

    /** GDPR Art. 17: every conversation, approval and checkpoint of this customer. Idempotent. */
    public void eraseCustomer(String customerId) {
        call(() -> restClient.delete()
                .uri("/v1/admin/customers/{id}", customerId)
                .headers(h -> h.set("X-Admin-Key", properties.getAdminApiKey()))
                .retrieve()
                .toBodilessEntity());
    }

    // --- plumbing -------------------------------------------------------------------

    private void customerHeaders(HttpHeaders headers, String customerId) {
        headers.set("X-Api-Key", properties.getGatewayApiKey());
        headers.set("X-Customer-Id", customerId);
    }

    private <T> T call(Supplier<T> request) {
        if (!properties.isEnabled()) {
            throw new SupportPilotException(HttpStatus.SERVICE_UNAVAILABLE, "The support assistant is not enabled.");
        }
        try {
            return request.get();
        } catch (ResourceAccessException e) { // connect refused, read timeout
            log.warn("SupportPilot unreachable: {}", e.getMessage());
            throw new SupportPilotException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
        }
    }

    private SupportPilotException translate(ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        return switch (status) {
            case 404 -> new SupportPilotException(HttpStatus.NOT_FOUND, "No such conversation, or nothing is waiting for a decision there.");
            case 409 -> new SupportPilotException(HttpStatus.CONFLICT,
                    "A colleague is reviewing your refund request. The answer will appear in this conversation.");
            case 422 -> new SupportPilotException(HttpStatus.BAD_REQUEST, "The message could not be accepted.");
            case 429 -> new SupportPilotException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many messages, please slow down.", retryAfter(response));
            default -> {
                // 401 here means OUR key is wrong: a deployment problem, not the user's.
                log.error("SupportPilot answered {}", status);
                yield new SupportPilotException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
            }
        };
    }

    private Long retryAfter(ClientHttpResponse response) {
        String value = response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        try {
            return value == null ? null : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```

New `exception/SupportPilotException.java` and `exception/SupportPilotExceptionHandler.java` (ordered first, because the
global handler's catch-all `Exception` method would otherwise win and answer 500):

`ecom-backend/src/main/java/com/ecommerce/project/exception/SupportPilotException.java`

```java
package com.ecommerce.project.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * A SupportPilot call that did not produce an answer, already translated into
 * what the shop's caller should see. The message is written for the customer or
 * admin; SupportPilot's own error text is logged, never passed through.
 */
@Getter
public class SupportPilotException extends RuntimeException {

    private final HttpStatus status;
    /** Seconds until a rate-limited customer may write again; null otherwise. */
    private final Long retryAfterSeconds;

    public SupportPilotException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public SupportPilotException(HttpStatus status, String message, Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
```

`ecom-backend/src/main/java/com/ecommerce/project/exception/SupportPilotExceptionHandler.java`

```java
package com.ecommerce.project.exception;

import com.ecommerce.project.controller.AdminSupportController;
import com.ecommerce.project.controller.SupportController;
import com.ecommerce.project.payload.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns {@link SupportPilotException} into its status. Ordered first because
 * {@link MyGlobalExceptionHandler} has a catch-all {@code Exception} handler,
 * and Spring picks the first advice that has <em>any</em> matching method — the
 * catch-all would win and answer 500.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {SupportController.class, AdminSupportController.class})
public class SupportPilotExceptionHandler {

    @ExceptionHandler(SupportPilotException.class)
    public ResponseEntity<ApiResponse> handle(SupportPilotException e) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(e.getStatus());
        if (e.getRetryAfterSeconds() != null) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()));
        }
        return response.body(new ApiResponse(e.getMessage(), false));
    }
}
```

New `payload/support/SupportDtos.java` (the browser's camelCase API):

`ecom-backend/src/main/java/com/ecommerce/project/payload/support/SupportDtos.java`

```java
package com.ecommerce.project.payload.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** The shop's own support API, as the React app sees it (camelCase, no SupportPilot internals). */
public final class SupportDtos {

    private SupportDtos() {
    }

    public record SupportStatus(boolean enabled) {
    }

    public record ChatRequest(
            @NotBlank @Size(max = 2000) String message,
            @Size(max = 64) String conversationId) {
    }

    /** status: open | awaiting_approval. While awaiting_approval the widget polls the conversation. */
    public record ChatResponse(String conversationId, String status, String reply) {
    }

    public record Message(String role, String content) {
    }

    public record Conversation(String conversationId, String status, List<Message> messages) {
    }

    /**
     * One refund the agent wants to file, enriched with what the admin needs to
     * decide: who, which order, how much. {@code orderTotal} is in {@code currency}.
     */
    public record PendingRefund(
            String conversationId,
            OffsetDateTime requestedAt,
            Long userId,
            String customerEmail,
            String customerName,
            String orderId,
            String orderStatus,
            BigDecimal orderTotal,
            String currency,
            String reason) {
    }

    public record Decision(
            @NotNull @Pattern(regexp = "approve|reject") String decision,
            @Size(max = 500) String note) {
    }
}
```

New `service/support/SupportChatService.java` and `service/support/AdminSupportService.java`:

`ecom-backend/src/main/java/com/ecommerce/project/service/support/SupportChatService.java`

```java
package com.ecommerce.project.service.support;

import com.ecommerce.project.exception.SupportPilotException;
import com.ecommerce.project.model.SupportConversation;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.support.SupportDtos.ChatRequest;
import com.ecommerce.project.payload.support.SupportDtos.ChatResponse;
import com.ecommerce.project.payload.support.SupportDtos.Conversation;
import com.ecommerce.project.payload.support.SupportDtos.Message;
import com.ecommerce.project.repository.SupportConversationRepository;
import com.ecommerce.project.service.support.SupportPilotApi.ChatOut;
import com.ecommerce.project.service.support.SupportPilotApi.ConversationOut;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * The customer's side of the support chat. The shop is the gateway: it decides
 * who the customer is, checks the conversation is theirs, then forwards.
 *
 * <p>No {@code @Transactional} around the SupportPilot call on purpose: one chat
 * turn takes seconds, and a pooled DB connection must not wait on a model.
 */
@Service
@RequiredArgsConstructor
public class SupportChatService {

    private final SupportPilotClient client;
    private final SupportConversationRepository conversations;

    public boolean isEnabled() {
        return client.isEnabled();
    }

    public ChatResponse chat(User user, ChatRequest request) {
        String conversationId = blankToNull(request.conversationId());
        if (conversationId != null) {
            requireOwn(user, conversationId);
        }
        ChatOut out = client.chat(SupportCustomerIds.of(user.getUserId()), request.message().trim(), conversationId);
        remember(user.getUserId(), out.conversationId(), out.status());
        return new ChatResponse(out.conversationId(), out.status(), out.reply());
    }

    public Conversation conversation(User user, String conversationId) {
        requireOwn(user, conversationId);
        ConversationOut out = client.conversation(SupportCustomerIds.of(user.getUserId()), conversationId);
        // The status may have changed while the customer was away (an admin decided).
        remember(user.getUserId(), out.conversationId(), out.status());
        return toConversation(out);
    }

    /** The most recent conversation, so the widget resumes it on any device. */
    public Optional<Conversation> latest(User user) {
        return conversations.findFirstByUserIdOrderByUpdatedAtDesc(user.getUserId())
                .map(c -> conversation(user, c.getConversationId()));
    }

    static Conversation toConversation(ConversationOut out) {
        return new Conversation(out.conversationId(), out.status(),
                out.messages().stream().map(m -> new Message(m.role(), m.content())).toList());
    }

    private void requireOwn(User user, String conversationId) {
        // Someone else's conversation answers exactly like one that does not exist.
        conversations.findByConversationIdAndUserId(conversationId, user.getUserId())
                .orElseThrow(() -> new SupportPilotException(HttpStatus.NOT_FOUND, "No such conversation."));
    }

    private void remember(Long userId, String conversationId, String status) {
        SupportConversation row = conversations.findById(conversationId)
                .orElseGet(() -> new SupportConversation(conversationId, userId, status));
        row.setStatus(status);
        row.setUpdatedAt(Instant.now()); // dirty even when the status is unchanged, so "latest" means last message
        conversations.save(row);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
```

`ecom-backend/src/main/java/com/ecommerce/project/service/support/AdminSupportService.java`

```java
package com.ecommerce.project.service.support;

import com.ecommerce.project.exception.SupportPilotException;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.support.SupportDtos.Conversation;
import com.ecommerce.project.payload.support.SupportDtos.PendingRefund;
import com.ecommerce.project.repository.SupportConversationRepository;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.service.support.SupportPilotApi.ActionOut;
import com.ecommerce.project.service.support.SupportPilotApi.ApprovalOut;
import com.ecommerce.project.service.support.SupportPilotApi.ChatOut;
import com.ecommerce.project.service.support.SupportPilotApi.OrderView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The admin's side: the queue of refunds the agent paused on, and the decision
 * that resumes it. SupportPilot owns the queue (a paused LangGraph run cannot
 * live anywhere else); this class adds the shop's view of each row — the
 * customer's email and the order's real total — so the admin decides on facts.
 */
@Service
@RequiredArgsConstructor
public class AdminSupportService {

    private static final String REFUND_TOOL = "request_refund";

    private final SupportPilotClient client;
    private final SupportOrderService supportOrderService;
    private final UserRepository userRepository;
    private final SupportConversationRepository conversations;

    /** Bounded by how many refunds are waiting at once — a handful — so one lookup per row is fine. */
    public List<PendingRefund> pendingRefunds() {
        return client.pendingApprovals().stream()
                .map(this::toPendingRefund)
                .toList();
    }

    /**
     * Approve or reject. On approve SupportPilot resumes the paused run, its
     * {@code request_refund} tool calls {@code /internal/support/orders/{id}/refunds},
     * and the return appears in Admin → Returns already APPROVED.
     */
    public ChatOut decide(String conversationId, String decision, String note, String adminUsername) {
        ChatOut out = client.decide(conversationId, decision, note, adminUsername);
        conversations.findById(conversationId).ifPresent(c -> {
            c.setStatus(out.status());
            conversations.save(c);
        });
        return out;
    }

    public Conversation transcript(String conversationId) {
        Long userId = conversations.findById(conversationId)
                .orElseThrow(() -> new SupportPilotException(HttpStatus.NOT_FOUND, "No such conversation."))
                .getUserId();
        return SupportChatService.toConversation(
                client.conversation(SupportCustomerIds.of(userId), conversationId));
    }

    private PendingRefund toPendingRefund(ApprovalOut approval) {
        Optional<User> user = SupportCustomerIds.userIdOf(approval.customerId()).flatMap(userRepository::findById);
        Map<String, Object> args = approval.actions().stream()
                .filter(a -> REFUND_TOOL.equals(a.name()))
                .findFirst()
                .map(ActionOut::args)
                .orElse(Map.of());
        String orderId = String.valueOf(args.getOrDefault("order_id", ""));
        Optional<OrderView> order = supportOrderService.findOrder(orderId);
        return new PendingRefund(
                approval.conversationId(),
                approval.requestedAt(),
                user.map(User::getUserId).orElse(null),
                user.map(User::getEmail).orElse(null),
                user.map(User::getUserName).orElse(null),
                orderId,
                order.map(OrderView::status).orElse("not found"),
                order.map(OrderView::total).orElse(null),
                order.map(OrderView::currency).orElse(null),
                String.valueOf(args.getOrDefault("reason", "")));
    }
}
```

New `controller/SupportController.java` and `controller/AdminSupportController.java`:

`ecom-backend/src/main/java/com/ecommerce/project/controller/SupportController.java`

```java
package com.ecommerce.project.controller;

import com.ecommerce.project.payload.support.SupportDtos.ChatRequest;
import com.ecommerce.project.payload.support.SupportDtos.ChatResponse;
import com.ecommerce.project.payload.support.SupportDtos.Conversation;
import com.ecommerce.project.payload.support.SupportDtos.SupportStatus;
import com.ecommerce.project.service.support.SupportChatService;
import com.ecommerce.project.util.AuthUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The customer chat widget's API. The customer is always the logged-in user, never a parameter. */
@Tag(name = "Support")
@RestController
@RequestMapping("/api/support")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class SupportController extends BaseController {

    private final SupportChatService supportChatService;
    private final AuthUtil authUtil;

    @GetMapping("/status")
    public ResponseEntity<SupportStatus> status() {
        return ok(new SupportStatus(supportChatService.isEnabled()));
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ok(supportChatService.chat(authUtil.loggedInUser(), request));
    }

    @GetMapping("/conversations/latest")
    public ResponseEntity<Conversation> latest() {
        return supportChatService.latest(authUtil.loggedInUser())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<Conversation> conversation(@PathVariable String conversationId) {
        return ok(supportChatService.conversation(authUtil.loggedInUser(), conversationId));
    }
}
```

`ecom-backend/src/main/java/com/ecommerce/project/controller/AdminSupportController.java`

```java
package com.ecommerce.project.controller;

import com.ecommerce.project.payload.support.SupportDtos.ChatResponse;
import com.ecommerce.project.payload.support.SupportDtos.Conversation;
import com.ecommerce.project.payload.support.SupportDtos.Decision;
import com.ecommerce.project.payload.support.SupportDtos.PendingRefund;
import com.ecommerce.project.service.support.AdminSupportService;
import com.ecommerce.project.service.support.SupportPilotApi.ChatOut;
import com.ecommerce.project.util.AuthUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The AI refund queue: refunds the agent paused on, waiting for a person. */
@Tag(name = "Support (admin)")
@RestController
@RequestMapping("/api/admin/support")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSupportController extends BaseController {

    private final AdminSupportService adminSupportService;
    private final AuthUtil authUtil;

    @GetMapping("/approvals")
    public ResponseEntity<List<PendingRefund>> pending() {
        return ok(adminSupportService.pendingRefunds());
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<Conversation> transcript(@PathVariable String conversationId) {
        return ok(adminSupportService.transcript(conversationId));
    }

    @PostMapping("/approvals/{conversationId}")
    public ResponseEntity<ChatResponse> decide(@PathVariable String conversationId,
                                               @Valid @RequestBody Decision decision) {
        // The admin's username goes to SupportPilot's audit trail and on to the return's admin note.
        ChatOut out = adminSupportService.decide(conversationId, decision.decision(), decision.note(),
                authUtil.loggedInUser().getUserName());
        return ok(new ChatResponse(out.conversationId(), out.status(), out.reply()));
    }
}
```

Every chat turn is paid model calls, so it gets a rate-limit rule at the door too:

`ecom-backend/src/main/java/com/ecommerce/project/ratelimit/RateLimitConfig.java`

```diff
@@ -55,7 +55,11 @@ public class RateLimitConfig {
                 // filling one address form legitimately fires a handful of these as
                 // the customer changes their mind about the state, and the response
                 // is cached for a day after that.
-                new RateLimitRule("geo-cities", "GET", "/api/public/geo/cities", 60, oneMinute, RateLimitKeyType.IP)
+                new RateLimitRule("geo-cities", "GET", "/api/public/geo/cities", 60, oneMinute, RateLimitKeyType.IP),
+
+                // Every message is one or more paid model calls. SupportPilot has its
+                // own per-customer limit too; this one stops the traffic at the door.
+                new RateLimitRule("support-chat", "POST", "/api/support/chat", 20, oneMinute, RateLimitKeyType.USER)
         );
     }
 }
```

### Step 6 — GDPR erasure reaches the chat transcripts

The transcripts are personal data in another service. `GdprErasureService.erase` is one DB transaction, so the HTTP
call goes on the outbox (runs only after the erasure commits, retried until SupportPilot answers).

`ecom-backend/src/main/java/com/ecommerce/project/service/outbox/OutboxEventTypes.java`

```diff
@@ -35,4 +35,7 @@ public final class OutboxEventTypes {
 
     /** A chargeback reached a terminal state (won / lost / closed) — alert the admins of the outcome. */
     public static final String DISPUTE_CLOSED = "DISPUTE_CLOSED";
+
+    /** GDPR erasure reached the AI support agent: delete the customer's conversations in SupportPilot. */
+    public static final String SUPPORT_CHAT_ERASURE = "SUPPORT_CHAT_ERASURE";
 }
```

`ecom-backend/src/main/java/com/ecommerce/project/service/outbox/payload/SupportChatErasureOutboxPayload.java`

```java
package com.ecommerce.project.service.outbox.payload;

/**
 * Outbox payload for erasing a customer's SupportPilot conversations.
 *
 * @param customerId the SupportPilot-side id, {@code u-<userId>}
 */
public record SupportChatErasureOutboxPayload(String customerId) {
}
```

`ecom-backend/src/main/java/com/ecommerce/project/service/outbox/handler/SupportChatErasureHandler.java`

```java
package com.ecommerce.project.service.outbox.handler;

import com.ecommerce.project.service.outbox.OutboxEventTypes;
import com.ecommerce.project.service.outbox.OutboxHandler;
import com.ecommerce.project.service.outbox.OutboxPayloadCodec;
import com.ecommerce.project.service.outbox.payload.SupportChatErasureOutboxPayload;
import com.ecommerce.project.service.support.SupportPilotClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Deletes a customer's conversations, approvals and agent checkpoints in
 * SupportPilot after a GDPR erasure.
 *
 * <p>On the outbox, not inside {@code GdprErasureService.erase}: that method is
 * one database transaction, and an HTTP call inside it would hold a pooled
 * connection on a remote service — and could not be undone if the transaction
 * then rolled back. Here the erasure commits first and this runs after, retried
 * with backoff until SupportPilot answers. Idempotent: SupportPilot's DELETE
 * answers 204 whether or not anything was left to delete.
 *
 * <p>Fails (and so retries, then dead-letters) while the assistant is disabled,
 * on purpose: personal data that may still sit in SupportPilot must not be
 * silently marked as erased.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportChatErasureHandler implements OutboxHandler {

    private final SupportPilotClient supportPilotClient;
    private final OutboxPayloadCodec payloadCodec;

    @Override
    public String eventType() {
        return OutboxEventTypes.SUPPORT_CHAT_ERASURE;
    }

    @Override
    public void handle(String payload) {
        SupportChatErasureOutboxPayload data = payloadCodec.deserialize(payload, SupportChatErasureOutboxPayload.class);
        supportPilotClient.eraseCustomer(data.customerId());
        log.info("support conversations erased for {}", data.customerId());
    }
}
```

`ecom-backend/src/main/java/com/ecommerce/project/service/gdpr/GdprErasureService.java`

```diff
@@ -1,5 +1,10 @@
 package com.ecommerce.project.service.gdpr;
 
+import com.ecommerce.project.repository.SupportConversationRepository;
+import com.ecommerce.project.service.outbox.OutboxEventPublisher;
+import com.ecommerce.project.service.outbox.OutboxEventTypes;
+import com.ecommerce.project.service.outbox.payload.SupportChatErasureOutboxPayload;
+import com.ecommerce.project.service.support.SupportCustomerIds;
 import com.ecommerce.project.exception.ResourceNotFoundException;
 import com.ecommerce.project.model.User;
 import com.ecommerce.project.repository.ProductRepository;
@@ -66,6 +71,8 @@ public class GdprErasureService {
     private final GdprTokenService gdprTokenService;
     private final StringRedisTemplate redisTemplate;
     private final AdminAuditLogService adminAuditLogService;
+    private final SupportConversationRepository supportConversationRepository;
+    private final OutboxEventPublisher outboxEventPublisher;
 
     public GdprErasureService(EntityManager entityManager,
                               UserRepository userRepository,
@@ -74,7 +81,9 @@ public class GdprErasureService {
                               RefreshTokenService refreshTokenService,
                               GdprTokenService gdprTokenService,
                               StringRedisTemplate redisTemplate,
-                              AdminAuditLogService adminAuditLogService) {
+                              AdminAuditLogService adminAuditLogService,
+                              SupportConversationRepository supportConversationRepository,
+                              OutboxEventPublisher outboxEventPublisher) {
         this.entityManager = entityManager;
         this.userRepository = userRepository;
         this.productRepository = productRepository;
@@ -83,6 +92,8 @@ public class GdprErasureService {
         this.gdprTokenService = gdprTokenService;
         this.redisTemplate = redisTemplate;
         this.adminAuditLogService = adminAuditLogService;
+        this.supportConversationRepository = supportConversationRepository;
+        this.outboxEventPublisher = outboxEventPublisher;
     }
 
     /**
@@ -109,6 +120,7 @@ public class GdprErasureService {
 
         revokeAccess(userId, originalUsername);
         deletePersonalContent(user, originalEmail, originalUsername);
+        eraseSupportConversations(userId);
         anonymiseRetainedRecords(originalEmail, originalUsername, pseudonym);
         tombstoneAccount(user, pseudonym);
 
@@ -124,6 +136,17 @@ public class GdprErasureService {
         log.info("GDPR erasure completed for user {} (pseudonym {})", userId, pseudonym);
     }
 
+    /**
+     * The chat transcripts live in SupportPilot; the call to delete them goes on
+     * the outbox so it runs only if this erasure commits. The shop's index of the
+     * conversations goes now, in this transaction.
+     */
+    private void eraseSupportConversations(Long userId) {
+        supportConversationRepository.deleteByUserId(userId);
+        outboxEventPublisher.publish(OutboxEventTypes.SUPPORT_CHAT_ERASURE,
+                new SupportChatErasureOutboxPayload(SupportCustomerIds.of(userId)));
+    }
+
     /** Kills every live session and any pending erasure link for the account. */
     private void revokeAccess(Long userId, String username) {
         // Including the session that asked: an erased account keeps no doors open.
```

### Step 7 — SupportPilot (Python) changes

In `roadmaps/ai-engineer/supportpilot`. Five small changes, all backwards compatible with the mock:

1. A **409 is a business answer** (like 404): never retried, never trips the breaker, and its reason reaches the agent.
2. `request_refund` sends **who / which conversation / which admin** with the refund, so the shop can re-check
   ownership and record the approver.
3. `decide()` puts the approving admin in the run config.
4. `DELETE /v1/admin/customers/{id}` for GDPR (checkpoints + conversations + approvals).
5. Tests for each.

`src/supportpilot/resilience.py`

```diff
@@ -30,6 +30,11 @@ class NotFound(Exception):
     """The dependency answered 404: a business answer, not a failure (never retried, never trips the breaker)."""
 
 
+class Conflict(Exception):
+    """The dependency answered 409: it understood and said no ("this order cannot be returned"). A business answer
+    like 404 - never retried, never trips the breaker. The message is the dependency's reason, safe to show."""
+
+
 class Transient(Exception):
     """A response that is worth retrying: 5xx and 429."""
 
@@ -116,8 +121,8 @@ class ResilientHttp:
             raise ServiceUnavailable(f"{self.name} is temporarily unavailable")
         try:
             result = self._with_retries(method, path, **kwargs)
-        except NotFound:
-            self.breaker.record_success()  # the service is fine, it just has no such thing
+        except (NotFound, Conflict):
+            self.breaker.record_success()  # the service is fine, it just said "no such thing" / "no"
             raise
         except (Transient, httpx.TransportError) as e:
             self.breaker.record_failure()
@@ -143,6 +148,8 @@ class ResilientHttp:
                 response = self._client.request(method, path, **kwargs)
                 if response.status_code == 404:
                     raise NotFound(path)
+                if response.status_code == 409:
+                    raise Conflict(_detail(response))
                 if response.status_code >= 500 or response.status_code == 429:
                     raise Transient(f"{response.status_code}")
                 response.raise_for_status()  # other 4xx: our bug (bad key, bad request) - not retryable
@@ -150,3 +157,12 @@ class ResilientHttp:
 
     def close(self) -> None:
         self._client.close()
+
+
+def _detail(response: httpx.Response) -> str:
+    """FastAPI and the shop's internal API both answer {"detail": "..."}; anything else gets a generic reason."""
+    try:
+        detail = response.json().get("detail")
+    except ValueError:
+        detail = None
+    return str(detail)[:300] if detail else "the request was refused"
```

`src/supportpilot/clients.py`

```diff
@@ -19,13 +19,26 @@ class OrdersClient:
         except NotFound:
             return None
 
-    def create_refund(self, order_id: str, reason: str, idempotency_key: str) -> dict[str, Any]:
-        """Safe to retry (and to call twice): the same key returns the same refund instead of creating a second one."""
+    def create_refund(
+        self,
+        order_id: str,
+        reason: str,
+        idempotency_key: str,
+        *,
+        customer_id: str | None = None,
+        conversation_id: str | None = None,
+        approved_by: str | None = None,
+    ) -> dict[str, Any]:
+        """Safe to retry (and to call twice): the same key returns the same refund instead of creating a second one.
+
+        The context headers let the order system re-check ownership itself and record who approved the refund.
+        Raises Conflict when the order system refuses (not delivered, outside the window, a return already open)."""
+        context = {"X-Customer-Id": customer_id, "X-Conversation-Id": conversation_id, "X-Approved-By": approved_by}
         return self._http.request(
             "POST",
             f"/orders/{order_id}/refunds",
             json={"reason": reason},
-            headers={"Idempotency-Key": idempotency_key},
+            headers={"Idempotency-Key": idempotency_key} | {k: v for k, v in context.items() if v},
         )
```

`src/supportpilot/tools.py`

```diff
@@ -13,7 +13,7 @@ from langchain_core.runnables import RunnableConfig
 from langchain_core.tools import BaseTool, tool
 
 from supportpilot.clients import DocuAskClient, OrdersClient
-from supportpilot.resilience import ServiceUnavailable
+from supportpilot.resilience import Conflict, ServiceUnavailable
 
 REFUND_WINDOW_DAYS = 30
 UNAVAILABLE = "The system is unavailable right now. Tell the customer to try again in a few minutes."
@@ -98,8 +98,18 @@ def build_tools(orders: OrdersClient, docuask: DocuAskClient, now=lambda: dateti
             if order is None or not _eligibility(order, now())[0]:
                 return f"Order {order_id} cannot be refunded."  # re-checked: the state may have changed while waiting
             # one refund per order per conversation, even if the approval is submitted twice
-            key = f"{config['configurable']['thread_id']}:{order['id']}"
-            refund = orders.create_refund(order["id"], reason[:500], key)
+            thread_id = config["configurable"]["thread_id"]
+            key = f"{thread_id}:{order['id']}"
+            refund = orders.create_refund(
+                order["id"],
+                reason[:500],
+                key,
+                customer_id=_customer(config),
+                conversation_id=thread_id,
+                approved_by=config["configurable"].get("approved_by"),
+            )
+        except Conflict as e:  # the order system has the final word on money
+            return f"Order {order_id} cannot be refunded: {e}."
         except ServiceUnavailable:
             return UNAVAILABLE
         return json.dumps({"refund_id": refund["refund_id"], "status": refund["status"]})
```

`src/supportpilot/service.py`

```diff
@@ -98,13 +98,11 @@ class SupportService:
                     }
                 ]
             }
+        config = self._config(conversation_id, customer_id)
+        config["configurable"]["approved_by"] = admin  # read by request_refund, recorded by the order system
         try:
             with get_usage_metadata_callback() as usage:
-                result = self._agent.invoke(
-                    Command(resume=resume),
-                    self._config(conversation_id, customer_id),
-                    version="v2",
-                )
+                result = self._agent.invoke(Command(resume=resume), config, version="v2")
         except Exception:
             self._store.release_approval(conversation_id)  # so the decision can be retried instead of being stuck
             raise
@@ -139,3 +137,13 @@ class SupportService:
             elif isinstance(m, AIMessage) and text:  # tool calls and tool results are internal
                 visible.append({"role": "assistant", "content": text})
         return conversation["status"], visible
+
+    def erase_customer(self, customer_id: str) -> int:
+        """GDPR Art. 17: every conversation of this customer - the agent's checkpoints (the transcript itself),
+        the conversation records and the approvals. Idempotent: erasing an erased customer erases nothing."""
+        conversation_ids = self._store.conversation_ids(customer_id)
+        for conversation_id in conversation_ids:
+            self._agent.checkpointer.delete_thread(conversation_id)
+        self._store.delete_customer(customer_id)
+        log.info("erased customer=%s conversations=%d", customer_id, len(conversation_ids))
+        return len(conversation_ids)
```

`src/supportpilot/store.py`

```diff
@@ -97,3 +97,11 @@ class Store:
                 }
             },
         )
+
+    # --- erasure --------------------------------------------------------------------------------------------------
+    def conversation_ids(self, customer_id: str) -> list[str]:
+        return [c["_id"] for c in self.conversations.find({"customer_id": customer_id}, {"_id": 1})]
+
+    def delete_customer(self, customer_id: str) -> None:
+        self.approvals.delete_many({"customer_id": customer_id})
+        self.conversations.delete_many({"customer_id": customer_id})
```

`src/supportpilot/api.py`

```diff
@@ -171,6 +171,18 @@ def decide(conversation_id: str, body: DecisionIn, admin: Admin, service: Servic
     )
 
 
+@router.delete(
+    "/admin/customers/{customer_id}",
+    status_code=status.HTTP_204_NO_CONTENT,
+    tags=["admin"],
+)
+def erase_customer(customer_id: str, _: Admin, service: Service) -> None:
+    """GDPR erasure, called by the shop after it erased the account. 204 whether or not anything was left."""
+    if not CUSTOMER_ID.match(customer_id):
+        raise HTTPException(status.HTTP_400_BAD_REQUEST, "Invalid customer id")
+    service.erase_customer(customer_id)
+
+
 @health.get("/live")
 def live() -> dict[str, str]:
     return {"status": "up"}
```

Tests:

`tests/conftest.py`

```diff
@@ -13,7 +13,7 @@ from pymongo import MongoClient
 from supportpilot.agent import build_agent
 from supportpilot.config import Settings
 from supportpilot.fake import ScriptedSupportModel
-from supportpilot.resilience import ServiceUnavailable
+from supportpilot.resilience import Conflict, ServiceUnavailable
 from supportpilot.service import SupportService
 from supportpilot.store import Store
 from supportpilot.tools import build_tools
@@ -92,6 +92,8 @@ class FakeOrders:
             "1004": make_order("1004", "c-200"),
         }
         self.refunds = {}
+        self.contexts = []  # the customer / conversation / approver sent with each refund call
+        self.refuse = None  # set to a reason to make the order system answer 409
         self.down = False
 
     def get_order(self, order_id):
@@ -99,9 +101,12 @@ class FakeOrders:
             raise ServiceUnavailable("orders is unavailable")
         return self.orders.get(order_id)
 
-    def create_refund(self, order_id, reason, idempotency_key):
+    def create_refund(self, order_id, reason, idempotency_key, **context):
         if self.down:
             raise ServiceUnavailable("orders is unavailable")
+        if self.refuse:
+            raise Conflict(self.refuse)
+        self.contexts.append(context)
         if idempotency_key not in self.refunds:
             self.refunds[idempotency_key] = {"refund_id": f"RF-{len(self.refunds) + 1:04d}", "status": "pending"}
         return self.refunds[idempotency_key]
```

`tests/test_resilience.py`

```diff
@@ -3,6 +3,7 @@ import pytest
 
 from supportpilot.resilience import (
     CircuitBreaker,
+    Conflict,
     NotFound,
     ResilientHttp,
     ServiceUnavailable,
@@ -122,3 +123,17 @@ def test_when_the_breaker_is_open_the_service_is_not_called_at_all():
     with pytest.raises(ServiceUnavailable, match="temporarily"):
         client.request("GET", "/x")
     assert len(calls) == 2  # failed fast: no third call
+
+
+def test_409_is_a_refusal_with_its_reason_never_retried_and_never_trips_the_breaker():
+    calls = []
+
+    def handler(request):
+        calls.append(1)
+        return httpx.Response(409, json={"detail": "Returns can only be requested for delivered orders"})
+
+    client = http(handler, retries=3)
+    for _ in range(10):
+        with pytest.raises(Conflict, match="delivered orders"):
+            client.request("POST", "/x")
+    assert len(calls) == 10 and client.breaker.state == "closed"
```

`tests/test_tools.py`

```diff
@@ -84,3 +84,17 @@ def test_when_a_service_is_down_the_model_gets_a_clear_message_not_an_exception(
         ("search_policies", {"question": "returns?"}),
     ]:
         assert "unavailable" in run(tools, name, args)
+
+
+def test_request_refund_tells_the_order_system_who_and_which_conversation(tools, orders):
+    config = {"configurable": {"customer_id": "c-100", "thread_id": "conv-9", "approved_by": "maria"}}
+    tool = next(t for t in tools if t.name == "request_refund")
+    tool.invoke({"order_id": "1001", "reason": "too small"}, config=config)
+    assert orders.contexts == [{"customer_id": "c-100", "conversation_id": "conv-9", "approved_by": "maria"}]
+
+
+def test_when_the_order_system_refuses_the_customer_hears_why_not_unavailable(tools, orders):
+    orders.refuse = "A return request for this order has already been approved"
+    out = run(tools, "request_refund", {"order_id": "1001", "reason": "x"})
+    assert out.startswith("Order 1001 cannot be refunded:") and "already been approved" in out
+    assert orders.refunds == {}
```

`tests/test_service.py`

```diff
@@ -104,3 +104,24 @@ def test_a_failed_resume_puts_the_approval_back_so_it_can_be_retried(make_servic
     assert store.approvals.find_one()["status"] == "pending"  # not stuck in "deciding"
     good = service.decide(out.conversation_id, "approve", None, "maria")
     assert "RF-" in good.reply
+
+
+def test_the_approving_admin_reaches_the_order_system(make_service, orders):
+    service, _ = make_service()
+    out = service.chat("c-100", "I want a refund for order 1001")
+    service.decide(out.conversation_id, "approve", None, "maria")
+    assert orders.contexts == [{"customer_id": "c-100", "conversation_id": out.conversation_id, "approved_by": "maria"}]
+
+
+def test_erasing_a_customer_removes_transcripts_records_and_approvals_and_nobody_elses(make_service, mongo):
+    service, store = make_service()
+    mine = service.chat("c-100", "I want a refund for order 1001")  # paused: an approval exists too
+    theirs = service.chat("c-200", "Where is my order 1004?")
+
+    assert service.erase_customer("c-100") == 1
+    assert store.get_conversation(mine.conversation_id) is None
+    assert store.pending_approvals() == []
+    assert mongo["checkpoints"].count_documents({"thread_id": mine.conversation_id}) == 0
+    assert mongo["checkpoints"].count_documents({"thread_id": theirs.conversation_id}) > 0
+    assert service.messages(theirs.conversation_id, "c-200")[1]
+    assert service.erase_customer("c-100") == 0  # idempotent
```

`tests/test_api.py`

```diff
@@ -106,3 +106,12 @@ def test_the_home_page_needs_no_key_and_is_safe_by_construction(client):
     assert "SupportPilot" in response.text
     assert "default-src 'none'" in response.headers["content-security-policy"]
     assert "innerHTML" not in response.text  # replies are only ever rendered with textContent
+
+
+def test_the_shop_can_erase_a_customer_with_the_admin_key_only(client):
+    cid = client.post("/v1/chat", json={"message": "Where is my order 1002?"}).json()["conversation_id"]
+    assert client.delete("/v1/admin/customers/c-100").status_code == 401  # the gateway key is not enough
+    assert client.delete("/v1/admin/customers/..%2Fx", headers=ADMIN).status_code in (400, 404)
+    assert client.delete("/v1/admin/customers/c-100", headers=ADMIN).status_code == 204
+    assert client.get(f"/v1/conversations/{cid}").status_code == 404
+    assert client.delete("/v1/admin/customers/c-100", headers=ADMIN).status_code == 204  # idempotent
```

Nothing else changes in SupportPilot: `ORDERS_BASE_URL=http://backend:8080/internal/support` and
`ORDERS_API_KEY=<SUPPORT_INTERNAL_API_KEY>` are pure configuration.

### Step 8 — DocuAsk: this shop's policies

No code change. New folder `support/policies/` with four documents written from the shop's code
(`ShippingCalculator`, `OrderStatus`, `ReturnServiceImpl`, Stripe + COD), replacing SupportPilot's generic shop-docs.
The `docuask-ingest` one-shot service in Step 10 loads them (re-running skips unchanged files by sha256).

`support/policies/returns.md`

```markdown
# Returns Policy

## Return window

Items can be returned within 30 days of delivery. The window is counted from the day the order was marked delivered, not from the order date. Only delivered orders can be returned, and an order can have one open return at a time.

## How to start a return

Start a return from My Orders, or ask the support assistant in the chat. A return the assistant prepares is checked and approved by a colleague before it is filed; the assistant tells you the result in the same conversation.

## Sending the item back

Once a return is approved, send the item back unused and in its original packaging, and enter the carrier and tracking number on the return. We follow the parcel and process the refund when it reaches our warehouse.

## How refunds are paid

Card payments are refunded automatically to the original card once the returned parcel arrives; the bank usually shows the money within 5 to 10 working days. Orders paid cash on delivery are refunded by our support team, who contact you for the bank details. Shipping costs are refunded only if the item was defective or the wrong item was sent.
```

`support/policies/shipping.md`

```markdown
# Shipping Policy

## Shipping costs

Shipping is free for orders of 100 USD or more, counted after discounts. Below that, delivery within Romania costs 3 USD and delivery to any other country costs 5 USD. Prices are stored in USD; if you shop in another currency, the amount is converted at the rate shown at checkout.

## Order statuses

An order goes from Placed to Packed, Shipped and Delivered. It can be cancelled until it is delivered. After delivery a return can be requested; the order then shows Return Requested, Returned and finally Refunded.

## Tracking

The status of every order is shown in My Orders. Guests can follow an order on the Track Order page with the order number.

## Lost parcels

If an order shows Shipped for more than 15 working days, contact support and we will open an investigation with the carrier, then send a replacement or refund the order in full.
```

`support/policies/payment.md`

```markdown
# Payment

## Accepted methods

We accept credit and debit cards through Stripe, and cash on delivery. Card payments are taken at checkout; cash on delivery is paid to the courier.

## Card security

Card payments are processed by Stripe and may ask for 3-D Secure authentication. We never see or store full card numbers.

## Failed payments

If a card payment fails, no money is taken. A pending authorisation disappears from the bank statement within 7 days.

## Currencies and VAT

Prices can be shown in several currencies for convenience; orders are settled in USD. VAT is added at the rate of the delivery country and is shown on the order summary and the invoice.

## Invoices

An invoice PDF is attached to the order confirmation email. Invoices cannot be changed after they are issued.
```

`support/policies/warranty.md`

```markdown
# Warranty

## Legal guarantee

All products sold to consumers in the EU have a legal guarantee of 2 years from delivery. During this period a defective product is repaired or replaced free of charge. If repair and replacement are impossible, the customer can ask for a refund.

## What is not covered

Damage caused by misuse, accidents, unauthorised repairs and normal wear is not covered. Batteries and other consumables are covered for 6 months.

## How to claim

Contact support (the chat or the Contact page) with the order number and a photo of the defect. We reply within 2 working days with a prepaid return label.
```

### Step 9 — Frontend

New `ecom-frontend/src/components/support/SupportChatWidget.jsx` — floating chat for signed-in customers (hidden on
admin pages and when the backend says support is disabled), resumes the latest conversation, locks the input and polls
while a refund waits for an admin:

`ecom-frontend/src/components/support/SupportChatWidget.jsx`

```jsx
import { useEffect, useRef, useState } from "react";
import { useSelector } from "react-redux";
import { useLocation } from "react-router-dom";
import toast from "react-hot-toast";
import { FaCommentDots, FaPaperPlane, FaPlus, FaTimes } from "react-icons/fa";
import api from "../../api/api";

const POLL_MS = 15000;
const GREETING = { role: "assistant", content: "Hi! I can check an order, explain our policies, or start a refund request." };

// The floating support chat. Talks only to the shop's /api/support/* — the shop
// decides who the customer is and forwards to SupportPilot; no key ever reaches
// the browser. While a refund waits for an admin the conversation is paused, so
// the input is disabled and the widget polls until the decision lands.
const SupportChatWidget = () => {
    const { user } = useSelector((state) => state.auth);
    const location = useLocation();
    const [enabled, setEnabled] = useState(false);
    const [open, setOpen] = useState(false);
    const [loaded, setLoaded] = useState(false);
    const [conversationId, setConversationId] = useState(null);
    const [status, setStatus] = useState("open");
    const [messages, setMessages] = useState([GREETING]);
    const [draft, setDraft] = useState("");
    const [sending, setSending] = useState(false);
    const listRef = useRef(null);

    const onAdminPage = /^\/[a-z]{2}\/admin/.test(location.pathname);

    useEffect(() => {
        if (!user) return; // rendering is gated on user below, so a stale flag never shows
        api.get("/support/status")
            .then(({ data }) => setEnabled(Boolean(data.enabled)))
            .catch(() => setEnabled(false));
    }, [user]);

    const showConversation = (data) => {
        setConversationId(data.conversationId);
        setStatus(data.status);
        setMessages(data.messages.length ? data.messages : [GREETING]);
    };

    // Resume the latest conversation (from any device) the first time the panel opens.
    useEffect(() => {
        if (!open || loaded) return;
        api.get("/support/conversations/latest")
            .then(({ status: httpStatus, data }) => {
                if (httpStatus === 200 && data) showConversation(data);
            })
            .catch(() => {})
            .finally(() => setLoaded(true));
    }, [open, loaded]);

    // Paused for an admin: poll until the decision arrives in the conversation.
    useEffect(() => {
        if (!open || status !== "awaiting_approval" || !conversationId) return;
        const timer = setInterval(() => {
            api.get(`/support/conversations/${conversationId}`)
                .then(({ data }) => showConversation(data))
                .catch(() => {});
        }, POLL_MS);
        return () => clearInterval(timer);
    }, [open, status, conversationId]);

    useEffect(() => {
        listRef.current?.scrollTo({ top: listRef.current.scrollHeight });
    }, [messages, open]);

    if (!user || !enabled || onAdminPage) return null;

    const send = async (e) => {
        e.preventDefault();
        const text = draft.trim();
        if (!text || sending) return;
        setSending(true);
        setDraft("");
        setMessages((prev) => [...prev, { role: "customer", content: text }]);
        try {
            const { data } = await api.post("/support/chat", { message: text, conversationId });
            setConversationId(data.conversationId);
            setStatus(data.status);
            setMessages((prev) => [...prev, { role: "assistant", content: data.reply }]);
        } catch (error) {
            setMessages((prev) => prev.slice(0, -1));
            setDraft(text);
            toast.error(error?.response?.data?.message || "The assistant could not answer. Please try again.");
        } finally {
            setSending(false);
        }
    };

    const startOver = () => {
        setConversationId(null);
        setStatus("open");
        setMessages([GREETING]);
    };

    const paused = status === "awaiting_approval";

    return (
        <div className="fixed bottom-5 right-5 z-50 flex flex-col items-end gap-3">
            {open && (
                <section
                    aria-label="Support chat"
                    className="w-[calc(100vw-2.5rem)] max-w-sm h-[28rem] flex flex-col bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded-xl shadow-xl"
                >
                    <header className="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700">
                        <h2 className="font-semibold text-gray-800 dark:text-white">Support assistant</h2>
                        <div className="flex items-center gap-1">
                            <button type="button" onClick={startOver} title="New conversation" aria-label="New conversation"
                                    className="p-2 rounded text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800">
                                <FaPlus />
                            </button>
                            <button type="button" onClick={() => setOpen(false)} title="Close" aria-label="Close support chat"
                                    className="p-2 rounded text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800">
                                <FaTimes />
                            </button>
                        </div>
                    </header>

                    <ol ref={listRef} aria-live="polite" className="flex-1 overflow-y-auto px-4 py-3 space-y-2">
                        {messages.map((m, i) => (
                            <li key={i} className={m.role === "customer" ? "flex justify-end" : "flex justify-start"}>
                                <span className={`max-w-[85%] whitespace-pre-wrap rounded-lg px-3 py-2 text-sm ${
                                    m.role === "customer"
                                        ? "bg-blue-600 text-white"
                                        : "bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-100"
                                }`}>
                                    {m.content}
                                </span>
                            </li>
                        ))}
                        {sending && <li className="text-xs text-gray-400">The assistant is typing…</li>}
                    </ol>

                    {paused && (
                        <p className="px-4 py-2 text-xs text-amber-700 bg-amber-50 dark:bg-amber-900/30 dark:text-amber-300">
                            A colleague is reviewing your refund request. The answer will appear here.
                        </p>
                    )}

                    <form onSubmit={send} className="flex items-center gap-2 p-3 border-t border-gray-200 dark:border-gray-700">
                        <label htmlFor="support-message" className="sr-only">Message</label>
                        <input
                            id="support-message"
                            value={draft}
                            onChange={(e) => setDraft(e.target.value)}
                            maxLength={2000}
                            disabled={sending || paused}
                            placeholder={paused ? "Waiting for a colleague…" : "Ask about an order or a policy"}
                            className="flex-1 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-800 dark:text-gray-100 disabled:opacity-60"
                        />
                        <button type="submit" disabled={sending || paused || !draft.trim()} aria-label="Send"
                                className="p-2 rounded-lg bg-blue-600 text-white disabled:opacity-50">
                            <FaPaperPlane />
                        </button>
                    </form>
                </section>
            )}

            <button
                type="button"
                onClick={() => setOpen((v) => !v)}
                aria-expanded={open}
                aria-label={open ? "Close support chat" : "Open support chat"}
                className="h-14 w-14 rounded-full bg-blue-600 text-white shadow-lg flex items-center justify-center text-xl hover:bg-blue-700"
            >
                {open ? <FaTimes /> : <FaCommentDots />}
            </button>
        </div>
    );
};

export default SupportChatWidget;
```

New `ecom-frontend/src/components/admin/support/AdminSupportApprovals.jsx` — the AI refund queue with transcript,
note, approve / reject:

`ecom-frontend/src/components/admin/support/AdminSupportApprovals.jsx`

```jsx
import { Fragment, useEffect, useState } from "react";
import api from "../../../api/api";
import toast from "react-hot-toast";
import { FaCheck, FaComments, FaSync, FaTimes } from "react-icons/fa";

const REFRESH_MS = 30000;

// Refunds the AI assistant paused on. Approving resumes the agent: it files the
// return in the shop (it then shows in Returns as APPROVED, badge "AI") and tells
// the customer in their conversation. No money moves here — the refund is paid
// from Returns once the parcel is back, exactly like a customer-filed return.
const AdminSupportApprovals = () => {
    const [rows, setRows] = useState([]);
    const [loading, setLoading] = useState(true);
    const [notes, setNotes] = useState({});
    const [busy, setBusy] = useState(null);
    const [openTranscript, setOpenTranscript] = useState(null);
    const [transcript, setTranscript] = useState([]);

    const fetchPending = () =>
        api.get("/admin/support/approvals")
            .then(({ data }) => setRows(data))
            .catch((error) => toast.error(error?.response?.data?.message || "Failed to load the AI refund queue"))
            .finally(() => setLoading(false));

    useEffect(() => {
        fetchPending();
        const timer = setInterval(fetchPending, REFRESH_MS);
        return () => clearInterval(timer);
    }, []);

    const toggleTranscript = async (conversationId) => {
        if (openTranscript === conversationId) {
            setOpenTranscript(null);
            return;
        }
        try {
            const { data } = await api.get(`/admin/support/conversations/${conversationId}`);
            setTranscript(data.messages);
            setOpenTranscript(conversationId);
        } catch (error) {
            toast.error(error?.response?.data?.message || "Failed to load the conversation");
        }
    };

    const decide = async (conversationId, decision) => {
        setBusy(conversationId);
        try {
            const { data } = await api.post(`/admin/support/approvals/${conversationId}`, {
                decision,
                note: notes[conversationId] || null,
            });
            toast.success(decision === "approve" ? "Approved. The agent told the customer:" : "Rejected.");
            if (decision === "approve") toast(data.reply, { duration: 8000 });
            fetchPending();
        } catch (error) {
            toast.error(error?.response?.data?.message || "The decision could not be applied");
        } finally {
            setBusy(null);
        }
    };

    const money = (amount, currency) =>
        amount == null ? "—" : `${Number(amount).toFixed(2)} ${currency || ""}`.trim();

    return (
        <div className="pb-6 pt-20 px-4 max-w-7xl mx-auto">
            <div className="mb-6 flex items-start justify-between">
                <div>
                    <h1 className="text-3xl font-bold text-gray-800 dark:text-white">AI Refund Requests</h1>
                    <p className="text-gray-500 text-sm">
                        Refunds the support assistant prepared. Nothing is filed until you approve.
                    </p>
                </div>
                <button type="button" onClick={fetchPending} title="Refresh"
                        className="p-2 rounded text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800">
                    <FaSync />
                </button>
            </div>

            {loading ? (
                <div className="flex justify-center py-20">
                    <span className="text-gray-500 dark:text-gray-400">Loading...</span>
                </div>
            ) : rows.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-20 bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-100 dark:border-gray-700">
                    <h2 className="text-gray-700 dark:text-gray-300 font-semibold">Nothing waiting</h2>
                    <p className="text-gray-400 text-sm">Refunds the assistant prepares will appear here.</p>
                </div>
            ) : (
                <div className="overflow-x-auto bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-100 dark:border-gray-700">
                    <table className="w-full text-sm">
                        <thead className="bg-gray-50 dark:bg-gray-900 border-b border-gray-200 dark:border-gray-700">
                            <tr>
                                {["Requested", "Customer", "Order", "Order status", "Amount", "Reason", "Note to customer", "Actions"].map((h) => (
                                    <th key={h} className="px-4 py-3 text-left font-semibold text-gray-700 dark:text-gray-300">{h}</th>
                                ))}
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
                            {rows.map((r) => (
                                <Fragment key={r.conversationId}>
                                    <tr className="hover:bg-gray-50 dark:hover:bg-gray-700/50 align-top">
                                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400 whitespace-nowrap">
                                            {r.requestedAt ? new Date(r.requestedAt).toLocaleString() : "—"}
                                        </td>
                                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400">
                                            <div>{r.customerName || "—"}</div>
                                            <div className="text-xs text-gray-400">{r.customerEmail}</div>
                                        </td>
                                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400">#{r.orderId}</td>
                                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400">{r.orderStatus}</td>
                                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400 whitespace-nowrap">{money(r.orderTotal, r.currency)}</td>
                                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400 max-w-xs">{r.reason}</td>
                                        <td className="px-4 py-3">
                                            <input
                                                aria-label={`Note for order ${r.orderId}`}
                                                maxLength={500}
                                                value={notes[r.conversationId] || ""}
                                                onChange={(e) => setNotes((n) => ({ ...n, [r.conversationId]: e.target.value }))}
                                                placeholder="Optional (shown on reject)"
                                                className="w-48 rounded border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-2 py-1 text-xs"
                                            />
                                        </td>
                                        <td className="px-4 py-3">
                                            <div className="flex items-center gap-2">
                                                <button type="button" onClick={() => toggleTranscript(r.conversationId)} title="Conversation"
                                                        className="p-2 rounded bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-200">
                                                    <FaComments />
                                                </button>
                                                <button type="button" disabled={busy === r.conversationId} onClick={() => decide(r.conversationId, "approve")}
                                                        title="Approve" className="p-2 rounded bg-green-100 text-green-700 disabled:opacity-50">
                                                    <FaCheck />
                                                </button>
                                                <button type="button" disabled={busy === r.conversationId} onClick={() => decide(r.conversationId, "reject")}
                                                        title="Reject" className="p-2 rounded bg-red-100 text-red-700 disabled:opacity-50">
                                                    <FaTimes />
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                    {openTranscript === r.conversationId && (
                                        <tr>
                                            <td colSpan={8} className="px-6 py-4 bg-gray-50 dark:bg-gray-900">
                                                <ol className="space-y-1">
                                                    {transcript.map((m, i) => (
                                                        <li key={i} className="text-sm">
                                                            <span className="font-semibold text-gray-700 dark:text-gray-300">
                                                                {m.role === "customer" ? "Customer" : "Assistant"}:
                                                            </span>{" "}
                                                            <span className="text-gray-600 dark:text-gray-400 whitespace-pre-wrap">{m.content}</span>
                                                        </li>
                                                    ))}
                                                </ol>
                                            </td>
                                        </tr>
                                    )}
                                </Fragment>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
};

export default AdminSupportApprovals;
```

Wire them in:

`ecom-frontend/src/App.jsx`

```diff
@@ -37,6 +37,8 @@ const ProductImport = lazy(() => import('./components/admin/import/ProductImport
 const PromoCampaigns = lazy(() => import('./components/admin/promo/PromoCampaigns'))
 const Orders = lazy(() => import('./components/admin/orders/Orders'))
 const AdminReturns = lazy(() => import('./components/admin/returns/AdminReturns'))
+const AdminSupportApprovals = lazy(() => import('./components/admin/support/AdminSupportApprovals'))
+const SupportChatWidget = lazy(() => import('./components/support/SupportChatWidget'))
 const Bundles = lazy(() => import('./components/bundles/Bundles'))
 const AdminBundles = lazy(() => import('./components/admin/bundles/AdminBundles'))
 const SubscriptionPlans = lazy(() => import('./components/subscriptions/SubscriptionPlans'))
@@ -113,6 +115,7 @@ function App() {
               <Route path='sellers' element={<Sellers />}/>
               <Route path='orders' element={<Orders />}/>
               <Route path='returns' element={<AdminReturns />}/>
+              <Route path='ai-refunds' element={<AdminSupportApprovals />}/>
               <Route path='categories' element={<Category />}/>
               <Route path='coupons' element={<Coupons />}/>
               <Route path='low-stock' element={<LowStockAlerts />}/>
@@ -132,6 +135,9 @@ function App() {
       </Routes>
       </RoutedErrorBoundary>
       </Suspense>
+      <Suspense fallback={null}>
+      <SupportChatWidget />
+      </Suspense>
       </div>
     </Router>
     <Toaster position='bottom-center'/>
```

`ecom-frontend/src/utils/index.js`

```diff
@@ -1,5 +1,5 @@
 import { bannerImageOne, bannerImageThree, bannerImageTwo } from "./constant";
-import { FaBoxOpen, FaBullhorn, FaChartBar, FaClipboardList, FaExclamationTriangle, FaFileImport, FaHome, FaShoppingCart, FaStore, FaSync, FaSyncAlt, FaTag, FaThList, FaUndo, FaUsers } from "react-icons/fa";
+import { FaBoxOpen, FaBullhorn, FaChartBar, FaClipboardList, FaExclamationTriangle, FaFileImport, FaHome, FaRobot, FaShoppingCart, FaStore, FaSync, FaSyncAlt, FaTag, FaThList, FaUndo, FaUsers } from "react-icons/fa";
 
 export const bannerLists =[
 
@@ -70,6 +70,10 @@ export const adminNavigation = [
       href :"/admin/returns",
       icon : FaUndo
     },
+    { name: "AI Refunds",
+      href :"/admin/ai-refunds",
+      icon : FaRobot
+    },
     { name: "Low Stock", 
       href :"/admin/low-stock",
       icon : FaExclamationTriangle
```

And mark agent-filed returns in the existing Returns page:

`ecom-frontend/src/components/admin/returns/AdminReturns.jsx`

```diff
@@ -116,7 +116,15 @@ const AdminReturns = () => {
                             <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
                                 {returns.map((r) => (
                                     <tr key={r.id} className="hover:bg-gray-50 dark:hover:bg-gray-700/50">
-                                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400">#{r.id}</td>
+                                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400">
+                                            #{r.id}
+                                            {r.source === "SUPPORTPILOT" && (
+                                                <span title={`Filed by the AI assistant (conversation ${r.supportConversationId})`}
+                                                      className="ml-2 px-1.5 py-0.5 rounded text-[10px] font-semibold bg-purple-100 text-purple-700">
+                                                    AI
+                                                </span>
+                                            )}
+                                        </td>
                                         <td className="px-4 py-3 text-gray-600 dark:text-gray-400">#{r.orderId}</td>
                                         <td className="px-4 py-3 text-gray-600 dark:text-gray-400">{r.userEmail}</td>
                                         <td className="px-4 py-3 text-gray-600 dark:text-gray-400 max-w-xs truncate">{r.reason}</td>
```

### Step 10 — Run it

New `docker-compose.support.yml` (an override: the existing `docker-compose.yml` stays as it is):

`docker-compose.support.yml`

```yaml
# AI customer support next to the shop: SupportPilot (the agent) + DocuAsk (policy RAG).
#
#   docker compose -f docker-compose.yml -f docker-compose.support.yml up -d --build
#
# Both services are built from their own repositories (paths below, override in .env).
# Nothing here publishes a port to the internet: the browser only ever talks to the
# shop's /api/support/*, and SupportPilot reaches the shop on the compose network.
# The 127.0.0.1 ports are for poking at them from this machine only.

x-support-keys: &support-keys
  SUPPORTPILOT_GATEWAY_KEY: ${SUPPORTPILOT_GATEWAY_KEY:?SUPPORTPILOT_GATEWAY_KEY is required - see .env.example}
  SUPPORTPILOT_ADMIN_KEY: ${SUPPORTPILOT_ADMIN_KEY:?SUPPORTPILOT_ADMIN_KEY is required - see .env.example}
  SUPPORT_INTERNAL_API_KEY: ${SUPPORT_INTERNAL_API_KEY:?SUPPORT_INTERNAL_API_KEY is required - see .env.example}

services:
  backend:
    environment:
      <<: *support-keys
      SUPPORT_ENABLED: "true"
      SUPPORTPILOT_URL: http://supportpilot:8000

  supportpilot-mongo:
    image: mongo:8
    volumes:
      - supportpilot-mongo-data:/data/db
    healthcheck:
      test: ["CMD", "mongosh", "--quiet", "--eval", "db.adminCommand('ping').ok"]
      interval: 5s
      timeout: 5s
      retries: 10

  supportpilot:
    build: ${SUPPORTPILOT_DIR:-../roadmaps/ai-engineer/supportpilot}
    ports:
      - "127.0.0.1:8001:8000"
    environment:
      GATEWAY_API_KEY: ${SUPPORTPILOT_GATEWAY_KEY}
      ADMIN_API_KEY: ${SUPPORTPILOT_ADMIN_KEY}
      MONGO_URL: mongodb://supportpilot-mongo:27017
      # The shop's Redis, its own database index so rate-limit keys never collide.
      REDIS_URL: redis://:${REDIS_PASSWORD:-}@redis:6379/2
      # The real order system: the shop's internal API, same contract as orders_mock.py.
      ORDERS_BASE_URL: http://backend:8080/internal/support
      ORDERS_API_KEY: ${SUPPORT_INTERNAL_API_KEY}
      DOCUASK_BASE_URL: http://docuask:8000
      DOCUASK_API_KEY: ${DOCUASK_API_KEY:?DOCUASK_API_KEY is required - see .env.example}
      LLM_PROVIDER: ${SUPPORT_LLM_PROVIDER:-fake}
      OPENAI_API_KEY: ${OPENAI_API_KEY:-}
      CHAT_MODEL: ${SUPPORT_CHAT_MODEL:-gpt-4.1-mini}
    depends_on:
      supportpilot-mongo:
        condition: service_healthy
      redis:
        condition: service_started
      docuask:
        condition: service_started

  docuask-db:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_USER: docuask
      POSTGRES_PASSWORD: ${DOCUASK_DB_PASSWORD:-docuask}
      POSTGRES_DB: docuask
    volumes:
      - docuask-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U docuask -d docuask"]
      interval: 5s
      timeout: 3s
      retries: 10

  docuask-migrate:
    build: ${DOCUASK_DIR:-../roadmaps/ai-engineer/docuask}
    command: ["alembic", "upgrade", "head"]
    environment:
      DATABASE_URL: postgresql+psycopg://docuask:${DOCUASK_DB_PASSWORD:-docuask}@docuask-db:5432/docuask
    depends_on:
      docuask-db:
        condition: service_healthy

  docuask:
    build: ${DOCUASK_DIR:-../roadmaps/ai-engineer/docuask}
    ports:
      - "127.0.0.1:8000:8000"
    environment: &docuask-env
      DATABASE_URL: postgresql+psycopg://docuask:${DOCUASK_DB_PASSWORD:-docuask}@docuask-db:5432/docuask
      API_KEYS: ${DOCUASK_API_KEY}
      LLM_PROVIDER: ${SUPPORT_LLM_PROVIDER:-fake}
      OPENAI_API_KEY: ${OPENAI_API_KEY:-}
      MAX_DISTANCE: ${DOCUASK_MAX_DISTANCE:-0.95}
    depends_on:
      docuask-migrate:
        condition: service_completed_successfully

  # One-shot: loads this shop's policies. Safe to re-run - unchanged files are skipped (sha256).
  docuask-ingest:
    build: ${DOCUASK_DIR:-../roadmaps/ai-engineer/docuask}
    command: ["python", "-m", "docuask.cli", "ingest", "/policies"]
    environment: *docuask-env
    volumes:
      - ./support/policies:/policies:ro
    depends_on:
      docuask-migrate:
        condition: service_completed_successfully

volumes:
  supportpilot-mongo-data:
  docuask-data:
```

1. Fill the new `.env` values (four different random keys):

```bash
openssl rand -hex 32
```

2. Start everything:

```bash
docker compose -f docker-compose.yml -f docker-compose.support.yml up -d --build
```

3. The policies are loaded by `docuask-ingest`; check it finished:

```bash
docker compose -f docker-compose.yml -f docker-compose.support.yml logs docuask-ingest
```

**Backend from the IDE instead of compose** (your usual dev loop): run SupportPilot from its own compose with
`ORDERS_BASE_URL=http://host.docker.internal:8080/internal/support` and `ORDERS_API_KEY=<same internal key>`, and give
the backend `SUPPORT_ENABLED=true`, `SUPPORTPILOT_URL=http://localhost:8001` and the three keys.

Notes:

- If `REDIS_PASSWORD` contains `@ : / ? #`, URL-encode it inside `REDIS_URL`.
- With `SUPPORT_LLM_PROVIDER=fake` the scripted model only recognises order numbers of **4–6 digits**
  (`ORDER_ID` regex in `fake.py`). A fresh shop has orders 1, 2, 3…; for a fake-model demo either test with a real model
  or start the order sequence at 1000 in your dev database.

### Step 11 — Try it by hand

1. Admin: Orders → move an order of `user1` to **Delivered** (this sets `delivered_at`).
2. `user1`: open the chat → "Where is my order 1042?" → status from the real order.
3. "What is your return policy?" → answer with citations from `support/policies/returns.md`.
4. "I want a refund for order 1042, it arrived broken" → "sent to a colleague for approval"; input locks.
5. Admin → **AI Refunds**: the row shows user1's email, order 1042, its total; open the transcript; **Approve**.
6. `user1`'s chat unlocks within 15 s with the agent's answer; Admin → **Returns** shows the return **APPROVED** with an
   **AI** badge; the order is **Returned**.
7. From here it is the normal flow: tracking → mark refunded → Stripe refund via the outbox.
8. Ask about someone else's order number → "No order … was found for this customer".

---

## 5. What was verified

| Check | Result |
|---|---|
| Backend compile (main + tests), offline Maven | OK |
| New `SupportPilotIntegrationTest` (Testcontainers Postgres + stub SupportPilot): key required, order shape, approved return + idempotent replay, 404 other customer, 409 shipped / 45-day-old, gateway headers, ownership before forwarding, 429 `Retry-After` / 503 mapping, admin queue enrichment, customer forbidden from admin queue | **9 / 9 pass** |
| Existing suites touched by the change: `ReturnServiceTest`, `GdprErasureTest`, `GdprExportTest`, `IdorAuthorizationTest` (55), `OpenApiContractTest`, `ForeignKeyIndexTest` (runs V1–V37 with Flyway on pgvector) | **82 / 82 pass** |
| SupportPilot full suite (real Mongo + Redis via Testcontainers) | **64 pass** (58 before + 6 new); `ruff check` + `ruff format --check` clean |
| Frontend: Vitest full suite | **70 pass** (66 before + 4 new widget tests); ESLint clean on new files; `vite build` OK |
| `docker compose ... config` with the override | valid, env merged as intended |
| Both patches | `git apply --check` clean on fresh checkouts |
| **Full stack, `docker compose up --build`** (isolated project, fresh volumes, fake LLM) | all 12 services built; 9 running and healthy, 3 one-shot jobs exited 0; Flyway V1–V37 applied, Hibernate `validate` passed; DocuAsk ingested the 4 policies |
| Service-to-service on the compose network | SupportPilot → backend `/internal/support`: 401 without key, served with key; SupportPilot → DocuAsk and the shared Redis (db 2, password): up; backend → SupportPilot: up |
| End-to-end over HTTP (real cookies + CSRF, seeded `admin` / `user1`) | admin → Delivered sets `delivered_at`; order answer from the real order; policy answer cited from `returns.md`; another customer's order = "not found"; refund → `awaiting_approval`; message while paused = 409; customer on admin queue = 403; queue shows email + 89.90 USD; approve → return `APPROVED` / `SUPPORTPILOT` with the idempotency key, order `Returned`, customer sees the answer; second approve = 404; one request id in the shop's and SupportPilot's logs; 0 errors in the three services' logs |
| Full backend suite on the branch (`mvnw test`) | 576 tests: 575 pass; `ForeignKeyIndexTest` failed only on a stale `target/classes` V37 from another branch and passes after `mvnw clean` |
| Browser (widget) | signed in as `user1`: chat resumes the latest conversation, a shipping question answers 3 / 5 USD, free from 100 USD, citing `shipping.md` |

**Not verified:** behaviour with a real LLM (the fake model was used everywhere).

Running it next to other local projects: the e2e above used `-p ecom-e2e` plus a throwaway override for
`container_name` and the backend's host port. On Windows `localhost` may resolve to `::1` first, so if another Vite
server holds `[::1]:5173` open the shop at `http://127.0.0.1:5173` and add that origin to `CORS_ALLOWED_ORIGINS`.

## 6. Known limits / follow-ups

- **Migration number clash:** the unmerged local branch `fix/audit-2fa-throttle-and-subscription-indexes` also adds a
  `V37` (`V37__user_subscription_lookup_indexes.sql`). Whichever branch merges second must rename its file to `V38__…`.

- Customer-filed returns (My Orders button) still have no 30-day check; only the agent's path enforces it. Making
  `ReturnServiceImpl.requestReturn` enforce `delivered_at` too would align the whole shop with returns.md.
- The shop has no outbound carrier/ETA, so `get_order` answers with `carrier: null`.
- One chat turn holds a Tomcat thread for up to `app.support.read-timeout` (90 s). Fine at this scale; virtual threads
  (`spring.threads.virtual.enabled=true`) or streaming would be the next step.
- Cash-on-delivery refunds stay manual (as today: `RefundService` skips the gateway for COD).
- Pre-existing, not touched: `ReturnServiceImpl.approveReturn` sets the order to `Returned` directly, bypassing the
  `OrderStatus` state machine and its restock.
