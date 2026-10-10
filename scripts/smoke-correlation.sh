#!/usr/bin/env bash
# ST-9.6 smoke-correlation (docs/architecture.md §1.3, §7.1; docs/api.md §3.5, §3.7):
#   one X-Request-Id present as correlation_id in logs of every service
#   in the demo chain (exit criterion 5 of slice S9).
# Only gateway routes, plain bash, no jq (python3 OK).
set -euo pipefail

GATEWAY="https://localhost"
MAILPIT_API="http://localhost:8025/api/v1/messages"

CORR_ID="corr-$(date +%s)-$$"
PASS="Sm0ke-Test-1"
EMP_EMAIL="corr-emp.${CORR_ID}@pullhire.local"
CAND_EMAIL="corr-cand.${CORR_ID}@pullhire.local"

register_user() { # email corr [role] [idempotency-key] -> HTTP status code
  local email="$1" corr="$2" role="${3:-candidate}" idem="${4:-corr-$(date +%s%N)-$$}" body
  body="$(printf '{"email":"%s","password":"Sm0ke-Test-1","role":"%s"}' "$email" "$role")"
  curl -k -s -o /dev/null -w '%{http_code}' -X POST "$GATEWAY/auth/register" \
    -H 'Content-Type: application/json' -H "X-Request-Id: $corr" \
    -H "Idempotency-Key: $idem" -d "$body" || true
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

login_token() { # email corr -> access_token (empty on failure)
  local email="$1" corr="$2"
  curl -k -fsS -X POST "$GATEWAY/auth/login" \
    -H 'Content-Type: application/json' -H "X-Request-Id: $corr" \
    -d "$(printf '{"email":"%s","password":"%s"}' "$email" "$PASS")" 2>/dev/null \
    | python3 -c 'import json,sys; print(json.load(sys.stdin).get("access_token",""))' 2>/dev/null || true
}

verify_and_login() { # email role -> access_token (registers, verifies, logs in)
  local email="$1" role="$2" code token deadline login
  code="$(register_user "$email" "$CORR_ID" "$role")"
  if [ "$code" != "201" ]; then
    echo "FAIL: POST /auth/register (${role}) returned ${code} (expected 201) for ${email}"
    exit 1
  fi
  echo "  POST /auth/register -> 201 for ${email} (role ${role})"
  token=""
  deadline=$((SECONDS + 90))
  while [ "$SECONDS" -lt "$deadline" ]; do
    token="$(fetch_verify_token "$email")"
    if [ -n "$token" ]; then
      break
    fi
    sleep 3
  done
  if [ -z "$token" ]; then
    echo "FAIL: no verification token in Mailpit for ${email} within 90s"
    exit 1
  fi
  code="$(curl -k -s -o /dev/null -w '%{http_code}' -X POST "$GATEWAY/auth/verify-email" \
    -H 'Content-Type: application/json' -H "X-Request-Id: $CORR_ID" \
    -d "$(printf '{"token":"%s"}' "$token")" || true)"
  if [ "$code" != "200" ]; then
    echo "FAIL: POST /auth/verify-email returned ${code} (expected 200) for ${email}"
    exit 1
  fi
  echo "  POST /auth/verify-email -> 200 for ${email}"
  login="$(login_token "$email" "$CORR_ID")"
  if [ -z "$login" ]; then
    echo "FAIL: POST /auth/login returned no access_token for ${email}"
    exit 1
  fi
  echo "  POST /auth/login -> access_token issued for ${email}"
  printf '%s' "$login"
}

assert_log_contains() { # service seconds -> PASS/FAIL (poll; eventually consistent)
  local svc="$1" seconds="$2" deadline=$((SECONDS + seconds))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if docker compose logs --no-color "$svc" 2>/dev/null | grep -qF "$CORR_ID"; then
      echo "  ${svc} -> PASS (correlation_id ${CORR_ID} found)"
      return 0
    fi
    sleep 3
  done
  echo "FAIL: correlation_id ${CORR_ID} not found in ${svc} logs within ${seconds}s"
  exit 1
}

echo "==> gateway X-Request-Id generation (no header sent)"
GEN_ID="$(curl -k -s -D - -o /dev/null "$GATEWAY/auth/register" 2>/dev/null \
  | python3 -c 'import sys; h={}; [h.__setitem__(k.strip().lower(),v.strip()) for l in sys.stdin.read().splitlines() if ":" in l for k,v in [l.split(":",1)]]; print(h.get("x-request-id",""))' 2>/dev/null || true)"
if [ -z "$GEN_ID" ]; then
  echo "FAIL: gateway did not echo/generate X-Request-Id header"
  exit 1
fi
echo "  gateway X-Request-Id echo -> PASS (${GEN_ID})"

echo "==> register + verify + login (employer + candidate, X-Request-Id: ${CORR_ID})"
EMP_TOKEN="$(verify_and_login "$EMP_EMAIL" "employer")"
CAND_TOKEN="$(verify_and_login "$CAND_EMAIL" "candidate")"

echo "==> employer company + need (context for POST /selections)"
put_code="$(curl -k -s -o /dev/null -w '%{http_code}' -X PUT "$GATEWAY/employer/company" \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $EMP_TOKEN" -H "X-Request-Id: $CORR_ID" \
  -d '{"name":"Corr Acme","contact_email":"hr@corr.local"}' || true)"
if [ "$put_code" != "200" ]; then
  echo "FAIL: PUT /employer/company returned ${put_code} (expected 200)"
  exit 1
fi
echo "  PUT /employer/company -> 200"
NEED_RESP="$(curl -k -fsS -X POST "$GATEWAY/employer/needs" \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $EMP_TOKEN" -H "X-Request-Id: $CORR_ID" \
  -d '{"title":"Corr Backend","specialization_code":"backend","grade":"middle"}' 2>/dev/null || true)"
NEED_ID="$(printf '%s' "$NEED_RESP" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin).get("id",""))' 2>/dev/null || true)"
if [ -z "$NEED_ID" ]; then
  echo "FAIL: POST /employer/needs returned no id"
  exit 1
fi
echo "  POST /employer/needs -> 201 (id ${NEED_ID})"

echo "==> POST /selections (selection-service)"
SEL_RESP="$(curl -k -fsS -X POST "$GATEWAY/selections" \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $EMP_TOKEN" -H "X-Request-Id: $CORR_ID" \
  -d "$(printf '{"need_id":%s,"specialization_code":"backend","grade":"middle"}' "$NEED_ID")" 2>/dev/null || true)"
SEL_ID="$(printf '%s' "$SEL_RESP" \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("id", d.get("selection_id","")))' 2>/dev/null || true)"
if [ -z "$SEL_RESP" ]; then
  echo "FAIL: POST /selections returned empty response"
  exit 1
fi
echo "  POST /selections -> PASS${SEL_ID:+ (id ${SEL_ID})}"

echo "==> resolve candidate_id for POST /invitations"
CAND_ID="$(curl -k -fsS "$GATEWAY/candidates?limit=50" \
  -H "Authorization: Bearer $EMP_TOKEN" -H "X-Request-Id: $CORR_ID" 2>/dev/null \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); items=d.get("items", d.get("candidates", d if isinstance(d,list) else [])); print(items[0].get("id", items[0].get("candidate_id","")) if items else "")' 2>/dev/null || true)"
if [ -z "$CAND_ID" ]; then
  CAND_ID="1"
  echo "  candidate list empty/unavailable, using fallback candidate_id=1"
else
  echo "  candidate_id=${CAND_ID}"
fi

echo "==> POST /invitations (outreach-service)"
INV_RESP="$(curl -k -fsS -X POST "$GATEWAY/invitations" \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $EMP_TOKEN" -H "X-Request-Id: $CORR_ID" \
  -d "$(printf '{"candidate_id":%s,"need_id":%s,"message":"Join Corr Acme","company_name":"Corr Acme","salary_from":100000,"salary_to":150000,"salary_basis":"gross","employer_contact":{"contact_email":"hr@corr.local"}}' "$CAND_ID" "$NEED_ID")" 2>/dev/null || true)"
INV_ID="$(printf '%s' "$INV_RESP" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin).get("id",""))' 2>/dev/null || true)"
if [ -z "$INV_ID" ]; then
  echo "FAIL: POST /invitations returned no id"
  exit 1
fi
echo "  POST /invitations -> 201 (id ${INV_ID})"

echo "==> candidate accepts invitation (invitation.accepted -> contact_released)"
acc_code="$(curl -k -s -o /dev/null -w '%{http_code}' -X POST "$GATEWAY/invitations/${INV_ID}/accept" \
  -H "Authorization: Bearer $CAND_TOKEN" -H "X-Request-Id: $CORR_ID" || true)"
if [ "$acc_code" != "200" ] && [ "$acc_code" != "201" ] && [ "$acc_code" != "204" ]; then
  echo "FAIL: POST /invitations/${INV_ID}/accept returned ${acc_code} (expected 200/201/204)"
  exit 1
fi
echo "  POST /invitations/${INV_ID}/accept -> ${acc_code}"

echo "==> gateway X-Request-Id echo on correlated request"
ECHO_ID="$(curl -k -s -D - -o /dev/null "$GATEWAY/selections/${SEL_ID:-1}" \
  -H "Authorization: Bearer $EMP_TOKEN" -H "X-Request-Id: $CORR_ID" 2>/dev/null \
  | python3 -c 'import sys; h={}; [h.__setitem__(k.strip().lower(),v.strip()) for l in sys.stdin.read().splitlines() if ":" in l for k,v in [l.split(":",1)]]; print(h.get("x-request-id",""))' 2>/dev/null || true)"
if [ "$ECHO_ID" != "$CORR_ID" ]; then
  echo "FAIL: gateway X-Request-Id echo mismatch (got '${ECHO_ID}', expected '${CORR_ID}')"
  exit 1
fi
echo "  gateway X-Request-Id echo -> PASS (${ECHO_ID})"

echo "==> correlation_id ${CORR_ID} in service logs (poll, deadline 120s each)"
for svc in auth-service selection-service outreach-service candidate-service notification-service; do
  assert_log_contains "$svc" 120
done

echo "CORRELATION OK (${CORR_ID})"
