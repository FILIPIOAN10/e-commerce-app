# Production Deployment Guide

This guide walks through deploying the **EcommerceHub** stack on a VPS using the production Docker Compose file (`docker-compose.prod.yml`). It covers both the self-hosted Postgres path and the managed Postgres path.

---

## What the production stack looks like

```
internet ── nginx ──┬── backend (Spring Boot)
                    ├── frontend (static Vite build)
                    ├── redis (password-protected)
                    └── postgres (optional self-hosted)
```

- `nginx` is the public edge. It serves the frontend, proxies `/api/*`, `/images/*` and `/actuator/health` to the backend, and blocks the rest of `/actuator/*`.
- `backend` runs with `SPRING_PROFILES_ACTIVE=prod` and uses environment variables for all credentials.
- `frontend` is a static nginx build. `VITE_BACK_END_URL=/api` is set at build time so the browser calls the same origin.
- `redis` is protected with `requirepass` and persists data with AOF.
- `postgres` is optional; you can replace it with a managed Postgres (AWS RDS, Supabase, DigitalOcean, etc.).

---

## 1. Provision a VPS

- Ubuntu 22.04/24.04 LTS
- 2 vCPU, 4 GB RAM minimum (4 vCPU/8 GB recommended)
- Docker 24+ and Docker Compose v2 installed
- SSH access and a non-root user in the `docker` group
- A firewall allowing ports 22, 80 and 443

---

## 2. Create an `.env` file

Copy the example and fill in real values:

```bash
cp .env.example .env
```

Required variables for `docker-compose.prod.yml`:

| Variable | Purpose |
| --- | --- |
| `POSTGRES_DB` | Database name |
| `POSTGRES_USER` | Database user |
| `POSTGRES_PASSWORD` | Strong database password |
| `REDIS_PASSWORD` | Strong Redis password |
| `JWT_SECRET` | 256-bit base64 secret (e.g. `openssl rand -base64 32`) |
| `BACKEND_IMAGE` | Image tag, e.g. `ghcr.io/owner/ecommerce-backend:v1.0.0` |
| `FRONTEND_IMAGE` | Image tag, e.g. `ghcr.io/owner/ecommerce-frontend:v1.0.0` |
| `VITE_BACK_END_URL` | Leave as `/api` when nginx is in front |
| `STRIPE_SECRET_KEY` | Stripe secret key |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook endpoint secret |
| `SPRING_MAIL_*` | SMTP host, port, username and password |

Optional but recommended:

| Variable | Purpose |
| --- | --- |
| `JWT_EXPIRATION_MS` | Access token lifetime (default 15 min) |
| `JWT_REFRESH_EXPIRATION_MS` | Refresh token lifetime (default 7 days) |
| `FRONTEND_URL` | Public URL, e.g. `https://yourdomain.com` |
| `IMAGE_BASE_URL` | Public image URL, e.g. `https://yourdomain.com/images` |

---

## 3. Using managed Postgres

If you want a managed database instead of the local `postgres` service:

1. Create the database and user in your managed provider.
2. Set `SPRING_DATASOURCE_URL` to the JDBC URL, for example:
   ```
   SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/<db>?ssl=true
   ```
3. Comment out or remove the `postgres` service from `docker-compose.prod.yml`.
4. Remove the `postgres` dependency from the `backend` service.

---

## 4. SSL / HTTPS

The provided `nginx/nginx.prod.conf` listens on port 80. For HTTPS, place a reverse proxy in front (Cloudflare, AWS ALB, Caddy or a separate nginx with Let's Encrypt) and forward traffic to `localhost:80`.

A minimal Caddy option:

```Caddyfile
yourdomain.com {
    reverse_proxy localhost:80
}
```

---

## 5. Start the stack

```bash
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

Verify everything is up:

```bash
docker compose -f docker-compose.prod.yml ps
curl -s http://localhost:8080/actuator/health
```

---

## 6. CI/CD deployment

The `deploy.yml` GitHub Actions workflow:

1. Builds and pushes images to GHCR on every version tag (`v*`).
2. SSHes to the VPS, updates the `BACKEND_IMAGE` and `FRONTEND_IMAGE` values in `.env`.
3. Pulls and starts the new images.
4. Polls `/actuator/health` for up to 2 minutes.
5. If the health check fails, it restores the previous `.env` and rolls back to the previous images.

Required repository secrets:

- `DEPLOY_HOST`
- `DEPLOY_USER`
- `DEPLOY_SSH_KEY`
- Optional: `DEPLOY_PATH` (defaults to `/opt/ecommerce`)

---

## 7. Operations

### View logs

```bash
docker compose -f docker-compose.prod.yml logs -f backend
```

### Back up the database

```bash
docker compose -f docker-compose.prod.yml exec -T postgres pg_dump -U $POSTGRES_USER -d $POSTGRES_DB > backup.sql
```

### Restart a service

```bash
docker compose -f docker-compose.prod.yml restart backend
```

### Rolling back manually

If the automatic rollback does not trigger, revert the image tags in `.env` and run:

```bash
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d --remove-orphans
```

---

## 8. Security checklist

- [ ] `.env` is never committed; it is listed in `.gitignore`.
- [ ] `JWT_SECRET` is at least 32 bytes, base64 encoded, and unique per environment.
- [ ] `REDIS_PASSWORD` and `POSTGRES_PASSWORD` are generated with a password manager.
- [ ] Stripe keys are live keys only in the production `.env`.
- [ ] `management.endpoints.web.exposure` does not expose sensitive endpoints to the internet.
- [ ] The host firewall blocks port 8080 (backend) and 6379 (Redis) from public access.
- [ ] SSH is key-only and root login is disabled.
