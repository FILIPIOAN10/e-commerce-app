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

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /build/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
