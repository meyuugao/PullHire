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

register_user() { # email corr [role] -> HTTP status code
  local email="$1" corr="$2" role="${3:-candidate}" body
  body="$(printf '{"email":"%s","password":"Sm0ke-Test-1","role":"%s"}' "$email" "$role")"
  curl -k -s -o /dev/null -w '%{http_code}' -X POST "$GATEWAY/auth/register" \
    -H 'Content-Type: application/json' -H "X-Request-Id: $corr" -d "$body" || true
}

fetch_verify_token() { # email -> raw verification token (empty when not yet delivered)
  python3 - "$MAILPIT_API" "$1" <<'PY' 2>/dev/null || true
import json, re, sys, urllib.request
api, email = sys.argv[1], sys.argv[2]
try:
    with urllib.request.urlopen(api + "?limit=50", timeout=5) as r:
        data = json.load(r)
except Exception:
    sys.exit(1)
msgs = data.get("messages", data if isinstance(data, list) else [])
for m in msgs:
    if email not in json.dumps(m):
        continue
    mid = m.get("ID", m.get("id"))
    try:
        with urllib.request.urlopen(api.rstrip("/messages") + "/message/%s" % mid, timeout=5) as r:
            body = r.read().decode("utf-8", "replace")
    except Exception:
        continue
    hit = re.search(r"token=([A-Za-z0-9_\-]+)", body)
    if hit:
        print(hit.group(1))
        break
PY
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

# =============================================================================
# S6 checks — employer-service company + needs via gateway
# =============================================================================
echo "==> S6 employer company + needs flow via gateway"
if [ "$have_psql" != "1" ] && ! curl -fsS "$MAILPIT_API" >/dev/null 2>&1; then
  echo "  SKIP: psql and Mailpit API both unavailable, cannot verify employer email"
else
  S6_CORR="smoke-s6-$(date +%s)-$$"
  S6_EMAIL="employer.${S6_CORR}@pullhire.local"
  S6_PASS="Sm0ke-Test-1"
  reg_code="$(register_user "$S6_EMAIL" "$S6_CORR" "employer")"
  if [ "$reg_code" != "201" ]; then
    echo "FAIL: POST /auth/register (employer) returned ${reg_code} (expected 201)"
    exit 1
  fi
  echo "  POST /auth/register -> 201 for ${S6_EMAIL} (role employer, X-Request-Id: ${S6_CORR})"

  # verify-email: the raw token travels only in the rendered email (Mailpit
  # regex); auth.email_tokens stores the hash, so psql only confirms issuance.
  VERIFY_TOKEN=""
  deadline=$((SECONDS + 90))
  while [ "$SECONDS" -lt "$deadline" ]; do
    VERIFY_TOKEN="$(fetch_verify_token "$S6_EMAIL")"
    if [ -n "$VERIFY_TOKEN" ]; then
      break
    fi
    sleep 3
  done
  if [ -z "$VERIFY_TOKEN" ]; then
    echo "FAIL: no verification token in Mailpit for ${S6_EMAIL} within 90s"
    exit 1
  fi
  verify_code="$(curl -k -s -o /dev/null -w '%{http_code}' -X POST "$GATEWAY/auth/verify-email" \
    -H 'Content-Type: application/json' -H "X-Request-Id: $S6_CORR" \
    -d "$(printf '{"token":"%s"}' "$VERIFY_TOKEN")" || true)"
  if [ "$verify_code" != "200" ]; then
    echo "FAIL: POST /auth/verify-email returned ${verify_code} (expected 200)"
    exit 1
  fi
  echo "  POST /auth/verify-email -> 200"

  LOGIN_RESP="$(curl -k -fsS -X POST "$GATEWAY/auth/login" \
    -H 'Content-Type: application/json' -H "X-Request-Id: $S6_CORR" \
    -d "$(printf '{"email":"%s","password":"%s"}' "$S6_EMAIL" "$S6_PASS")" 2>/dev/null || true)"
  EMP_TOKEN="$(printf '%s' "$LOGIN_RESP" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin).get("access_token",""))' 2>/dev/null || true)"
  if [ -z "$EMP_TOKEN" ]; then
    echo "FAIL: POST /auth/login returned no access_token for ${S6_EMAIL}"
    exit 1
  fi
  echo "  POST /auth/login -> access_token issued"

  put_code="$(curl -k -s -o /dev/null -w '%{http_code}' -X PUT "$GATEWAY/employer/company" \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $EMP_TOKEN" -H "X-Request-Id: $S6_CORR" \
    -d '{"name":"Smoke Acme","contact_email":"hr@smoke.local"}' || true)"
  if [ "$put_code" != "200" ]; then
    echo "FAIL: PUT /employer/company returned ${put_code} (expected 200)"
    exit 1
  fi
  echo "  PUT /employer/company -> 200"
  get_code="$(curl -k -s -o /dev/null -w '%{http_code}' "$GATEWAY/employer/company" \
    -H "Authorization: Bearer $EMP_TOKEN" -H "X-Request-Id: $S6_CORR" || true)"
  if [ "$get_code" != "200" ]; then
    echo "FAIL: GET /employer/company returned ${get_code} (expected 200)"
    exit 1
  fi
  echo "  GET /employer/company -> 200"

  NEED_RESP="$(curl -k -fsS -X POST "$GATEWAY/employer/needs" \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $EMP_TOKEN" -H "X-Request-Id: $S6_CORR" \
    -d '{"title":"Smoke Backend","specialization_code":"backend","grade":"middle"}' 2>/dev/null || true)"
  NEED_ID="$(printf '%s' "$NEED_RESP" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin).get("id",""))' 2>/dev/null || true)"
  if [ -z "$NEED_ID" ]; then
    echo "FAIL: POST /employer/needs returned no id"
    exit 1
  fi
  echo "  POST /employer/needs -> 201 (id ${NEED_ID})"
  LIST_RESP="$(curl -k -fsS "$GATEWAY/employer/needs" \
    -H "Authorization: Bearer $EMP_TOKEN" -H "X-Request-Id: $S6_CORR" 2>/dev/null || true)"
  NEED_TOTAL="$(printf '%s' "$LIST_RESP" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin).get("total",""))' 2>/dev/null || true)"
  if [ "$NEED_TOTAL" != "1" ]; then
    echo "FAIL: GET /employer/needs total=${NEED_TOTAL} (expected 1)"
    exit 1
  fi
  echo "  GET /employer/needs -> 200 (total=1)"
  need_put="$(curl -k -s -o /dev/null -w '%{http_code}' -X PUT "$GATEWAY/employer/needs/${NEED_ID}" \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $EMP_TOKEN" -H "X-Request-Id: $S6_CORR" \
    -d '{"title":"Smoke Backend v2","specialization_code":"backend","grade":"middle"}' || true)"
  if [ "$need_put" != "200" ]; then
    echo "FAIL: PUT /employer/needs/${NEED_ID} returned ${need_put} (expected 200)"
    exit 1
  fi
  echo "  PUT /employer/needs/${NEED_ID} -> 200"
  need_del="$(curl -k -s -o /dev/null -w '%{http_code}' -X DELETE "$GATEWAY/employer/needs/${NEED_ID}" \
    -H "Authorization: Bearer $EMP_TOKEN" -H "X-Request-Id: $S6_CORR" || true)"
  if [ "$need_del" != "204" ]; then
    echo "FAIL: DELETE /employer/needs/${NEED_ID} returned ${need_del} (expected 204)"
    exit 1
  fi
  echo "  DELETE /employer/needs/${NEED_ID} -> 204"

  # OQ-26 publish-only: the delete fans out via the employer outbox; no temp
  # queues, no Rabbit inbound pattern. Correlation comes from X-Request-Id→MDC.
  if [ "$have_psql" != "1" ]; then
    echo "  SKIP need_deleted outbox assert: psql unavailable"
  else
    delivered=0
    deadline=$((SECONDS + 60))
    while [ "$SECONDS" -lt "$deadline" ]; do
      cnt="$(pg_value "SELECT count(*) FROM employer.outbox_events WHERE correlation_id = '${S6_CORR}' AND event_type = 'employer.need_deleted' AND published_at IS NOT NULL")"
      case "$cnt" in ''|*[!0-9]*) cnt=0 ;; esac
      if [ "$cnt" != "0" ]; then
        delivered=1
        break
      fi
      sleep 3
    done
    if [ "$delivered" = "1" ]; then
      echo "  employer.need_deleted outbox row published (correlation ${S6_CORR})"
    else
      echo "FAIL: no published employer.need_deleted outbox row for ${S6_CORR} within 60s"
      exit 1
    fi
  fi

  echo "==> S6 401/403 via gateway"
  anon_code="$(curl -k -s -o /dev/null -w '%{http_code}' "$GATEWAY/employer/company" || true)"
  if [ "$anon_code" != "401" ]; then
    echo "FAIL: GET /employer/company without token returned ${anon_code} (expected 401)"
    exit 1
  fi
  echo "  GET /employer/company without token -> 401"
  CAND_CORR="smoke-s6-cand-$(date +%s)-$$"
  CAND_EMAIL="candidate.${CAND_CORR}@pullhire.local"
  cand_reg="$(register_user "$CAND_EMAIL" "$CAND_CORR" "candidate")"
  if [ "$cand_reg" != "201" ]; then
    echo "FAIL: POST /auth/register (candidate) returned ${cand_reg} (expected 201)"
    exit 1
  fi
  CAND_TOKEN_VALUE=""
  deadline=$((SECONDS + 90))
  while [ "$SECONDS" -lt "$deadline" ]; do
    CAND_TOKEN_VALUE="$(fetch_verify_token "$CAND_EMAIL")"
    if [ -n "$CAND_TOKEN_VALUE" ]; then
      break
    fi
    sleep 3
  done
  if [ -z "$CAND_TOKEN_VALUE" ]; then
    echo "FAIL: no verification token in Mailpit for ${CAND_EMAIL} within 90s"
    exit 1
  fi
  curl -k -fsS -X POST "$GATEWAY/auth/verify-email" \
    -H 'Content-Type: application/json' -H "X-Request-Id: $CAND_CORR" \
    -d "$(printf '{"token":"%s"}' "$CAND_TOKEN_VALUE")" >/dev/null
  CAND_LOGIN="$(curl -k -fsS -X POST "$GATEWAY/auth/login" \
    -H 'Content-Type: application/json' -H "X-Request-Id: $CAND_CORR" \
    -d "$(printf '{"email":"%s","password":"%s"}' "$CAND_EMAIL" "$S6_PASS")" 2>/dev/null || true)"
  CAND_TOKEN="$(printf '%s' "$CAND_LOGIN" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin).get("access_token",""))' 2>/dev/null || true)"
  if [ -z "$CAND_TOKEN" ]; then
    echo "FAIL: POST /auth/login returned no access_token for ${CAND_EMAIL}"
    exit 1
  fi
  forb_code="$(curl -k -s -o /dev/null -w '%{http_code}' "$GATEWAY/employer/company" \
    -H "Authorization: Bearer $CAND_TOKEN" -H "X-Request-Id: $CAND_CORR" || true)"
  if [ "$forb_code" != "403" ]; then
    echo "FAIL: GET /employer/company with candidate token returned ${forb_code} (expected 403)"
    exit 1
  fi
  echo "  GET /employer/company with candidate token -> 403"
fi

echo "SMOKE OK"
