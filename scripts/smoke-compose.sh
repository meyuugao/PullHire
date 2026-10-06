#!/usr/bin/env bash
# S0 smoke (docs/architecture.md §7.3, docs/deployment.md §5):
#   compose up -> every stub /healthz = 200 -> gateway https://localhost and /docs.
# No seed, no e2e in S0.
set -euo pipefail

PORTS=(8081 8082 8083 8084 8085 8086 8087 8088)
NAMES=(auth candidate fsp assessment selection employer outreach notification)

echo "==> docker compose up -d --build"
docker compose up -d --build

echo "==> waiting for stub health (timeout 300s)"
deadline=$((SECONDS + 300))
for i in "${!PORTS[@]}"; do
  port="${PORTS[$i]}"
  name="${NAMES[$i]}"
  until curl -fsS "http://localhost:${port}/healthz" >/dev/null 2>&1; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "TIMEOUT: ${name}-service /healthz on :${port} never returned 200"
      docker compose ps
      exit 1
    fi
    sleep 3
  done
  echo "  ${name}-service  http://localhost:${port}/healthz -> 200"
done

echo "==> gateway checks"
curl -kfsSL "https://localhost/" -o /dev/null -w "  https://localhost/      -> %{http_code}\n"
curl -kfsSL "https://localhost/docs/" -o /dev/null -w "  https://localhost/docs/ -> %{http_code}\n"

echo "SMOKE OK"
