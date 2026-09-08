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

| Feature | Description |
|---|---|
| Multi-role RBAC | Admin, Seller, Customer portals with route and API authorization |
| OAuth2 Login | GitHub and Google login with auto user provisioning |
| 2FA / TOTP | Time-based one-time passwords with purpose-scoped JWT challenge tokens |
| Semantic Search | OpenAI embeddings + pgvector with SQL fallback |
| AI Recommendations | "Recommended for You" and "Similar Products" via vector similarity |
| Rate Limiting | Redis-based distributed rate limiting per endpoint |
| Caching | Redis TTL cache for products, categories, and search results |
| Stripe Payments | PaymentIntent flow with order and inventory management |
| Vault Integration | Centralized secrets management — zero secrets in version control |
| Flyway Migrations | Versioned schema evolution with `ddl-auto=validate` drift detection |
| CSRF Protection | CookieCsrfTokenRepository for SPA state-changing operations |
| Admin Dashboard | Product/category/order/seller management with analytics |
| Audit Logging | Request-level activity tracking |
| Low Stock Alerts | Threshold monitoring with admin/seller notifications |
| Product Image Gallery | Multiple images per product with carousel and thumbnails |
| Code Splitting | React.lazy + Suspense for route-level lazy loading |
| Recently Viewed | Per-user product tracking in Redis |
| Coupon System | Admin-managed discount coupons with expiry and usage limits |
| Reviews & Ratings | Per-product reviews with pagination |
| Wishlist | Per-user wishlist with add/remove |

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

Observe the outbox poller using FOR UPDATE SKIP LOCKED to process events with exponential backoff on failure.

Expected Result: Background tasks run safely without overlapping, and emails/invoices are processed reliably.

7. Checkout Address Form (City Lookup)
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
`ecom-backend/src/main/resources/db/migration` and run automatically on startup:

| Migration | Purpose |
|---|---|
| `V1__baseline_schema.sql` | Full schema: 17 tables, FKs, unique constraints, indexes |
| `V2__seed_roles_and_users.sql` | Roles and demo users (replaces the old `data.sql`) |

Hibernate runs with `ddl-auto=validate`, so the application refuses to start if an
entity ever drifts out of sync with the schema.

To add a change, create a new file — never edit an applied one:

```bash
# ecom-backend/src/main/resources/db/migration/V3__add_product_sku.sql
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
