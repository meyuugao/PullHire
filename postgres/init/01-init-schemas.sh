#!/bin/bash
# TOPOLOGY A Postgres init (docs/architecture.md §5.2, docs/deployment.md §4):
# one schema + one role per business service. Cross-schema access stays impossible
# by convention (no PUBLIC grants on the created schemas).
set -euo pipefail

create_role_and_schema() {
    local role="$1"
    local password="$2"
    local schema="$3"

    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
		CREATE ROLE ${role} LOGIN PASSWORD '${password}';
		CREATE SCHEMA IF NOT EXISTS ${schema} AUTHORIZATION ${role};
		REVOKE ALL ON SCHEMA ${schema} FROM PUBLIC;
		GRANT USAGE, CREATE ON SCHEMA ${schema} TO ${role};
		ALTER ROLE ${role} SET search_path = ${schema};
	SQL
    echo "init: role=${role} schema=${schema}"
}

create_role_and_schema "$AUTH_DB_ROLE" "$AUTH_DB_PASSWORD" "auth"
create_role_and_schema "$CANDIDATE_DB_ROLE" "$CANDIDATE_DB_PASSWORD" "candidate"
create_role_and_schema "$FSP_DB_ROLE" "$FSP_DB_PASSWORD" "fsp"
create_role_and_schema "$ASSESSMENT_DB_ROLE" "$ASSESSMENT_DB_PASSWORD" "assessment"
create_role_and_schema "$SELECTION_DB_ROLE" "$SELECTION_DB_PASSWORD" "selection"
create_role_and_schema "$EMPLOYER_DB_ROLE" "$EMPLOYER_DB_PASSWORD" "employer"
create_role_and_schema "$OUTREACH_DB_ROLE" "$OUTREACH_DB_PASSWORD" "outreach"
create_role_and_schema "$NOTIFICATION_DB_ROLE" "$NOTIFICATION_DB_PASSWORD" "notification"
