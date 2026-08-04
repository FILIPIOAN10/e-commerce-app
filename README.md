# EcommerceHub: Enterprise Multi-Vendor Platform
 
## Full-Stack E-commerce Solution
Developed a production-grade marketplace using Spring Boot 3.5, React, and Redux. Implemented separate portals for admin, customer, and seller roles with real-time inventory management, order processing, and dashboard analytics. Added low stock threshold alerts with admin/seller notifications and product image gallery support with multiple images per product.
 
## Advanced Security & Authentication
Integrated enterprise-level authentication using Spring Security with JWT tokens, role-based access control (RBAC), and stateless sessions. Implemented OAuth2 social login with GitHub and Google. Added TOTP-based two-factor authentication (2FA) with purpose-scoped challenge tokens to prevent token misuse. Secured secrets using HashiCorp Vault integration, separating credentials from application code.
 
## AI-Powered Semantic Search
Integrated Spring AI with OpenAI embeddings and PostgreSQL pgvector for semantic product search. Products are indexed as vector embeddings combining name, category, tags, and description. Natural language queries like "something to listen music with" return relevant results (Wireless Headphones, Bluetooth Speaker) without exact keyword matching. Includes hybrid search with SQL fallback and admin-triggered reindexing.
 
## Scalable Database & Caching Architecture
Designed complex entity relationships using JPA/Hibernate with PostgreSQL 16 and pgvector. Implemented advanced features including pagination, sorting, and keyword search. Used Redis 7.4 for multi-layer caching (products, categories, search results) with TTL-based eviction and distributed rate limiting on login, signup, payment, and search endpoints.
 
## Payment Processing
Implemented Stripe payment gateway integration with PaymentIntent-based checkout flow. Order placement includes cart-to-order conversion, inventory deduction, and payment status tracking within a single transactional boundary.
 
## Infrastructure & DevOps
Containerized the entire stack using Docker Compose with PostgreSQL + pgvector, Redis, HashiCorp Vault, and Maven-based backend with remote debugging support. Vault-init service automatically injects secrets at startup. Configured health checks and service dependencies for reliable startup ordering.
 
## Tech Stack
 
### Backend
- Java 17, Spring Boot 3.5, Spring Security, Spring AI 1.1
- Spring Data JPA / Hibernate, PostgreSQL 16 + pgvector
- Redis 7.4 (caching & rate limiting)
- HashiCorp Vault (secrets management)
- JWT (jjwt 0.13), OAuth2 (GitHub + Google), TOTP 2FA (GoogleAuth)
- Stripe SDK 31.2, SpringDoc OpenAPI (Swagger UI)
- Maven 3.9
 
### Frontend
- React, Redux, React Router
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
| Rate Limiting | Redis-based distributed rate limiting with configurable rules per endpoint |
| Caching | Redis cache with TTL eviction for products, categories, and search results |
| Stripe Payments | PaymentIntent flow with order and inventory management |
| Vault Integration | Centralized secrets management for JWT, OpenAI, Stripe, OAuth, and DB credentials |
| Admin Dashboard | Analytics overview, product/category management, order tracking, seller management |
| Audit Logging | Request-level audit trail tracking user activity across API endpoints |
| Low Stock Alerts | Threshold-based stock monitoring with sidebar badge notifications for admin and seller roles |
| Product Image Gallery | Multiple images per product with carousel UI, admin/seller multi-file upload, and thumbnail navigation |
 
## Getting Started
 
### Prerequisites
- Docker & Docker Compose
- Node.js 18+ (for frontend)
 
### Backend & Infrastructure
```bash
# Clone the repository
git clone <repo-url>
cd e-commerce-app
 
# Configure environment variables
cp .env.example .env
# Edit .env with your secrets (JWT_SECRET, OPENAI_API_KEY, STRIPE_SECRET_KEY, etc.)
 
# Start all services
docker compose up --build
