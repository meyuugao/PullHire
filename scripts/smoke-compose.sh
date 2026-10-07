#!/usr/bin/env bash
# S0 smoke (docs/architecture.md §7.3, docs/deployment.md §5):
#   compose up -> every stub /healthz = 200 -> gateway https://localhost and /docs.
# No seed, no e2e in S0.
# S2 adds: /metrics + /readyz on all 8 services, a real user.registered flow,
#          correlation_id propagation, duplicate-delivery idempotency and a
#          broker-outage no-loss check.
set -euo pipefail

PORTS=(8081 8082 8083 8084 8085 8086 8087 8088)
NAMES=(auth candidate fsp assessment selection employer outreach notification)

GATEWAY="https://localhost"
MAILPIT_API="http://localhost:8025/api/v1/messages"
RABBIT_API="http://localhost:15672/api"

# --- config resolution (.env wins over the compose defaults) -----------------
dotenv() { # key default
  local key="$1" default="$2" line val
  if [ -f .env ]; then
    line="$(grep -E "^[[:space:]]*${key}=" .env | tail -n1 || true)"
    if [ -n "$line" ]; then
      val="${line#*=}"
      val="${val%\"}"; val="${val#\"}"
      val="${val%\'}"; val="${val#\'}"
      printf '%s' "$val"
      return 0
    fi
  fi
  printf '%s' "$default"
}

PG_USER="$(dotenv POSTGRES_SUPERUSER pullhire_admin)"
PG_DB="$(dotenv POSTGRES_DB pullhire)"
RABBIT_USER="$(dotenv RABBITMQ_DEFAULT_USER pullhire)"
RABBIT_PASS="$(dotenv RABBITMQ_DEFAULT_PASS change-me-rabbitmq)"

pg_value() { # sql -> single trimmed scalar
  docker compose exec -T postgres psql -U "$PG_USER" -d "$PG_DB" -tAc "$1" 2>/dev/null | tr -d '\r[:space:]'
}

register_user() { # email corr -> HTTP status code
  local email="$1" corr="$2" body
  body="$(printf '{"email":"%s","password":"Sm0ke-Test-1","role":"candidate"}' "$email")"
  curl -k -s -o /dev/null -w '%{http_code}' -X POST "$GATEWAY/auth/register" \
    -H 'Content-Type: application/json' -H "X-Request-Id: $corr" -d "$body" || true
}

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

# =============================================================================
# S2 checks
# =============================================================================
echo "==> S2 /metrics + /readyz on all 8 business services"
for i in "${!PORTS[@]}"; do
  port="${PORTS[$i]}"
  name="${NAMES[$i]}"
  metrics="$(curl -k -s -o /dev/null -w '%{http_code}' "http://localhost:${port}/metrics" || true)"
  ready="$(curl -k -s -o /dev/null -w '%{http_code}' "http://localhost:${port}/readyz" || true)"
  if [ "$metrics" = "200" ] && [ "$ready" = "200" ]; then
    echo "  ${name}-service  /metrics -> 200  /readyz -> 200"
  else
    echo "FAIL: ${name}-service :${port} -> /metrics ${metrics}, /readyz ${ready} (expected 200/200)"
    docker compose ps
    exit 1
  fi
done

# Direct DB access is the most precise way to observe notification_log; Mailpit
# is the fallback when psql cannot be reached.
have_psql=0
if docker compose exec -T postgres psql -U "$PG_USER" -d "$PG_DB" -tAc 'SELECT 1' >/dev/null 2>&1; then
  have_psql=1
fi

notification_effect() { # email -> count
  if [ "$have_psql" = "1" ]; then
    docker compose exec -T postgres psql -U "$PG_USER" -d "$PG_DB" -tAc \
      "SELECT count(*) FROM notification.notification_log WHERE recipient_email = '$1'" 2>/dev/null \
      | tr -d '[:space:]'
  else
    { curl -fsS "$MAILPIT_API" 2>/dev/null | grep -oF "$1" || true; } | wc -l | tr -d ' '
  fi
}

echo "==> S2 real user.registered flow via gateway"
CORR_ID="smoke-$(date +%s)-$$"
REG_EMAIL="smoke.${CORR_ID}@pullhire.local"
reg_code="$(register_user "$REG_EMAIL" "$CORR_ID")"
if [ "$reg_code" != "201" ]; then
  echo "FAIL: POST /auth/register returned ${reg_code} (expected 201) for ${REG_EMAIL}"
  exit 1
fi
echo "  POST /auth/register -> 201 for ${REG_EMAIL} (X-Request-Id: ${CORR_ID})"

if [ "$have_psql" != "1" ] && ! curl -fsS "$MAILPIT_API" >/dev/null 2>&1; then
  echo "  SKIP notification effect: psql and Mailpit API both unavailable"
else
  effect=0
  deadline=$((SECONDS + 90))
  while [ "$SECONDS" -lt "$deadline" ]; do
    count="$(notification_effect "$REG_EMAIL")"
    case "$count" in ''|*[!0-9]*) count=0 ;; esac
    if [ "$count" -gt 0 ]; then
      effect=1
      break
    fi
    sleep 3
  done
  if [ "$effect" = "1" ]; then
    echo "  notification side observed for ${REG_EMAIL}"
  else
    echo "FAIL: no notification_log row / Mailpit message for ${REG_EMAIL} within 90s"
    exit 1
  fi
fi

echo "==> S2 correlation_id in auth + notification logs (exit criterion 5)"
sleep 5
for svc in auth-service notification-service; do
  if docker compose logs --no-color "$svc" 2>/dev/null | grep -qF "$CORR_ID"; then
    echo "  ${svc}: correlation_id ${CORR_ID} found"
  else
    echo "FAIL: correlation_id ${CORR_ID} not found in ${svc} logs"
    exit 1
  fi
done

echo "==> S2 duplicate delivery idempotency"
if ! curl -fsS -u "${RABBIT_USER}:${RABBIT_PASS}" "$RABBIT_API/overview" >/dev/null 2>&1; then
  echo "  SKIP: RabbitMQ management API unavailable at ${RABBIT_API}"
elif [ "$have_psql" != "1" ]; then
  echo "  SKIP: psql unavailable, cannot inspect notification_log for duplicates"
else
  DUP_EVENT_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"
  DUP_EMAIL="dup.${DUP_EVENT_ID}@pullhire.local"
  DUP_ENVELOPE="$(python3 - "$DUP_EVENT_ID" "$DUP_EMAIL" <<'PY'
import datetime, json, sys
event_id, email = sys.argv[1], sys.argv[2]
print(json.dumps({
    "event_id": event_id,
    "type": "user.registered",
    "version": 1,
    "occurred_at": datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z"),
    "producer": "smoke",
    "correlation_id": "smoke-dup-" + event_id,
    "payload": {
        "user_id": "999999",
        "email": email,
        "role": "candidate",
        "verification_url": "http://localhost:3000/verify-email?token=smoke-dup",
    },
}))
PY
)"
  DUP_BODY="$(python3 - "$DUP_ENVELOPE" <<'PY'
import json, sys
print(json.dumps({
    "properties": {},
    "routing_key": "user.registered",
    "payload": sys.argv[1],
    "payload_encoding": "string",
}))
PY
)"
  for _ in 1 2; do
    curl -fsS -u "${RABBIT_USER}:${RABBIT_PASS}" -H 'Content-Type: application/json' \
      -X POST "$RABBIT_API/exchanges/%2F/pullhire.events/publish" -d "$DUP_BODY" >/dev/null
  done
  echo "  published the same event_id twice; expecting idempotent handling"

  rows=0
  deadline=$((SECONDS + 60))
  while [ "$SECONDS" -lt "$deadline" ]; do
    rows="$(pg_value "SELECT count(*) FROM notification.notification_log WHERE source_event_id = '${DUP_EVENT_ID}'")"
    case "$rows" in ''|*[!0-9]*) rows=0 ;; esac
    if [ "$rows" != "0" ]; then
      break
    fi
    sleep 3
  done
  if [ "$rows" = "1" ]; then
    echo "  duplicate delivery -> single notification_log row (idempotent)"
  else
    echo "FAIL: duplicate delivery produced ${rows} notification_log rows (expected 1)"
    exit 1
  fi
fi

echo "==> S2 broker outage / no event loss"
if ! docker compose ps --status running --services 2>/dev/null | grep -qx rabbitmq; then
  echo "  SKIP: rabbitmq container not running; cannot exercise broker outage"
elif [ "$have_psql" != "1" ] && ! curl -fsS "$MAILPIT_API" >/dev/null 2>&1; then
  echo "  SKIP: neither psql nor Mailpit available to observe outbox delivery"
else
  STOP_CORR="smoke-outage-$(date +%s)-$$"
  STOP_EMAIL="outage.${STOP_CORR}@pullhire.local"
  echo "  stopping rabbitmq ..."
  docker compose stop rabbitmq >/dev/null 2>&1 || true
  stop_code="$(register_user "$STOP_EMAIL" "$STOP_CORR")"
  if [ "$stop_code" != "201" ]; then
    echo "FAIL: register during broker outage returned ${stop_code} (expected 201; outbox must still commit)"
    docker compose start rabbitmq >/dev/null 2>&1 || true
    exit 1
  fi
  echo "  register accepted while broker down (outbox row committed)"
  echo "  starting rabbitmq ..."
  docker compose start rabbitmq >/dev/null 2>&1 || true

  delivered=0
  deadline=$((SECONDS + 180))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if [ "$have_psql" = "1" ]; then
      pending="$(pg_value "SELECT count(*) FROM auth.outbox_events WHERE correlation_id = '${STOP_CORR}' AND published_at IS NOT NULL")"
    else
      pending="$({ curl -fsS "$MAILPIT_API" 2>/dev/null | grep -oF "$STOP_EMAIL" || true; } | wc -l | tr -d ' ')"
    fi
    case "$pending" in ''|*[!0-9]*) pending=0 ;; esac
    if [ "$pending" != "0" ]; then
      delivered=1
      break
    fi
    sleep 3
  done
  if [ "$delivered" = "1" ]; then
    echo "  outbox event delivered after broker restart (no loss)"
  else
    echo "FAIL: outbox event for ${STOP_CORR} not delivered within 180s of broker restart"
    exit 1
  fi
fi

echo "SMOKE OK"
