#!/usr/bin/env bash
# S9 footprint (docs/architecture.md §5.1/§5.3, docs/deployment.md §1):
#   sum actual stack RAM via docker stats for running pullhire-* containers
#   and compare against the S9 exit criterion 4 threshold (<= 2.2 GB).
# Does NOT start the stack; the stack must already be running.
# Informational only: sum(mem_limits) is reported but never fails the check;
# limit tuning is ST-9.8's job. FAIL only when actual usage > threshold.
set -euo pipefail

THRESHOLD_MIB=2200

cd "$(dirname "$0")/.."

to_mib() { # "12.34MiB" | "1.5GiB" | "456KiB" | "123B" -> integer MiB (rounded down)
  printf '%s' "$1" | tr -d ' ' | awk '
    {
      v = $0
      num = v + 0
      if (v ~ /GiB/) { printf "%d", num * 1024 }
      else if (v ~ /MiB/) { printf "%d", num }
      else if (v ~ /KiB/) { printf "%d", num / 1024 }
      else if (v ~ /B$/) { printf "%d", num / 1024 / 1024 }
      else { printf "0" }
    }'
}

bytes_to_mib() { # bytes -> integer MiB (0 stays 0 = unlimited)
  printf '%s' "$1" | tr -d ' \r' | awk '{ printf "%d", $1 / 1024 / 1024 }'
}

echo "==> collecting running pullhire-* containers"
compose_ids="$(docker compose ps -q 2>/dev/null | tr -d '\r' || true)"
stats="$(docker stats --no-stream --format '{{.Name}}\t{{.MemUsage}}' 2>/dev/null || true)"
names="$(printf '%s\n' "$stats" | grep -E '^pullhire-' || true)"
if [ -z "$names" ]; then
  echo "FAIL: no running pullhire-* containers found (start the stack first)"
  exit 1
fi

echo "==> per-container memory (actual vs mem_limit)"
printf '  %-28s %12s %12s\n' "container" "actual" "mem_limit"
total_usage=0
total_limit=0
count=0
while IFS="$(printf '\t')" read -r cname memusage _rest; do
  [ -n "${cname:-}" ] || continue
  case "$cname" in pullhire-*) ;; *) continue ;; esac
  if [ -n "$compose_ids" ]; then
    cid="$(docker ps -q --no-trunc --filter "name=^${cname}$" 2>/dev/null | tr -d '\r' || true)"
    if [ -n "$cid" ]; then
      hit="$(printf '%s' "$compose_ids" | grep -qF "$cid" && printf yes || printf no)"
      if [ "$hit" != "yes" ]; then
        short="$(printf '%s' "$cid" | grep -oE '^[0-9a-f]+' || true)"
        if ! printf '%s' "$compose_ids" | grep -qF "$short"; then
          if ! printf '%s' "$compose_ids" | grep -qF "${cid:0:12}"; then
            continue
          fi
        fi
      fi
    fi
  fi
  used_raw="$(printf '%s' "$memusage" | awk -F'/' '{ print $1 }' | tr -d ' \r')"
  used_mib="$(to_mib "$used_raw")"
  case "$used_mib" in ''|*[!0-9]*) used_mib=0 ;; esac
  limit_bytes="$(docker inspect --format '{{.HostConfig.Memory}}' "$cname" 2>/dev/null | tr -d '\r ' || true)"
  case "$limit_bytes" in ''|*[!0-9]*) limit_bytes=0 ;; esac
  if [ "$limit_bytes" = "0" ]; then
    limit_disp="unlimited"
    limit_mib=0
  else
    limit_mib="$(bytes_to_mib "$limit_bytes")"
    case "$limit_mib" in ''|*[!0-9]*) limit_mib=0 ;; esac
    limit_disp="${limit_mib}MiB"
  fi
  printf '  %-28s %10sMiB %12s\n' "$cname" "$used_mib" "$limit_disp"
  total_usage=$((total_usage + used_mib))
  total_limit=$((total_limit + limit_mib))
  count=$((count + 1))
done <<EOF
$names
EOF

if [ "$count" -eq 0 ]; then
  echo "FAIL: no running pullhire-* containers found (start the stack first)"
  exit 1
fi

echo "  ------------------------------"
echo "  sum(actual usage) -> ${total_usage} MiB (threshold ${THRESHOLD_MIB} MiB over ${count} containers)"
echo "  sum(mem_limits) -> ${total_limit} MiB (informational: limits exceed target, tuning is ST-9.8's job)"
if [ "$total_usage" -gt "$THRESHOLD_MIB" ]; then
  echo "FAIL: actual stack RAM ${total_usage} MiB > threshold ${THRESHOLD_MIB} MiB"
  exit 1
fi
echo "FOOTPRINT OK"
