# ── Build: compila Angular en producción ──────────────────────────────────────────────────────
FROM node:22-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# set-env.js genera environment.prod.ts con estos valores.
ARG API_URL=/samec-api
ARG RECAPTCHA_SITE_KEY=
ARG DOMINIO_CORREO=essalud.gob.pe
ENV API_URL=${API_URL} RECAPTCHA_SITE_KEY=${RECAPTCHA_SITE_KEY} DOMINIO_CORREO=${DOMINIO_CORREO}
RUN npm run build:prod

# ── Runtime: Nginx sirve el build y orquesta el tráfico hacia el core (ver ../nginx/default.conf) ──
FROM nginx:stable-alpine
COPY --from=build /build/dist/same_angular/browser /usr/share/nginx/html
EXPOSE 80
