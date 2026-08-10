# EcommerceHub: Enterprise Multi-Vendor Platform

![JaCoCo Coverage](https://img.shields.io/badge/coverage-JaCoCo%20report-blue)
 
## Full-Stack E-commerce Solution
Built a production-grade marketplace using Spring Boot 3.5, React, and Redux. Implemented separate portals for admin, customer, and seller roles with real-time inventory management, order processing, and dashboard analytics. Added low stock threshold alerts with admin/seller notifications and product image gallery support with multiple images per product.
 
## Advanced Security & Authentication
Integrated enterprise-level authentication using Spring Security with JWT tokens, role-based access control (RBAC), and stateless sessions. Implemented OAuth2 social login with GitHub and Google. Added TOTP-based two-factor authentication (2FA) with purpose-scoped challenge tokens to prevent token misuse. Enabled CSRF protection with CookieCsrfTokenRepository for state-changing operations. Secured secrets using HashiCorp Vault integration, separating credentials from application code. Custom exception handling with @ControllerAdvice for structured error responses.
 
## AI-Powered Semantic Search & Recommendations
Integrated Spring AI with OpenAI embeddings and PostgreSQL pgvector for semantic product search. Products are indexed as vector embeddings combining name, category, tags, and description. Natural language queries like "something to listen music with" return relevant results (Wireless Headphones, Bluetooth Speaker) without exact keyword matching. Includes hybrid search with SQL fallback and admin-triggered reindexing.

Added AI-powered product recommendations using the same vector store:
- **"Recommended for You"** on the Home page — combines recently viewed products and past order history to build a semantic query, then returns similar products via cosine similarity. Falls back to top-selling products when no user history exists.
- **"Similar Products"** on the product detail modal — uses the current product's name, description, and tags to find related items via vector similarity search. Falls back to same-category products when semantic search is unavailable.
 
## Scalable Database & Caching Architecture
Designed complex entity relationships using JPA/Hibernate with PostgreSQL 16 and pgvector. Implemented advanced features including pagination, sorting, and keyword search. Used Redis 7.4 for multi-layer caching (products, categories, search results) with TTL-based eviction and distributed rate limiting on login, signup, payment, and search endpoints. Recently viewed products tracked per user in Redis lists.
 
## Payment Processing
Implemented Stripe payment gateway integration with PaymentIntent-based checkout flow. Order placement includes cart-to-order conversion, inventory deduction, and payment status tracking within a single transactional boundary.
 
## Frontend Performance
Implemented code-splitting with React.lazy and Suspense for all route-level components, reducing initial bundle size and enabling on-demand chunk loading. Eliminated console.log statements in production code and fixed React list key warnings for improved rendering performance.
 
## Infrastructure & DevOps
Containerized the entire stack using Docker Compose with PostgreSQL + pgvector, Redis, HashiCorp Vault, and Maven-based backend with remote debugging support. Vault-init service automatically injects secrets at startup. Configured health checks and service dependencies for reliable startup ordering.
 
## Tech Stack
 
### Backend
- Java 17, Spring Boot 3.5, Spring Security, Spring AI 1.1
- Spring Data JPA / Hibernate, PostgreSQL 16 + pgvector
- Flyway (versioned schema migrations)
- Redis 7.4 (caching, rate limiting & recently viewed tracking)
- HashiCorp Vault (secrets management)
- JWT (jjwt 0.13), OAuth2 (GitHub + Google), TOTP 2FA (GoogleAuth)
- Stripe SDK 31.2, SpringDoc OpenAPI (Swagger UI)
- Maven 3.9
 
### Frontend
- React, Redux, React Router
- React.lazy + Suspense (code-splitting)
- Tailwind CSS, Material UI (DataGrid)
- Vite, Axios
 
### Infrastructure
- Docker Compose
- PostgreSQL 16 + pgvector extension
- Redis 7.4 Alpine
- HashiCorp Vault
- Maven Eclipse Temurin 17
 
## Key Features
 
| Feature | Description |
|---|---|
| Multi-role RBAC | Admin, Seller, Customer portals with route-level and API-level authorization |
| OAuth2 Login | GitHub and Google social authentication with automatic user provisioning |
| 2FA / TOTP | Time-based one-time passwords with purpose-scoped JWT challenge tokens |
| Semantic Search | OpenAI embeddings + pgvector similarity search with hybrid SQL fallback |
| AI Recommendations | "Recommended for You" based on browsing history + orders; "Similar Products" via cosine similarity |
| Rate Limiting | Redis-based distributed rate limiting with configurable rules per endpoint |
| Caching | Redis cache with TTL eviction for products, categories, and search results |
| Stripe Payments | PaymentIntent flow with order and inventory management |
| Vault Integration | Centralized secrets management for JWT, OpenAI, Stripe, OAuth, mail, and DB credentials — zero secrets in version control |
| Flyway Migrations | Versioned, repeatable schema evolution with `ddl-auto=validate` drift detection |
| CSRF Protection | CookieCsrfTokenRepository with SPA-friendly token handling |
| Admin Dashboard | Analytics overview, product/category management, order tracking, seller management |
| Audit Logging | Request-level audit trail tracking user activity across API endpoints |
| Low Stock Alerts | Threshold-based stock monitoring with sidebar badge notifications for admin and seller roles |
| Product Image Gallery | Multiple images per product with carousel UI, admin/seller multi-file upload, and thumbnail navigation |
| Code Splitting | React.lazy + Suspense for route-level lazy loading and reduced initial bundle |
| Recently Viewed | Per-user product view tracking in Redis with Home page display |
| Coupon System | Admin-managed discount coupons with validation, expiry, and usage limits |
| Reviews & Ratings | Per-product reviews with rating, comment, and pagination |
| Wishlist | Per-user product wishlist with add/remove and paginated view |
 
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

# ─── Test Coverage (JaCoCo) ───
# Run tests and generate coverage report:
cd ecom-backend && ./mvnw test
# Open the HTML report:
# target/site/jacoco/index.html
# The verify phase enforces >= 60% line coverage on the service layer:
./mvnw verify
```

`docker-compose.yml` contains **no secrets**. Every credential is read from the
git-ignored `.env`, forwarded into HashiCorp Vault by `scripts/vault-init.sh`, and
resolved by Spring Cloud Vault at startup. Required variables have no fallback
defaults, so Compose fails fast with a clear message if one is missing.

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
