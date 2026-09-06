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

**Money is a decimal, and the database agrees.** Prices and totals were
`double` throughout — a binary float standing in for a count of cents, which
cannot hold 84.99 exactly. The `Money` value object (`BigDecimal`, scale 2,
HALF_UP) owns every arithmetic step of the pricing pipeline, and an order now
stores `NUMERIC(12,2)` rather than `DOUBLE PRECISION`, so that exactness survives
the last hop into the database instead of being widened away there. The
catalogue and the cart followed, so a price is exact from the product row it
starts on through to the amount confirmed with Stripe — and `SUM(total_amount)`
behind the revenue reports is exact rather than nearly right. Two hand-rolled
roundings went with it: `Math.round(x * 100) / 100` in the bundle pricing and
`price - (discount * 0.01 * price)` for a special price, both replaced by
`Money.percentage`, which rounds to the cent at every step so the figures agree
with each other by construction. *Trade-off:* the conversion is going across the
codebase one slice per branch rather than in one sweep, because every slice
touches the payment path; what is left of the old world is visible as explicit
`toDouble()` calls, and each one marks a boundary a later slice removes.

**An order now carries VAT, and the pipeline was already shaped for it.**
`PriceLineType.TAX` and `PriceBreakdown.taxTotal()` had been sitting unused since
the pricing pipeline was built — a slot with no rule to fill it, so every order's
`total_amount` was a pre-tax figure and the amount confirmed with Stripe had no
tax in it. `VatRule` fills the slot, last in the `@Order` chain: it reads the
running total *after* the coupon and shipping rules have run, so VAT lands on the
discounted price the customer actually pays and (by EU practice, and a config
flag) on the carriage too. The rate is not an app constant the way the shipping
bands are — it is a legal figure that moves by country and by budget — so it is
externalised as `app.tax.rates.<ISO>` with a default, resolved from the delivery
address; a market the store does not charge tax in sets `app.tax.enabled=false`
and the line disappears rather than showing as €0.00. The amount is persisted in
its own `NUMERIC(12,2)` column beside the other money, and V29 backfills existing
orders with `0.00` — correct, not merely convenient, because those orders were
genuinely priced and charged without tax. *Trade-off:* the taxable base is the
post-discount total including shipping, which is the common EU case but not
universal — a jurisdiction that zero-rates shipping or taxes the pre-discount
amount would need the rule to grow a second mode; and the rate table is a flat
country map with no notion of reduced rates per product category, which this
catalogue does not need yet.

**The last four `Double`s are gone, and the migration is closed.** After the
order, product and cart slices, four columns still stood on `DOUBLE PRECISION`: a
bundle's and a promo campaign's percentage off, a subscription plan's price, and
a return's refund amount. Two are amounts and two are percentages, but V25 had
already settled that both kinds travel as `NUMERIC(12,2)` here — it converted
`products.discount`, a percentage, on the same reasoning that a percentage
multiplied into a price must not reintroduce the float error the price just shed,
and that `12.50%` has to be storable as `12.50`. So V30 converts all four the
same way, `ROUND(...)` in every `USING` clause as a no-op for real data and a net
for legacy float noise. The conversion let two seams close: `ReturnServiceImpl`
had an `asLegacyDouble(BigDecimal)` bridge and a `getOrderTotal` that narrowed
the order's total back to `double` purely to feed the refund field — both now
deleted, because the refund is copied straight across as the `BigDecimal` it
always was — and `SubscriptionServiceImpl` sent Stripe `(long)(amount * 100)`,
the exact float-cents bug `Money.toCents()` exists to prevent, now
`Money.of(amount).toCents()`. `Money` grew a `percentage(BigDecimal)` overload so
the discount rate no longer round-trips through `double` on its way into the
arithmetic. *Trade-off:* none of consequence — this slice is a type change with
no behaviour change, which is why it was kept for last, after every slice that
could actually move a number had landed and been checked.

**An approved return refunds the customer by itself, exactly once.** Marking a
return refunded only ever flipped the order status — the money was refunded by
hand in the Stripe dashboard, and the `charge.refunded` webhook was the sole
thing that ever reacted to a refund. Now the "mark refunded" transaction writes
a `refunds` row and, in the same commit, an outbox event; the dispatcher's
handler issues the Stripe refund and drives the row to `SUCCEEDED`. Three things
make a double refund unrepresentable: a partial unique index on `return_id` so a
second `markAsRefunded` (a double-click, the delivered-tracking sweep firing
twice) is rejected before any Stripe call; an `Idempotency-Key` of
`refund:{id}` on the Stripe call so a redelivered outbox event gets the original
refund back rather than a second one; and a partial unique index on
`stripe_refund_id` so the outbox path and a refund made straight in the
dashboard cannot both record the same Stripe refund — the `charge.refunded`
webhook reconciles onto the row the other path already wrote. A transient Stripe
error is rethrown so the outbox backs off and retries; a permanent rejection
(already refunded, not refundable) marks the row `FAILED` and raises an admin
notification rather than looping. *Trade-off:* the order still moves to
`Refunded` the moment the admin clicks — that is their assertion and it is what
happens in all but the rare permanent-failure case — so the `refunds` row, not
the order status, is the record of whether the money actually moved. Cash-on-
delivery returns keep the old manual path: there is no charge to reverse.

**Named patterns already in place:** `PaymentGateway` is a **Strategy** selected
from a registry; the coupon-then-shipping-then-tax pricing pipeline is a
**Chain of Responsibility** ordered by `@Order`, not statement order;
`OrderStatus` is an explicit **state machine** (each status declares its
successors); the order-lifecycle listeners are **Observers** on
`@TransactionalEventListener`.

**The database owns the invariants the entity mapping only claims.** A code
health audit turned up three defects that shared a shape: something the model
asserted, that nothing enforced. `User.cart` is mapped `@OneToOne`, but
`carts.user_id` carried a plain FK — no unique constraint — and cart creation
read-then-inserted, so two concurrent first touches (a double-clicked *Add to
cart*, or the SPA loading the cart and adding an item at once) both saw nothing
and both inserted. `findCartByEmail` returns a single `Cart`, so from that point
every cart request for that user threw on a two-row result, permanently, until
someone deleted a row by hand. V26 collapses existing duplicates — merging the
loser's items into the keeper rather than dropping them — and adds the unique
index; creation now goes through `INSERT … ON CONFLICT DO NOTHING` and re-reads.
*Trade-off:* an upsert rather than `save()`-and-catch, because a duplicate-key
violation raised inside the caller's transaction marks it rollback-only —
catching it trades the duplicate cart for an `UnexpectedRollbackException` at
commit, which is not an improvement.

**A scheduled job that only pushes state one way is a leak.** `applyActiveCampaigns`
wrote a campaign's discount onto every one of its products once a minute, and
had no path back: when `end_time` passed the campaign simply stopped being
selected, so the promotional price stayed on the product forever — invisibly,
because the campaign no longer showed as active anywhere in the admin UI.
Deleting a campaign did the same thing, and deleted the only rows that could
have undone it. The sweep is now symmetric — `promo_campaign_products.original_discount`
remembers what to restore, `promo_campaigns.applied` records what the sweep has
actually done — so a campaign is applied once and reverted once instead of
rewritten every tick. That also removes the incidental cost: the old pass was
1 + N queries and N updates per campaign, bumping `@Version` and churning WAL
for values that had not changed, where a steady state is now two SELECTs that
return nothing. `fixedDelay` replaces `fixedRate` and it takes the same advisory
lock as the other sweeps, so it cannot overlap itself or collide across
instances. The schedule itself moved out to a `PromoCampaignSweepJob`, matching
`AbandonedCartReminderJob` and `StockReconciliationJob`: scheduling is a
deployment concern, and a test that wants to observe one pass should not have to
defeat a timer to do it — `app.promo.enabled=false` in the test profile, and the
service driven directly. *Known limit:* campaigns already running when V27 lands
have no recorded original discount — it was overwritten before the column
existed — so they revert to no discount rather than to whatever preceded them.

**One key, one writer.** `state.products` held a single `pagination` object that
`productCatalogReducer`, `categoryReducer` and `lowStockReducer` all wrote, and
the merge in `ProductReducer` spreads the category slice last. `/products` fires
the product and category queries in parallel, so whenever the smaller categories
response landed second it overwrote the catalog's page count with its own — a
12-page catalog rendering as one page, intermittently, which is why it survived
so long. Each list now owns its own paginator. The same merge also returned a
fresh object for every action dispatched anywhere in the app, so `useSelector`'s
reference check re-rendered every consumer of `state.products` on unrelated
traffic; it now hands back the same reference when nothing moved.

**Configuration that only resolves in development is a production outage.**
`app.password-reset.frontend-url` was hardcoded to `localhost:5173` with no
placeholder, and the prod compose sets `FRONTEND_URL` — which relaxed binding
maps to `frontend.url`, not to that key. `EmailService` used it for four links:
password reset, email verification and both order-tracking mails. All four
pointed at localhost in production, and nothing logged an error because the mail
sent fine. The duplicate property is gone. Stripe had the mirror image: the key
was read as `stripe.secret.key` by `StripeServiceImpl` and `stripe.api.key` by
`SubscriptionServiceImpl`, and only the former is set in production, so
subscriptions failed with "Stripe API key is not configured" while checkout
worked. One property name, one environment variable, end to end.

**Monitoring that cannot be scraped is not monitoring.** `/actuator/**` required
`ADMIN`, and Prometheus scrapes with no JWT, so every metric was blackholed and
every rule in `alert-rules.yml` sat un-evaluated — findable only during the first
incident it was meant to catch. The obvious repair, opening `/actuator/prometheus`,
is the wrong one, and the codebase says so: `IdorAuthorizationTest` asserts that
every actuator endpoint bar health and info stays closed to anonymous and to a
plain user. So the scraper gets a credential instead of the endpoint being
opened — a second `SecurityFilterChain`, ordered ahead of the main one and
scoped to `/actuator/**`, that accepts HTTP Basic against a single in-memory
`METRICS` account, alongside the JWT filter so an `ADMIN` reaching actuator
through the app's own cookie still works. *Trade-off:* the account is in memory
and not in `users`, because a scrape credential is not a person and must not be
able to sign in to the application; the cost is that it is configured rather
than managed, and with no password set no account exists at all, so an
unconfigured deployment fails closed rather than open. The stack itself had
never been runnable either — `monitoring/` held a scrape config, alert rules and
a dashboard that no compose file started — so Prometheus and Grafana are now
services under a `monitoring` profile, with the scrape password materialised
from the environment as a file so no credential is committed.

**Validation belongs at the edge, and an annotation nobody reads is not
validation.** Nine `@RequestBody` parameters carried no `@Valid`, so `CouponDTO`'s
`@Min(1) @Max(100)` had been sitting there unenforced — an admin could store a
500% coupon and the pricing pipeline would take more off the running total than
the order was worth. The DTOs with no constraints at all were worse, because the
missing value got dereferenced anyway: a null `addressId` reached
`findById(null)` and a null campaign `startTime` reached `LocalDateTime.parse`,
both surfacing as 500s on input the *client* got wrong. Every constraint added
corresponds to a dereference that throws or a number that breaks the arithmetic,
rather than to a general wish for tidier input. `HandlerMethodValidationException`
— what `List<@Valid CartItemDTO>` raises — had no case in the advice and fell
through the catch-all as a 500; it now answers 400 naming the failing element's
index.

**A caught exception that changes nothing is a lie told to the user.** Four of
`EmailService`'s sends already threw so the outbox would retry them. The other
five caught, logged and returned normally, every one called straight from a
request handler — so the endpoint above answered "check your email" for a message
that was never sent, and the user simply waited. The contact form showed how
invisible that was: its controller already had a "failed to send, please try
again" branch that could never run. *Trade-off:* signup is the one place the
failure is still swallowed. `AuthServiceImpl` is `@Transactional`, so letting it
out would roll the registration back and the account would silently not exist
while the user was told signup failed. An unverified account is a state the
domain already models, so it is kept and the response says the mail did not go.

**Neither JPA nor Postgres indexes the child side of a foreign key.** A
`@JoinColumn` generates no index and `REFERENCES` indexes only the parent's
primary key, so seven FK columns were sequential scans — worst inside the
cascade behind deleting a product, since `bundle_products.product_id` is
`ON DELETE CASCADE` and gets scanned during the delete itself. Rather than a
migration listing seven names and a test asserting those seven exist,
`ForeignKeyIndexTest` asserts the invariant over `pg_constraint`: no foreign key
anywhere lacks an index leading with its own column. The failure it guards
against is the *next* `@JoinColumn` added without one, which a named list would
happily pass. Two of the seven were ones the audit itself had missed; the query
found them.

**The seller nobody renders.** `Product.user` was an EAGER `@ManyToOne` and
`User.roles` is an EAGER `@ManyToMany`, so a page of twenty products issued a
select per distinct seller and then a select for each of those sellers' roles —
roughly forty queries for a list that needs one, each hydrating a whole `User`
including its password hash for a DTO with no seller field. Now lazy, which is
safe on three counts: ModelMapper has no target property to traverse into, the
only readers call `getUserId()` and a proxy answers that without a query, and
`Order.withDetails` already named the attribute so the order path is unchanged.
*Deliberately partial:* `Product.category` stays eager, because the mapper reads
`getCategoryName()` and converting it means an entity graph on every finder
across the whole catalogue surface, for a cost bounded by distinct categories on
a page rather than by rows. The seller was the part that scaled.

**CSRF was waived for `/api/auth/**` as a whole**, which swept up `POST /signout`
and both device-revocation routes — state changes made by someone who already
holds a session, which is the thing CSRF exists to stop. Signout takes a
cross-site form post with no preflight to stand in the way, so any page could
sign a visitor out, or drop every session they had. Now waived only for the
routes reached before a token exists. Verified against the running stack rather
than reasoned about, because this is the change most likely to break a real
login: a normal GET issues the cookie, signin and forgot-password still pass
without a token, signout passes with one and is refused without.

### In flight this iteration

The money migration is complete — all five slices are on `main`, and no column or
field in the codebase holds money as a binary float.

Current work: **automated refunds** (`feat/automated-refunds`, `V31`). An
approved return now issues its own Stripe refund through the outbox instead of
leaving it to be done by hand in the dashboard — see the Engineering-decisions
entry above.

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
