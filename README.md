# EcommerceHub: Enterprise Multi-Vendor Platform

![JaCoCo Coverage](https://img.shields.io/badge/coverage-JaCoCo%20report-blue)
![CI](https://github.com/FILIPIOAN10/e-commerce-app/actions/workflows/ci.yml/badge.svg)

A full-stack, multi-vendor e-commerce marketplace with separate portals for **admin**, **seller**, and **customer** users. Built with **Spring Boot** and **React**, it covers product catalog, order management, payments, AI-powered search, recommendations, and real-time inventory.

The stack is containerized with **Docker Compose** and uses **PostgreSQL + pgvector**, **Redis**, and **HashiCorp Vault** for data, caching, and secrets.

## What it does

- **Multi-role marketplace** — admin, seller, and customer portals with role-based access control.
- **Product & inventory** — product management, categories, low-stock alerts, image galleries, and recently viewed tracking.
- **Cart & checkout** — stackable coupons, shipping cost estimation, guest checkout, cart save-for-later, and Stripe payments.
- **AI search & recommendations** — natural language product search and "Recommended for You" / "Similar Products" powered by OpenAI embeddings and pgvector.
- **Security** — JWT authentication, OAuth2 (GitHub, Google), TOTP 2FA with purpose-scoped challenge tokens, CSRF protection, and Vault-backed secrets.
- **Observability & scale** — Redis caching, rate limiting, audit logging, Flyway migrations, and JaCoCo test coverage.

## Tech Stack

### Backend
- Java 21, Spring Boot 4.1.0, Spring Security, Spring AI 2.0.0, Spring Cloud 2025.1.2
- PostgreSQL 16 + pgvector, Spring Data JPA / Hibernate, Flyway
- Redis 7.4 (caching, rate limiting, recently viewed)
- HashiCorp Vault (secrets)
- JWT, OAuth2 (GitHub + Google), TOTP 2FA
- Stripe SDK, SpringDoc OpenAPI

### Frontend
- React, Redux, React Router
- Vite, Tailwind CSS, Material UI
- React.lazy + Suspense (code-splitting)

### Infrastructure
- Docker Compose
- PostgreSQL 16 + pgvector extension
- Redis 7.4 Alpine
- HashiCorp Vault
- Maven Eclipse Temurin 21

## Key Features

### Catalog & Discovery

| Feature | Description |
|---|---|
| Product Management | Full CRUD with pagination, sorting, and `@Version` optimistic locking; admin sees all, sellers see their own |
| Category Management | Admin CRUD with category → products browsing |
| Product Image Gallery | Multiple images per product with carousel, thumbnails, and signed image URLs |
| Faceted Search | `/public/products/search/faceted` with computed facet dimensions and filter composition |
| Keyword Search & Autocomplete | Cached SQL keyword search plus a type-ahead autocomplete endpoint |
| Semantic Search | OpenAI embeddings + pgvector (HNSW, cosine) with a NoOp fallback so the app runs with AI disabled |
| Search Reindex | Admin-triggered re-embedding of the product catalogue |
| AI Recommendations | "Recommended for You" and "Similar Products" via vector similarity |
| Frequently Bought Together | Co-purchase suggestions on the product detail page |
| Featured Products | Curated homepage product rail |
| Recently Viewed | Per-user product tracking in Redis |
| Product Q&A | Customer questions with seller/admin answers, public read |
| Reviews & Ratings | Per-product reviews with pagination, helpful/unhelpful votes, and denormalised rating aggregates |
| Wishlist | Per-user wishlist with add/remove/list |
| Product Compare | Client-side side-by-side comparison page |
| Product Bundles | Admin-managed bundles, public browse, add-bundle-to-cart |
| Bulk Product Import | Admin file import for catalogue seeding |

### Cart & Checkout

| Feature | Description |
|---|---|
| Shopping Cart | Add/update/remove with one cart per user enforced at the DB level |
| Save for Later | Move items out of the cart and back without losing them |
| Guest Checkout | Full purchase flow with no account required |
| Abandoned Cart Recovery | Advisory-locked sweep that sends staged reminders through the outbox with a tokenised recovery link |
| Coupon System | Admin-managed discount coupons with expiry, usage limits, and checkout validation |
| Promo Campaigns | Scheduled campaigns that apply and revert product pricing idempotently |
| Pricing Pipeline | Ordered rules — coupon discount → shipping → VAT — over a shared BigDecimal breakdown |
| VAT / Tax | Per-country rates with configurable default, taxable-shipping toggle, and a global off switch |
| Order Preview | Price breakdown (subtotal + shipping + VAT) before payment |
| Shipping Estimation | Per-address shipping cost lookup |
| Stock Reservation | Redis-held checkout reservation with a TTL so carts cannot oversell |
| Address Book | Full CRUD for user addresses with ownership checks |
| Geo City Lookup | Backend-served city index so the address form no longer ships a 7.9 MB dataset to the browser |

### Payments, Orders & Money

| Feature | Description |
|---|---|
| Stripe Payments | PaymentIntent flow with order creation and inventory consumption |
| Pluggable Payment Gateways | `PaymentGateway` + registry so a provider is a new bean, not an if/else branch |
| Stripe Webhooks | Single signature-verified endpoint deduped on event id, dispatching to typed handlers |
| Idempotency Keys | `Idempotency-Key` header makes state-changing requests replay-safe under a unique DB claim |
| Order Lifecycle | Status transitions for admin and seller, customer-facing tracking, and customer cancel |
| Invoice Generation | OpenPDF invoices backed by a gapless invoice-number sequence |
| Order Export | Admin CSV and PDF exports of the order book |
| Returns (RMA) | Customer return requests with admin approve/reject/refund and courier tracking |
| Automated Refunds | Stripe refunds driven through the outbox with a refund status machine |
| Disputes & Chargebacks | `charge.dispute.*` webhooks create dispute records; admins upload evidence into a non-web-served directory with deadline alerts |
| Subscriptions | Public plans, Stripe checkout, my-subscriptions, cancel, and admin plan CRUD |
| Subscription Lifecycle | Handlers for checkout completed, invoice paid/failed, subscription updated/deleted, with renewal sweep |
| Multi-Currency | Frankfurter/ECB rate provider with fixed-rate fallback, cached rates, presentation-only conversion over a USD base |
| Money Precision | BigDecimal end to end — no money field or column is a float |

### Inventory

| Feature | Description |
|---|---|
| Stock Movement Ledger | Every quantity change appends an append-only movement row with a reason code |
| Stock Reconciliation | Hourly sweep flagging any product whose movements no longer sum to its quantity |
| Low Stock Alerts | Threshold monitoring with admin/seller notifications, counts, and summary |
| Admin Stock Views | Per-product movement history and a discrepancy report |

### Authentication & Security

| Feature | Description |
|---|---|
| Multi-role RBAC | Admin, Seller, Customer portals with route and API authorization |
| JWT Auth | Access tokens in HttpOnly cookies, stateless Spring Security |
| Refresh Token Rotation | Redis-backed refresh sessions with rotation on every use |
| Device / Session Management | List active devices and revoke one or all sessions |
| OAuth2 Login | GitHub and Google login with auto user provisioning |
| 2FA / TOTP | Time-based one-time passwords with purpose-scoped JWT challenge tokens and QR enrolment |
| Email Verification | Signup verification link with resend |
| Password Reset | Forgot-password and tokenised reset flow |
| Login Lockout | Failed-attempt lockout with a self-service unlock request and admin unlock |
| Rate Limiting | Redis fixed-window limiting across ~16 per-endpoint rules keyed by IP or user, failing open |
| CSRF Protection | CookieCsrfTokenRepository for SPA state-changing operations |
| CORS Allow-list | Configurable origin list shared with the WebSocket handshake |
| Vault Integration | Centralized secrets management — zero secrets in version control, fail-fast if unset |
| Audit Logging | Admin audit log plus per-user activity tracking, both queryable in the admin UI |
| GDPR Export | Art. 15 async ZIP export delivered as a single-use expiring link, purged on TTL |
| GDPR Erasure | Art. 17 two-step deletion — password plus emailed confirmation — anonymising what must be kept |

### Async & Messaging

| Feature | Description |
|---|---|
| Transactional Outbox | Events written in the business transaction, drained with `FOR UPDATE SKIP LOCKED`, exponential backoff, and dead-lettering |
| Outbox Handlers | Nine handlers: order confirmation, order status, cart abandonment, GDPR export, refund, dispute opened/closed, subscription ended, subscription payment failed |
| Domain Events | Order-lifecycle listeners for email, invoice, notification, and activity log on a bounded executor |
| Transactional Email | Seven HTML templates covering verification, reset, orders, cart recovery, and GDPR |
| Real-time Notifications | STOMP/WebSocket authenticated at the handshake from the auth cookie, with per-user push |
| Notification Bell | Unread count, notification list, and mark-all-read in the SPA |
| Scheduled Jobs | Six schedulers: outbox dispatcher, abandoned-cart sweep, promo sweep, stock reconciliation, subscription renewal, GDPR purge |

### Admin & Analytics

| Feature | Description |
|---|---|
| Admin Dashboard | Sales over time, top products, order-status split, and revenue by category |
| Admin Console | Fourteen managed areas: dashboard, products, bundles, subscriptions, sellers, orders, returns, categories, coupons, low stock, activity logs, import, campaigns, users |
| Seller Portal | Seller-scoped products, orders, and low-stock views |
| User Management | List users, change roles, delete accounts, unlock locked accounts |
| GraphQL Admin API | Read-side queries for products, categories, orders, users, coupons, campaigns, and returns alongside REST |
| Contact Form | Public contact endpoint with rate limiting |

### Frontend Platform

| Feature | Description |
|---|---|
| Localised Routing | Language in the URL (`/:lang/...`) as the single source of truth, with a language switcher |
| i18n | Three locales (English, French, Romanian) via react-i18next |
| SEO Meta | hreflang, canonical, and og:locale per language route |
| Dark Mode & Theming | Tailwind v4 and MUI driven by one shared token set and one toggle |
| Code Splitting | React.lazy + Suspense for route-level lazy loading |
| UI State Kit | Skeletons, empty states, error boundary, spinners, status badges, trust badges, breadcrumbs |
| Admin Data Grids | MUI X DataGrid tables with server-side pagination and row actions |
| Accessible Forms | react-hook-form fields with announced validation errors |
| Redux State | Nineteen reducers with an axios client carrying auth and CSRF interceptors |

### Platform & Operations

| Feature | Description |
|---|---|
| Flyway Migrations | 35 versioned migrations with `ddl-auto=validate` drift detection |
| Redis Caching | Nine named caches with per-cache TTLs and transaction-aware eviction |
| Observability | Actuator + Micrometer/Prometheus metrics, Grafana dashboard, alert rules, JSON logging |
| Docker Compose | Eight-service local stack: Postgres+pgvector, Redis, Vault, vault-init, backend, frontend, Prometheus, Grafana |
| Integration Testing | 97 backend test classes on Testcontainers Postgres + Redis, with RestAssured and OpenAPI request validation |
| Frontend & E2E Testing | Vitest component suites, eight Playwright specs, and a Selenium page-object suite |
| CI/CD | Five GitHub Actions workflows — CI, CodeQL, GHCR build-push, deploy, Selenium — plus gitleaks and Dependabot |
| Coverage Gate | JaCoCo enforcing ≥60% line coverage on the service layer at `verify` |
| API Documentation | SpringDoc OpenAPI with Swagger UI and a Postman collection |


## Engineering decisions
1. Authentication & Security (JWT, OAuth2, CSRF)
What to test: Login, registration, token refresh, and CSRF protection on state-changing routes.

Prerequisites: Running frontend (:5173) and backend, test user created.

Steps:

Go to /login and submit valid credentials. Verify you receive the HttpOnly auth cookie and redirect to the correct base language route (e.g., /en/products).

Test a state-changing endpoint like /api/auth/signout without a valid session/token. Verify it is rejected (CSRF active).

Sign in, click "Sign out", and verify cookies/session are properly cleared.

Expected Result: Secure authentication flow; unprotected routes reject unauthorized access; sign-out cleanly drops the session.

2. Multi-Currency Selector
What to test: Switching currencies on the frontend and verifying correct presentation conversion.

Prerequisites: App running, products available in the catalogue.

Steps:

Open the homepage/product list (/en/products).

Use the currency picker (header/settings) to switch from USD to EUR (or JPY).

Observe product prices updating instantly on the UI.

Expected Result: Prices convert based on the cached exchange rates (fetched from ECB/config) while the base settlement in the backend remains in USD.


3. Cart & Checkout (Stock Locking & Money Precision)
What to test: Adding items, Redis stock reservation, exact BigDecimal calculation (including VAT & Shipping).

Prerequisites: Logged-in user, active product with stock.

Steps:

Add an item to the cart. Verify Redis holds a temporary stock reservation.

Proceed to checkout, enter a shipping address, and review the price breakdown (Subtotal + Shipping + VAT).

Complete payment via Stripe.

Expected Result: Order is placed, stock is permanently consumed in Postgres via the stock movement service, exact cent precision is maintained, and an outbox event triggers the confirmation email.

4. Stripe Subscriptions & Webhooks
What to test: Subscription lifecycle management via unified Stripe webhooks.

Prerequisites: Stripe CLI configured for webhook forwarding to /api/public/webhooks/stripe.

Steps:

Trigger a test subscription checkout.

Simulate Stripe events via CLI (e.g., stripe trigger customer.subscription.created, invoice.payment_failed).

Expected Result: The single deduped webhook endpoint processes the event, updates the subscription state via SubscriptionLifecycleService, and triggers outbox notifications for failures/cancellations.

5. Chargebacks & Disputes (New Feature - V33)
What to test: Handling Stripe dispute events, evidence uploads, and dispute state machines.

Prerequisites: Admin privileges, existing order in the system.

Steps:

Simulate a charge.dispute.created webhook event from Stripe.

Log in as an admin, navigate to the disputes panel, and upload a supporting evidence file.

Verify the file goes through FileService into the secure, non-web-served directory (app.disputes.evidence-dir).

Expected Result: Dispute record is created in NEEDS_RESPONSE status, outbox fires the deadline alert, and evidence files are securely stored without public web access.

6. Admin Actions & Sweeps (Async Outbox & Background Jobs)
What to test: Outbox dispatcher processing and campaign/stock sweeps.

Prerequisites: Background schedulers active (or triggered via test profiles).

Steps:

Trigger actions that generate outbox events (e.g., order creation or refund).

Observe the outbox poller claim rows with FOR UPDATE SKIP LOCKED, then run each handler with exponential backoff on failure.

Watch a row through one delivery: it goes PENDING → IN_PROGRESS (claimed) → DONE, and the claim commits before the handler starts — a `SELECT status FROM outbox_event` from psql reads IN_PROGRESS while the handler is still working.

Expected Result: Background tasks run safely without overlapping, and emails/invoices are processed reliably.

7. Outbound Email (SMTP Timeouts)
What to test: That a mail server which stops responding fails the send instead of hanging it.

Prerequisites: A way to black-hole SMTP — a firewall DROP rule to port 587, or point `spring.mail.host` at an address that accepts connections and never replies.

Steps:

Trigger any email (register an account, or place an order).

Watch the outbox row and the backend log.

Expected Result: The send gives up after `mail.smtp.timeout` (10s by default) rather than blocking forever. The outbox records the failure, backs the event off, and retries it later; the dispatcher keeps draining everything else in the queue.

Why it matters: all three JavaMail timeouts default to infinite. A server that accepts the TCP connection and then stops answering — a partial outage, a firewall that black-holes rather than rejects, a throttled sender — parks the calling thread permanently, with no exception and no log line, because the call never returns. Mail is sent from the outbox dispatcher, so a single stalled send would stop the queue that also carries refunds and GDPR exports, and only a restart would clear it. The defaults here are 5s connect and 10s read/write, overridable with `MAIL_SMTP_CONNECT_TIMEOUT_MS`, `MAIL_SMTP_READ_TIMEOUT_MS` and `MAIL_SMTP_WRITE_TIMEOUT_MS`. Connect is the tightest of the three: a TCP handshake either happens quickly or is not going to, while delivering the message legitimately takes longer. `SmtpTimeoutConfigurationTest` asserts them off the `JavaMailSender` bean rather than out of the environment — a misspelled key would still be present as a property and still be silently ignored by JavaMail.
Why the claim is a lease, not a lock: handlers do slow external work — a Stripe refund, an SMTP send, building a GDPR archive — and `processBatch()` used to be `@Transactional`, so all of it ran inside the transaction that claimed the batch. That transaction held a pooled connection and the claimed rows' locks throughout. Stripe's client defaults to an 80s read timeout, so one latency spike against a batch of 20 could park one of `DB_POOL_MAX` connections for close to half an hour while the storefront's own checkouts timed out on `hikari.connection-timeout` — the same failure `StripeServiceImpl` documents for the request path. The dispatcher now opens only short transactions (claim, then record each outcome) and runs handlers with none. What keeps a second dispatcher off a row while its handler runs is the lease written at claim time (`app.outbox.lease-seconds`, default 300); once it expires the row is claimable again, which is how an event survives a process that died mid-handler without needing a reaper job. The attempt is counted at claim rather than on failure, so an event that takes the process down still exhausts its budget and dead-letters. Handlers must be idempotent — at-least-once delivery already required that.

8. Checkout Address Form (City Lookup)
What to test: That the country → state → city dropdowns still cascade, and that opening the form no longer downloads a multi-megabyte dataset.

Prerequisites: App running, signed-in user with something in the cart.

Steps:

Open devtools on the Network tab, then go to /en/checkout and open the address form.

Pick a country, then a state, and watch the requests.

Expected Result: Two lazy chunks load (country ~96 KB, state ~555 KB), and picking a state fires GET /api/public/geo/cities?country=RO&state=CJ returning a few KB of names. The city dropdown populates from that response. No chunk over 1 MB is fetched. A second visit re-uses the cached city response for a day.

Why it matters: the form used to resolve cities in the browser from the country-state-city package, whose city.json is 7.9 MB — 92% of an 8.7 MB chunk (2.3 MB gzipped) downloaded the moment the address form opened, which is the single highest-value moment in the app and often the worst connection. Countries and states together are only 635 KB, so they stay client-side for instant dropdowns; only the cities moved to the backend. The package's barrel re-exports City, so importing it drags city.json in even when City is never called — hence the deep imports of `country-state-city/lib/country` and `/lib/state` in AddAddressForm. The backend reads a reshaped copy of the dataset (grouped by country-state, names only: 7.7 MB → 2.0 MB) that is parsed on first request rather than at startup; regenerate it with `python scripts/generate-city-index.py` after upgrading the npm package. If the lookup is unreachable the city list stays empty and the address can still be saved — a dropdown that never populates must not become a checkout the customer cannot complete.

## Getting Started
 
### Prerequisites
- Docker & Docker Compose
- Node.js 18+ (for frontend)
 
### Backend & Infrastructure
```bash
# Clone the repository
git clone https://github.com/FILIPIOAN10/e-commerce-app.git
cd e-commerce-app
 
# Configure environment variables
cp .env.example .env
# Edit .env with your secrets (JWT_SECRET, OPENAI_API_KEY, STRIPE_SECRET_KEY, etc.)
 
# Start all services
docker compose up --build
```

`docker-compose.yml` contains **no secrets**. Every credential is read from the
git-ignored `.env`, forwarded into HashiCorp Vault by `scripts/vault-init.sh`, and
resolved by Spring Cloud Vault at startup. Required variables have no fallback
defaults, so Compose fails fast with a clear message if one is missing.

The stack runs under the **`local`** Spring profile. Vault and Spring AI are the
only things disabled (both incompatible with Spring Boot 4.1 in local Docker) —
rate limiting and the email-verification signup flow run exactly as in
production. If you have no SMTP server, run a fake one (`npx maildev --smtp 1025`)
or set `APP_SKIP_VERIFICATION_EMAIL=true` in `.env`; set `RATE_LIMIT_ENABLED=false`
only for load tests.

### Database migrations

The schema is owned by **Flyway**, not by Hibernate. Migrations live in
`ecom-backend/src/main/resources/db/migration` and run automatically on startup.
There are **35 versioned migrations** covering **38 tables**:

| Migration | Purpose |
|---|---|
| `V1__baseline_schema.sql` | Baseline: 17 tables, FKs, unique constraints, indexes |
| `V2__seed_roles_and_users.sql` | Roles and demo users (replaces the old `data.sql`) |
| `V3__add_cart_checkout_columns.sql` | Cart-item and order columns needed by checkout |
| `V4__reviews_social_and_questions.sql` | Helpful/unhelpful votes on reviews, plus the product Q&A table |
| `V5__admin_analytics.sql` | `user_activity_logs`, `promo_campaigns`, `promo_campaign_products` |
| `V6__return_tracking_columns.sql` | Courier tracking columns on return requests |
| `V7__create_bundles.sql` | `bundles` + `bundle_products` |
| `V8__create_subscriptions.sql` | `subscription_plans` + `user_subscriptions` |
| `V9__db_indexes.sql` | Performance indexes for orders, cart items and products |
| `V10__create_processed_webhook_events.sql` | Webhook dedup table — the unique event id is the exactly-once claim |
| `V11__create_admin_audit_logs.sql` | Admin audit trail |
| `V12__seed_demo_products.sql` | Demo catalogue for local/Selenium runs (removed again by V34) |
| `V13__unique_payment_intent.sql` | One order per Stripe payment id; NULL/empty (COD) excluded |
| `V14__stock_and_coupon_integrity.sql` | Database-level invariants for stock and coupon usage, after clamping bad rows |
| `V15__hnsw_vector_index.sql` | HNSW index on the semantic-search vector store |
| `V16__create_idempotency_keys.sql` | `Idempotency-Key` records — request hash + stored response, replayed on retry |
| `V17__add_optimistic_lock_versions.sql` | `version` columns so concurrent edits 409 instead of last-write-wins |
| `V18__create_invoice_numbering.sql` | Gapless per-year fiscal invoice numbering (a SEQUENCE would burn numbers) |
| `V19__create_outbox.sql` | Transactional outbox for durable post-commit side effects |
| `V20__abandoned_cart_recovery.sql` | `last_activity_at` on carts, reminder stages, recovery tokens |
| `V21__gdpr_export_and_erasure.sql` | Art. 15 export records and Art. 17 erasure/anonymisation support |
| `V22__product_rating_denormalisation_and_facet_indexes.sql` | Denormalised product rating + facet indexes for filtered search |
| `V23__create_stock_movement.sql` | Append-only stock ledger behind `products.quantity` |
| `V24__order_money_to_numeric.sql` | Order money: `DOUBLE PRECISION` → `NUMERIC` |
| `V25__product_and_cart_money_to_numeric.sql` | Product and cart money → `NUMERIC` |
| `V26__carts_one_per_user.sql` | One cart per user, enforced by the database |
| `V27__promo_campaign_apply_and_revert.sql` | Remembers original prices so a campaign can be reverted |
| `V28__missing_foreign_key_indexes.sql` | The foreign keys that were still unindexed |
| `V29__order_tax_amount.sql` | `tax_amount` on orders for the VAT line |
| `V30__legacy_money_fields_to_numeric.sql` | The last four money columns leave `double` — no float money remains |
| `V31__create_refunds.sql` | `refunds` table behind automated Stripe refunds |
| `V32__multi_currency.sql` | Presentation currency on top of the USD base |
| `V33__disputes.sql` | `disputes` + `dispute_evidence_files` for chargebacks |
| `V34__remove_demo_catalog_seed.sql` | Drops the V12 demo catalogue everywhere except the Selenium suite |
| `V35__outbox_in_progress_lease.sql` | Outbox claim becomes a lease (IN_PROGRESS + expiry) so handlers run outside the claim transaction |

The Selenium suite drives fixed URLs (`/products/1`) and needs a known catalogue,
so its workflow re-adds the seed by appending `classpath:db/seed` (the repeatable
`R__demo_catalog.sql`) to `spring.flyway.locations`. Every other database stays
without it.

Hibernate runs with `ddl-auto=validate`, so the application refuses to start if an
entity ever drifts out of sync with the schema.

To add a change, create a new file — never edit an applied one:

```bash
# ecom-backend/src/main/resources/db/migration/V36__add_product_sku.sql
ALTER TABLE products ADD COLUMN sku VARCHAR(64);
```

An existing database created by the previous `ddl-auto=update` setup is adopted
automatically: `baseline-on-migrate` stamps it at version 1 and continues from V2,
so no data is lost. To rebuild from scratch:

```bash
docker compose down -v && docker compose up --build
```

Inspect applied migrations at any time:

```bash
docker exec -it ecommerce-postgres psql -U postgres -d ecommerce \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

### Frontend

```bash
cd ecom-frontend
npm install
npm run dev
```

The app is served at `http://localhost:5173`, the API at `http://localhost:8080`,
and Swagger UI at `http://localhost:8080/swagger-ui.html`.

### Demo accounts

| Username | Password | Roles |
|---|---|---|
| `admin` | `adminPass` | USER, SELLER, ADMIN |
| `user1` | `password1` | USER |
| `seller1` | `password2` | SELLER |

### Test Coverage (JaCoCo)

```bash
# Run tests and generate coverage report:
cd ecom-backend && ./mvnw test
# Open the HTML report:
# target/site/jacoco/index.html
# The verify phase enforces >= 60% line coverage on the service layer:
./mvnw verify
```

<!-- CI trigger test - 2026-08-12 -->
