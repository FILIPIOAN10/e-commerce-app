# ── Build stage ──
FROM node:22-alpine AS builder

ARG VITE_BACK_END_URL=/api
ENV VITE_BACK_END_URL=${VITE_BACK_END_URL}

WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ── Runtime stage ──
FROM nginx:alpine

# See ecom-backend/Dockerfile for why this is here: the same Trivy gate found
# 7 HIGH in libuuid/util-linux (2.42.1-r0), all fixed upstream in the same
# Alpine 3.24 branch already (2.42.3-r0/r1) — apk upgrade tracks that branch's
# current fixes rather than whatever the base layer had cached.
RUN apk update && apk upgrade --no-cache

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /build/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
