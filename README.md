# EcommerceHub: Enterprise Multi-Vendor Platform
 
## Full-Stack E-commerce Solution
Developed a production-grade marketplace using Spring Boot 3.5, React, and Redux. Implemented separate portals for admin, customer, and seller roles with real-time inventory management, order processing, and dashboard analytics.
 
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
<img width="1870" height="976" alt="1" src="https://github.com/user-attachments/assets/8784c475-a6cb-4c38-8702-33c1571d47b2" />
<img width="1893" height="934" alt="2" src="https://github.com/user-attachments/assets/7b551d57-806b-4518-ab10-d759431ae4da" />
<img width="1884" height="780" alt="3" src="https://github.com/user-attachments/assets/92897dc7-722f-47fa-a5b5-250dcc0fb29a" />
<img width="1871" height="923" alt="4" src="https://github.com/user-attachments/assets/c0179639-98a0-4803-bd8b-69b8ba2a5b47" />
<img width="1734" height="904" alt="5" src="https://github.com/user-attachments/assets/d2829efb-655c-4f84-84cb-b2932e1c70a5" />
<img width="1881" height="936" alt="6" src="https://github.com/user-attachments/assets/6daa7f48-009b-4f9d-b51c-1a0d2d058bcd" />
<img width="1174" height="941" alt="7" src="https://github.com/user-attachments/assets/e5718b49-738e-4843-90e4-181aaf3fe1ec" />
<img width="1396" height="699" alt="8" src="https://github.com/user-attachments/assets/28f73dd2-db90-4ea5-b416-384fd1eca762" />
<img width="1371" height="938" alt="9" src="https://github.com/user-attachments/assets/d7f7a35b-a4ef-4d13-9373-b7cea6c4dad5" />
<img width="1397" height="962" alt="10" src="https://github.com/user-attachments/assets/feeb912a-ff1e-49cb-8a24-a37b24bb594b" />
<img width="1908" height="994" alt="11" src="https://github.com/user-attachments/assets/47c13b4b-45f6-45df-8c5c-43b526947fe3" />
<img width="1904" height="760" alt="12" src="https://github.com/user-attachments/assets/095cf000-7c47-49c4-b76e-1b2c26e06060" />
<img width="552" height="592" alt="s1" src="https://github.com/user-attachments/assets/3694632a-1fd7-49ce-b371-c866b89868a8" />
<img width="1861" height="892" alt="image" src="https://github.com/user-attachments/assets/8241c476-0510-4b8b-b293-15e03bbc8761" />
