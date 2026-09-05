#!/usr/bin/env bash
# End-to-end test of rollback.sh against a real docker daemon.
#
# The api is a stub: busybox's httpd answering /v1/health with a body that
# names the commit baked into the image, the way the real image bakes its
# own (ENV + label). Everything else is the real thing -- the compose
# project, the deploy-<sha> tags, the `current` pointer, --force-recreate,
# --wait, and the health verification -- so this exercises the script the
# way the VPS runs it. CI runs it (ubuntu-latest has docker); by hand:
#
#   bash infra/rollback.test.sh
#
# Without a docker daemon it skips itself, unless ROLLBACK_TEST_REQUIRE_DOCKER
# is set, in which case that is a failure (CI sets it).
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
ROLLBACK="$here/rollback.sh"

if ! docker info >/dev/null 2>&1; then
  if [ -n "${ROLLBACK_TEST_REQUIRE_DOCKER:-}" ]; then
    echo "rollback.test: docker is required here and is not available" >&2
    exit 1
  fi
  echo "rollback.test: no docker daemon; skipping"
  exit 0
fi

PORT="${ROLLBACK_TEST_PORT:-3091}"
export ASR_IMAGE="asr-rollback-test"
export ASR_ROOT
ASR_ROOT=$(mktemp -d)
export ASR_HEALTH_URL="http://127.0.0.1:$PORT/v1/health"
export ASR_WAIT_TIMEOUT=30
export ASR_HEALTH_TRIES=6

cleanup() {
  (cd "$ASR_ROOT" && docker compose down -v --remove-orphans >/dev/null 2>&1) || true
  docker images "$ASR_IMAGE" --format '{{.Repository}}:{{.Tag}}' | xargs -r docker rmi -f >/dev/null 2>&1 || true
  rm -rf "$ASR_ROOT"
}
trap cleanup EXIT

cat > "$ASR_ROOT/docker-compose.yml" <<COMPOSE
name: asr-rollback-test
services:
  api:
    image: $ASR_IMAGE:current
    network_mode: host
    restart: "no"
COMPOSE
mkdir -p "$ASR_ROOT/backups"
touch "$ASR_ROOT/backups/pre-deploy-20260101-000000.dump"

sha() { printf "$1%.0s" $(seq 40); }   # sha a -> 40 a's
A=$(sha a); B=$(sha b); C=$(sha c); D=$(sha d); X=$(sha e)

# build <sha> <tag> [legacy|broken]
#   legacy: no ENV/label, like images from before the commit was baked in
#   broken: exits at once, like a release that cannot start
build() {
  local sha=$1 tag=$2 kind=${3:-}
  local ctx; ctx=$(mktemp -d)
  printf '{"ok":true,"commit":"%s"}' "$sha" > "$ctx/health"
  {
    echo "FROM busybox:1.36"
    echo "COPY health /www/v1/health"
    if [ "$kind" != legacy ]; then
      echo "ENV ASR_COMMIT=$sha"
      echo "LABEL io.joinasr.commit=$sha"
    fi
    echo "HEALTHCHECK --interval=1s --timeout=2s --retries=5 CMD wget -qO- http://127.0.0.1:$PORT/v1/health >/dev/null 2>&1 || exit 1"
    if [ "$kind" = broken ]; then
      echo 'CMD ["false"]'
    else
      echo "CMD [\"httpd\", \"-f\", \"-p\", \"127.0.0.1:$PORT\", \"-h\", \"/www\"]"
    fi
  } > "$ctx/Dockerfile"
  docker build -q -t "$ASR_IMAGE:$tag" "$ctx" >/dev/null
  rm -rf "$ctx"
  sleep 1.1   # the script orders releases by CreatedAt, which has seconds
}

start_from() {  # tag -> make it current and start it, as a deploy would
  docker tag "$ASR_IMAGE:$1" "$ASR_IMAGE:current"
  (cd "$ASR_ROOT" && docker compose up -d --wait --wait-timeout 30 >/dev/null 2>&1)
}

serving() {  # the commit the stub answers with right now
  curl -sS --max-time 3 "$ASR_HEALTH_URL" 2>/dev/null | sed -n 's/.*"commit":"\([^"]*\)".*/\1/p'
}
running_image() {
  local cid; cid=$(cd "$ASR_ROOT" && docker compose ps -q api)
  docker inspect -f '{{.Image}}' "$cid"
}
image_id() { docker images "$ASR_IMAGE:$1" --no-trunc --format '{{.ID}}'; }

passed=0; failed=0
ok()   { passed=$((passed + 1)); echo "  ok   $1"; }
bad()  { failed=$((failed + 1)); echo "  FAIL $1" >&2; }
check() { if eval "$2"; then ok "$1"; else bad "$1"; fi; }

out=""; status=0
run() {  # run the script with the given args; stdin is never a terminal
  set +e
  out=$("$ROLLBACK" "$@" </dev/null 2>&1)
  status=$?
  set -e
}
expect_serving() {
  local got; got=$(serving)
  if [ "$got" = "$1" ]; then ok "serving ${1:0:7}"; else bad "expected ${1:0:7} serving, got '${got:0:7}'"; echo "$out" >&2; fi
}
expect_status() {
  if [ "$status" = "$1" ]; then ok "exit $1: $2"; else bad "expected exit $1 ($2), got $status"; echo "$out" >&2; fi
}
expect_output() {
  case "$out" in *"$1"*) ok "says: $1" ;; *) bad "did not say: $1"; echo "$out" >&2 ;; esac
}

echo "building releases"
build "$A" deploy-aaaaaaa legacy
build "$B" deploy-bbbbbbb
build "$C" deploy-ccccccc
start_from deploy-ccccccc
expect_serving "$C"

echo "--list and --dry-run change nothing"
run --list
expect_status 0 "--list"
expect_output "running:  $C (deploy-ccccccc)"
expect_output "would go to: deploy-bbbbbbb ($B"
run --dry-run
expect_status 0 "--dry-run"
expect_output "dry run; nothing changed"
expect_serving "$C"

echo "no terminal and no --yes: refuses"
run
expect_status 1 "needs --yes"
expect_output "pass --yes"
expect_serving "$C"

echo "rollback to the previous release"
run --yes
expect_status 0 "rollback"
expect_output "now serving $B (deploy-bbbbbbb)"
expect_output "the newest pre-deploy dump is backups/pre-deploy-20260101-000000.dump"
expect_serving "$B"
check "releases.log records it" "grep -q ' rollback $C -> $B (deploy-bbbbbbb)$' '$ASR_ROOT/releases.log'"

echo "a second rollback goes further back, to a legacy image without a baked commit"
run --yes
expect_status 0 "rollback again"
expect_output "now serving commit unknown (deploy-aaaaaaa)"
expect_serving "$A"
check "running the legacy image" "[ \"\$(running_image)\" = \"\$(image_id deploy-aaaaaaa)\" ]"

echo "nothing older: refuses and stays"
run --yes
expect_status 1 "oldest kept"
expect_output "nothing older to go back to"
expect_serving "$A"

echo "an explicit sha can go forward again, short or full"
run --yes ccccccc
expect_status 0 "to ccccccc"
expect_serving "$C"
run --yes "$B"
expect_status 0 "to the full sha"
expect_serving "$B"
run --yes deploy-bbbbbbb
expect_status 1 "already running"
expect_output "already running"

echo "naming the running release when current points at a build that never started"
docker tag "$ASR_IMAGE:deploy-ccccccc" "$ASR_IMAGE:current"
run --yes bbbbbbb
expect_status 0 "pointer repaired"
expect_output "deploy-bbbbbbb is already running; $ASR_IMAGE:current pointed back at it (it was on deploy-ccccccc)"
expect_serving "$B"
check "current points at the running image again" "[ \"\$(image_id current)\" = \"\$(running_image)\" ]"
run --yes bbbbbbb
expect_status 1 "already running, pointer fine"
expect_output "already running; nothing to do"

echo "bad arguments"
run --yes zzz
expect_status 1 "not a commit"
run --yes 1234567
expect_status 1 "unknown release"
expect_output "no kept release for 1234567"
run --yes "bbbbbbb$(sha 0 | cut -c1-33)"
expect_status 1 "full sha that does not match the kept image"
expect_output "was built from $B"
expect_serving "$B"

echo "running something that is not a kept release: back to the newest that served"
build "$X" stray
start_from stray
expect_serving "$X"
run --list
expect_output "running:  $X (not a kept release)"
run --yes
expect_status 0 "from a stray image"
expect_serving "$C"

echo "a target that cannot start: reverted, nothing changed"
build "$D" deploy-ddddddd broken
run --yes ddddddd
expect_status 1 "broken target"
expect_output "did not come up healthy"
expect_output "nothing changed in the end"
expect_serving "$C"
check "current points back at the running image" "[ \"\$(image_id current)\" = \"\$(running_image)\" ]"

echo
echo "rollback.test: $passed passed, $failed failed"
[ "$failed" -eq 0 ]
