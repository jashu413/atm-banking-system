# ── Stage 1: build React app ─────────────────────────────────────────────────
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --prefer-offline

COPY . .

# VITE_API_BASE_URL=/api/v1 → nginx proxies /api to the backend container
ARG VITE_API_BASE_URL=/api/v1
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

RUN npm run build

# ── Stage 2: serve with nginx ─────────────────────────────────────────────────
FROM nginx:1.27-alpine AS runtime

# Create a dedicated non-root user for nginx worker processes.
# The master process must start as root to bind port 80; workers drop privileges.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Grant the non-root user ownership of directories nginx workers write to.
RUN chown -R appuser:appgroup \
      /var/cache/nginx \
      /var/log/nginx \
      /usr/share/nginx/html && \
    touch /var/run/nginx.pid && \
    chown appuser:appgroup /var/run/nginx.pid

COPY --from=build --chown=appuser:appgroup /app/dist /usr/share/nginx/html
COPY --chown=appuser:appgroup nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
