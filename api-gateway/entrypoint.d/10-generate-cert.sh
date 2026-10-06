#!/bin/sh
# Generate a self-signed certificate at container start (docs/architecture.md §3.2,
# docs/deployment.md §4). Paths come from GATEWAY_TLS_CERT / GATEWAY_TLS_KEY;
# nothing secret is committed to the repository.
set -eu

CERT="${GATEWAY_TLS_CERT:-/etc/nginx/certs/server.crt}"
KEY="${GATEWAY_TLS_KEY:-/etc/nginx/certs/server.key}"

mkdir -p "$(dirname "$CERT")" "$(dirname "$KEY")"

if [ ! -f "$CERT" ] || [ ! -f "$KEY" ]; then
    echo "gateway: generating self-signed certificate at ${CERT}"
    openssl req -x509 -nodes -newkey rsa:2048 -days 365 \
        -keyout "$KEY" -out "$CERT" \
        -subj "/C=RU/O=PullHire/CN=localhost"
fi
