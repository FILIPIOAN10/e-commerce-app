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

The parts of this codebase worth a second look are the ones where a naive
implementation is subtly wrong under concurrency or against a hostile client.
Each decision below is stated as *problem → approach → trade-off*.

**Stock is never over-sold, and lapsed carts never leak it.** Checkout reserves
stock in Redis while the customer is paying. The reserved total per product is
*always derived* from the live reservations — a sorted set scored by expiry — so
a reservation that times out simply stops counting; there is no separate counter
that can drift. The reserve operation (release old, prune expired, check, create)
runs as one atomic Lua script, closing the check-then-act gap that let two
requests both see "enough stock". The authoritative oversell gate is a
conditional `UPDATE … WHERE quantity >= :qty` at consume time: the loser of a
race is rejected there. *Trade-off:* the reservation check reads on-hand stock as
a script argument, not atomically, so an over-optimistic reservation can slip
through — it is caught at the gate, never sold. The Redis-Lua reservation layer
is also more machinery than a single `SELECT … FOR UPDATE` would be, chosen so
the DB is not the contention point for browse-heavy traffic.

**Anything that moves money is idempotent.** The Stripe webhook records each
`event_id` in `processed_webhook_events` with a unique constraint that is the
real backstop against concurrent double-delivery (the pre-check is just an
optimisation). Order-creating endpoints take an `Idempotency-Key` header: the
same key replays the stored response, the same key with a different body is a
422, an in-flight key is a 409. A payment reference is unique across orders.
Before an order is created, the server recomputes the price through its own
pipeline and confirms the gateway's PaymentIntent is for that exact amount —
the client's claimed `pgStatus` is never trusted. *Trade-off:* an extra table
and a client contract for the key; a gateway round-trip on the checkout path.

**Lost updates surface as 409s, not silent last-write-wins.** `@Version` is on
`Product`, `Coupon` and `Order`; the native stock and coupon UPDATEs bump the
version too, so an entity save that raced them is rejected rather than clobbering
their change. The post-commit side effects of checkout (confirmation email,
notifications, audit) run on a bounded pool *after the transaction commits*, and
the Redis reservation purge is deferred to after-commit as well — a rolled-back
checkout keeps its held stock. *Trade-off:* callers must be prepared to retry a
409; "email may arrive twice" is accepted over "email silently lost" (made
durable by the transactional outbox — see below).

**`open-in-view` is off**, so every read path resolves its data before the
transaction closes. That forces three patterns worth naming: list queries use
JPA **Specifications** (`findAll(spec, pageable)`) for composable filtering;
sort input is checked against a **`SortWhitelist`** allow-list (an unknown
property is a 400, not a 500, and `user.password` can't be used to probe an
ordering); and paginated queries that also need a collection use the **two-phase
id-then-details** fetch (page over IDs in SQL, then fetch the full graph for that
page) because a `JOIN FETCH` plus `Pageable` paginates in memory.

**A crash between "order saved" and "email sent" loses neither.** The
confirmation email is not sent from the checkout transaction; a row is written to
`outbox_event` inside it, and a poller drains the table with `FOR UPDATE SKIP
LOCKED` so several instances can work the same queue without stepping on each
other. A failing event retries with exponential backoff and is dead-lettered
after 8 attempts rather than retried forever. Fiscal invoice numbers rely on the
same commit-or-nothing property from the other side: a per-year counter row is
incremented *inside* the transaction that inserts the invoice, rather than drawn
from a SEQUENCE, because a rolled-back checkout must not consume number 41 and
leave a permanent hole in the year. *Trade-off:* the outbox costs a table, a
poller and at-least-once delivery, so handlers have to be idempotent; the counter
row serialises concurrent issuers on a lock held at the tail of checkout, which
is fine at this store's volume and would move behind the outbox if it were not.

**Every change to stock can be explained afterwards.** `products.quantity` was a
bare mutable number: when it said 3, nothing recorded what sold, what came back,
or what an admin corrected by hand. Every write now goes through one service that
applies the change and appends a signed `stock_movement` row — reason, cause,
resulting balance, actor — via a single
`UPDATE ... WHERE quantity + :delta >= 0 RETURNING quantity`, which decides and
reads the new balance in one statement and is itself the oversell gate. The
service is `Propagation.MANDATORY`: a movement recorded outside a transaction
could outlive the rolled-back order that caused it, so a caller without one fails
loudly. *Trade-off:* the raw UPDATE leaves an already-loaded `Product` stale in
the persistence context, so callers take the new figure from the returned
movement; and the trail is only as good as the discipline of routing every write
through the service, so a scheduled sweep asserts
`SUM(delta) = products.quantity` per product and logs whatever drifted.

**Named patterns already in place:** `PaymentGateway` is a **Strategy** selected
from a registry; the coupon-then-shipping-then-tax pricing pipeline is a
**Chain of Responsibility** ordered by `@Order`, not statement order;
`OrderStatus` is an explicit **state machine** (each status declares its
successors); the order-lifecycle listeners are **Observers** on
`@TransactionalEventListener`.

### In flight this iteration

Not yet merged, each on its own branch: an Art. 15 data export and Art. 17
erasure path for GDPR (see `docs/gdpr.md`); faceted product search with
drill-down counts; and the stock ledger described above.

Money is mid-migration. The `Money` value object — exact `BigDecimal`, scale 2,
HALF_UP — owns the pricing pipeline and everything handed to Stripe, but the
entities still store `double`/`Double`. `Order`/`OrderItem`, then
`Product`/`Cart`, then tax, then the remainder are being moved across one slice
per branch rather than in one sweep, because every slice touches the payment
path.

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
