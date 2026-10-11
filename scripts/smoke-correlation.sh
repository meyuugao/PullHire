#!/usr/bin/env bash
# ST-9.6 smoke-correlation (docs/architecture.md §1.3, §7.1; docs/api.md §3.5, §3.7):
#   one X-Request-Id present as correlation_id in logs of every service
#   in the demo chain (exit criterion 5 of slice S9).
# Chain: auth -> candidate -> assessment -> employer -> selection (+ notification
# via Mailpit verification emails). The outreach leg (invitations/accept) is
# skipped: it requires a candidate category, and assessment calibration makes
# auto-assignment unreachable (TD S13, docs/backlog/closed_questions.md).
# Only gateway routes, plain bash, no jq (python3 OK).
set -euo pipefail

# Windows git-bash portability: `python3` may be an unusable Store stub
# while real CPython is available as `python`.
if ! python3 --version >/dev/null 2>&1; then
  python3() { python "$@"; }
fi

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
  echo "  POST /auth/register -> 201 for ${email} (role ${role})" >&2
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
  echo "  POST /auth/verify-email -> 200 for ${email}" >&2
  login="$(login_token "$email" "$CORR_ID")"
  if [ -z "$login" ]; then
    echo "FAIL: POST /auth/login returned no access_token for ${email}"
    exit 1
  fi
  echo "  POST /auth/login -> access_token issued for ${email}" >&2
  printf '%s' "$login"
}

assert_log_contains() { # service seconds -> PASS/FAIL (poll; eventually consistent)
  local svc="$1" seconds="$2"
  local deadline=$((SECONDS + seconds))
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
echo "  notification-service -> PASS (verification emails delivered via Mailpit)" >&2

echo "==> candidate profile + assessment (category prerequisite for POST /invitations)"
PROF_RESP="$(curl -k -fsS -X PUT "$GATEWAY/candidate/profile" \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $CAND_TOKEN" -H "X-Request-Id: $CORR_ID" \
  -d "$(printf '{"full_name":"Corr Cand","contact_email":"%s","specialization_code":"backend","industry_code":"it"}' "$CAND_EMAIL")" 2>/dev/null || true)"
CAND_PROFILE_ID="$(printf '%s' "$PROF_RESP" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin).get("id",""))' 2>/dev/null || true)"
if [ -z "$CAND_PROFILE_ID" ]; then
  echo "FAIL: PUT /candidate/profile returned no id"
  exit 1
fi
echo "  PUT /candidate/profile -> 200 (profile_id ${CAND_PROFILE_ID})" >&2

SURV_RESP="$(curl -k -fsS -X POST "$GATEWAY/assessment/survey" \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $CAND_TOKEN" -H "X-Request-Id: $CORR_ID" \
  -d '{"industry_code":"it","specialization_code":"backend","target_grade":"middle","declared_dimensions":["backend","middle"],"survey_answers":{"q1":"a"}}' 2>/dev/null || true)"
SESS_ID="$(printf '%s' "$SURV_RESP" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin).get("session_id",""))' 2>/dev/null || true)"
if [ -z "$SESS_ID" ]; then
  echo "FAIL: POST /assessment/survey returned no session_id"
  exit 1
fi
echo "  POST /assessment/survey -> 200 (session ${SESS_ID})" >&2

CURR="$(curl -k -fsS -X POST "$GATEWAY/assessment/sessions/${SESS_ID}/start" \
  -H "Authorization: Bearer $CAND_TOKEN" -H "X-Request-Id: $CORR_ID" 2>/dev/null || true)"
if [ -z "$CURR" ]; then
  echo "FAIL: POST /assessment/sessions/${SESS_ID}/start returned empty response"
  exit 1
fi
i=0
while [ "$i" -lt 32 ]; do
  i=$((i + 1))
  DONE="$(printf '%s' "$CURR" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin).get("completed", False))' 2>/dev/null || true)"
  if [ "$DONE" = "True" ]; then
    break
  fi
  # Seed-coupled answers (assessment_seed.sql keys): single_choice=option-b,
  # multiple_choice=[statement-a,statement-c], backend anchor short_answer=500;
  # code items are not auto-gradable (any answer counts as served).
  PAYLOAD="$(printf '%s' "$CURR" | python3 -c '
import json, sys
d = json.load(sys.stdin)
item = d.get("item") or {}
content = item.get("content") or {}
fmt = content.get("format", "")
if fmt == "single_choice":
    ans = "option-b"
elif fmt == "multiple_choice":
    ans = ["statement-a", "statement-c"]
elif fmt == "short_answer":
    ans = 500 if content.get("answer_type") == "integer" else "smoke"
else:
    ans = "smoke"
print(json.dumps({"item_id": item.get("item_id"), "answer": ans}))' 2>/dev/null || true)"
  CURR="$(curl -k -fsS -X POST "$GATEWAY/assessment/sessions/${SESS_ID}/answers" \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $CAND_TOKEN" -H "X-Request-Id: $CORR_ID" \
    -H "Idempotency-Key: ans-${CORR_ID}-$i" -d "$PAYLOAD" 2>/dev/null || true)"
  if [ -z "$CURR" ]; then
    echo "FAIL: POST /assessment/sessions/${SESS_ID}/answers (item $i) returned empty response"
    exit 1
  fi
done
CAT_CODE="$(printf '%s' "$CURR" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin).get("category_code") or "")' 2>/dev/null || true)"
SESS_STATUS="$(printf '%s' "$CURR" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin).get("status",""))' 2>/dev/null || true)"
if [ -z "$CAT_CODE" ]; then
  # Expected with the committed seed: the 4-items/spec bank exhausts before SE
  # can reach the auto-assign threshold, so the session lands in needs_review
  # without a category (TD S13, see docs/backlog/closed_questions.md). The
  # correlation chain does not depend on the category outcome.
  echo "  assessment answers loop -> completed (status ${SESS_STATUS}, no auto-category — TD S13)" >&2
else
  echo "  assessment answers loop -> completed (category ${CAT_CODE})" >&2
fi

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
  -H "Idempotency-Key: sel-${CORR_ID}" \
  -d '{"title":"Corr Backend","specialization_code":"backend","grade":"middle"}' 2>/dev/null || true)"
SEL_ID="$(printf '%s' "$SEL_RESP" \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("id", d.get("selection_id","")))' 2>/dev/null || true)"
if [ -z "$SEL_RESP" ]; then
  echo "FAIL: POST /selections returned empty response"
  exit 1
fi
echo "  POST /selections -> PASS${SEL_ID:+ (id ${SEL_ID})}"

# outreach-нога (POST /invitations + accept) пропущена: требует категорию
# кандидата, а калибровка assessment (банк 4 вопроса vs SE<=0.3) делает
# автовыдачу категории недостижимой — TD S13, см.
# docs/backlog/closed_questions.md; correlation для outreach покрывается
# invitation-логами unit-уровня.

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
for svc in auth-service candidate-service assessment-service employer-service selection-service notification-service; do
  assert_log_contains "$svc" 120
done

echo "CORRELATION OK (${CORR_ID})"
