#!/usr/bin/env bash
# S9 PII audit (docs/data_model.md §7.3 search projection, §13 audit/retention):
#   audits live compose postgres for PII-convention compliance.
#   §7.3  - selection.candidate_cards exposes only the whitelisted,
#           non-contact projection (display_name is masked, NOT a leak).
#   §13   - audit tables exist; audit/log tables carry no secrets;
#           contact release lives in the invitations table, never in
#           outreach.invitation_events (see §9.2/§13).
set -euo pipefail

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

pg_value() { # sql -> single trimmed scalar
  docker compose exec -T postgres psql -U "$PG_USER" -d "$PG_DB" -tAc "$1" 2>/dev/null | tr -d '\r[:space:]'
}

pg_lines() { # sql -> raw lines (one value per line, order preserved)
  docker compose exec -T postgres psql -U "$PG_USER" -d "$PG_DB" -tAc "$1" 2>/dev/null | tr -d '\r'
}

FAILURES=0

pass() { # name
  echo "  $1 -> PASS"
}

fail() { # name detail
  echo "FAIL: $1: $2"
  FAILURES=$((FAILURES + 1))
}

echo "==> PII conventions audit against live compose postgres"

# DB is mandatory: no SKIP paths, fail if postgres is unreachable.
if ! docker compose exec -T postgres psql -U "$PG_USER" -d "$PG_DB" -tAc 'SELECT 1' >/dev/null 2>&1; then
  echo "FAIL: postgres-unreachable: docker compose exec -T postgres psql -U \"$PG_USER\" -d \"$PG_DB\" failed"
  exit 1
fi

# --- (a) PII in search projection -------------------------------------------
# Whitelist of legit columns for selection.candidate_cards (data_model §7.3).
# display_name is intentionally NOT blacklisted: it is masked per §7.3.
echo "==> check (a): selection.candidate_cards projection"
CARDS_COLS="$(pg_lines "SELECT column_name FROM information_schema.columns WHERE table_schema = 'selection' AND table_name = 'candidate_cards' ORDER BY 1;")"
if [ -z "$CARDS_COLS" ]; then
  fail "candidate-cards-projection" "selection.candidate_cards has no visible columns (missing table?)"
else
  BAD=""
  while IFS= read -r col; do
    [ -z "$col" ] && continue
    lower="$(printf '%s' "$col" | tr '[:upper:]' '[:lower:]')"
    case "$lower" in
      id|candidate_id|display_name|specialization_code|grade|stack|fsp_verified|fsp_achievements_count|updated_at)
        ;;
      *)
        BAD="${BAD}${BAD:+, }${col}(not-whitelisted)"
        continue
        ;;
    esac
    if printf '%s' "$lower" | grep -Eq '(email|phone|contact)'; then
      BAD="${BAD}${BAD:+, }${col}(contact-pattern)"
    fi
  done <<< "$CARDS_COLS"
  # Belt-and-braces scalar: any contact-pattern column at all.
  CONTACT_HIT="$(pg_value "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'selection' AND table_name = 'candidate_cards' AND column_name ~* '(email|phone|contact)'")"
  case "$CONTACT_HIT" in ''|*[!0-9]*) CONTACT_HIT=0 ;; esac
  if [ "$CONTACT_HIT" != "0" ]; then
    CONTACT_COLS="$(pg_lines "SELECT column_name FROM information_schema.columns WHERE table_schema = 'selection' AND table_name = 'candidate_cards' AND column_name ~* '(email|phone|contact)' ORDER BY 1;" | tr '\n' ',' | sed 's/,$//;s/,/, /g')"
    BAD="${BAD}${BAD:+; }contact-pattern columns present: ${CONTACT_COLS}"
  fi
  if [ -z "$BAD" ]; then
    pass "candidate-cards-projection"
  else
    fail "candidate-cards-projection" "$BAD"
  fi
fi

# --- (b) §13 audit tables exist ----------------------------------------------
echo "==> check (b): section-13 audit tables exist"
for tbl in "assessment.grade_history" "outreach.invitation_events" "auth.consents" "auth.users" "notification.notification_log"; do
  HIT="$(pg_lines "SELECT to_regclass('${tbl}')")"
  if [ -z "$HIT" ] || [ "$HIT" = "-" ]; then
    fail "audit-table-${tbl}" "to_regclass('${tbl}') is NULL (missing)"
  else
    pass "audit-table-${tbl}"
  fi
done
DELETED_AT="$(pg_value "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'auth' AND table_name = 'users' AND column_name = 'deleted_at'")"
case "$DELETED_AT" in ''|*[!0-9]*) DELETED_AT=0 ;; esac
if [ "$DELETED_AT" = "0" ]; then
  fail "audit-table-auth.users.deleted_at" "column auth.users.deleted_at missing"
else
  pass "audit-table-auth.users.deleted_at"
fi

# --- (c) No secrets in audit/log tables --------------------------------------
echo "==> check (c): no secrets or contact columns in audit/log tables"
# auth.users is intentionally NOT scanned here: §13 lists it only for soft-delete
# (deleted_at); password_hash is the canonical credential store (data_model §3.1),
# not a secret duplicated into logs. Scanned tables are the append-only logs.
SECRET_HIT="$(pg_lines "SELECT table_schema || '.' || table_name || '.' || column_name FROM information_schema.columns WHERE (table_schema, table_name) IN (('assessment','grade_history'),('outreach','invitation_events'),('auth','consents'),('notification','notification_log')) AND column_name ~* '(password|token|secret)' ORDER BY 1;")"
if [ -n "$SECRET_HIT" ]; then
  DETAIL="$(printf '%s' "$SECRET_HIT" | tr '\n' ',' | sed 's/,$//;s/,/, /g')"
  fail "audit-tables-no-secrets" "secret-bearing columns present: ${DETAIL}"
else
  pass "audit-tables-no-secrets"
fi
EVENTS_CONTACT="$(pg_lines "SELECT column_name FROM information_schema.columns WHERE table_schema = 'outreach' AND table_name = 'invitation_events' AND column_name ~* '(email|phone|contact)' ORDER BY 1;")"
if [ -n "$EVENTS_CONTACT" ]; then
  DETAIL="$(printf '%s' "$EVENTS_CONTACT" | tr '\n' ',' | sed 's/,$//;s/,/, /g')"
  fail "invitation-events-no-contact" "contact-bearing columns present: ${DETAIL} (contact release lives in the invitations table, not events, per §9.2/§13)"
else
  pass "invitation-events-no-contact"
fi

# --- (d) 8 schemas exist ------------------------------------------------------
echo "==> check (d): eight domain schemas exist"
MISSING=""
for schema in auth candidate fsp assessment selection employer outreach notification; do
  HIT="$(pg_value "SELECT count(*) FROM pg_namespace WHERE nspname = '${schema}'")"
  case "$HIT" in ''|*[!0-9]*) HIT=0 ;; esac
  if [ "$HIT" = "0" ]; then
    MISSING="${MISSING}${MISSING:+, }${schema}"
  fi
done
if [ -n "$MISSING" ]; then
  fail "domain-schemas" "missing schemas: ${MISSING}"
else
  pass "domain-schemas"
fi

# --- (e) Negative sanity check (comment only) ---------------------------------
# Negative sanity (not executed): intentionally adding a contact-bearing column
# (e.g. ALTER TABLE selection.candidate_cards ADD COLUMN contact_email text, or
#  ALTER TABLE outreach.invitation_events ADD COLUMN phone text) must flip check
# (a) and/or (c) above to FAIL. No mutation is performed here by design: this
# audit is read-only against the live compose database.

if [ "$FAILURES" != "0" ]; then
  echo "PII AUDIT FAILED (${FAILURES} check(s) failed)"
  exit 1
fi
echo "PII AUDIT OK"
