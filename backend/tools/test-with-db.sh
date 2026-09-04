#!/usr/bin/env bash
# The whole test suite, including the half that needs Postgres.
#
# `pnpm test` on its own silently skips every DATABASE_URL test --
# describe.skipIf, which reports them as "skipped" and exits 0. That is more
# than a third of the suite, and running without it is how five commits in a
# row were pushed with CI already red: the deploy workflow is gated on CI,
# so nothing shipped, and the only sign was a workflow nobody was looking at.
#
# This starts a throwaway cluster in /tmp, migrates it, and runs everything.
# It is not what CI runs -- CI has a real Postgres service -- but it fails in
# the same place, which is the point.
#
#   ./tools/test-with-db.sh            run the suite
#   DATABASE_URL=... pnpm test         if you already have a database
set -euo pipefail

cd "$(dirname "$0")/.."

if [ -n "${DATABASE_URL:-}" ]; then
  echo "using the DATABASE_URL already set"
  exec pnpm test "$@"
fi

PORT=${ASR_TEST_PG_PORT:-5599}
DATA=${ASR_TEST_PG_DATA:-/tmp/asr-test-pg}
BIN=$(ls -d /usr/lib/postgresql/*/bin 2>/dev/null | sort -V | tail -1 || true)
[ -n "$BIN" ] || { echo "No Postgres server binaries found. Install postgresql, or set DATABASE_URL."; exit 1; }
export PATH="$PATH:$BIN"

# initdb refuses to run as root, which is what this sandbox is. Anything
# unprivileged will do; the cluster is thrown away either way.
RUNAS=""
if [ "$(id -u)" -eq 0 ]; then
  for candidate in claude postgres nobody; do
    if id "$candidate" >/dev/null 2>&1; then RUNAS=$candidate; break; fi
  done
  [ -n "$RUNAS" ] || { echo "Running as root and no unprivileged user to start Postgres as."; exit 1; }
fi

run() { if [ -n "$RUNAS" ]; then su "$RUNAS" -s /bin/bash -c "PATH=$PATH; $1"; else bash -c "$1"; fi; }

if ! run "pg_ctl -D $DATA status" >/dev/null 2>&1; then
  echo "starting a throwaway Postgres on $PORT"
  rm -rf "$DATA"
  mkdir -p "$DATA"
  [ -n "$RUNAS" ] && chown "$RUNAS" "$DATA"
  run "initdb -U postgres -A trust -D $DATA" >/dev/null
  run "pg_ctl -D $DATA -o '-p $PORT -k /tmp' -l $DATA.log start" >/dev/null
  for _ in $(seq 1 20); do
    run "pg_isready -h /tmp -p $PORT" >/dev/null 2>&1 && break
    sleep 0.5
  done
fi

run "psql -h /tmp -p $PORT -U postgres -c 'drop database if exists asr_test'" >/dev/null
run "psql -h /tmp -p $PORT -U postgres -c 'create database asr_test'" >/dev/null

export DATABASE_URL="postgres://postgres@localhost:$PORT/asr_test"
npx tsx src/server/db/migrate.ts up
pnpm test "$@"
