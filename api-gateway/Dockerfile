# api-gateway — single entry point (docs/architecture.md §3.2).
# nginx:1.27-alpine + openssl for the self-signed demo certificate generated at
# container start (no committed secrets, docs/deployment.md §4 / §7.2).
FROM nginx:1.27-alpine

RUN apk add --no-cache openssl
RUN if [ ! -f /usr/lib/nginx/modules/ngx_http_js_module.so ]; then apk add --no-cache nginx-module-njs; fi

COPY conf.d/default.conf /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/nginx.conf
COPY njs/json429.js /etc/nginx/njs/json429.js
COPY entrypoint.d/10-generate-cert.sh /docker-entrypoint.d/10-generate-cert.sh
RUN chmod +x /docker-entrypoint.d/10-generate-cert.sh

COPY docs /usr/share/nginx/html/docs

EXPOSE 80 443
