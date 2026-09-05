#!/usr/bin/env bash
# Roll the API back to a release that has already served, in one command.
#
#   rollback.sh               the release before the one running
#   rollback.sh <sha>         that release (full or 7-character sha), if kept
#   rollback.sh --list        what is kept, newest first; changes nothing
#   rollback.sh --dry-run     what a rollback would do now; changes nothing
#   rollback.sh --yes ...     do not ask first (no terminal, or a workflow)
#
# Version of record: infra/rollback.sh. The deploy and first-time-setup.sh
# install it at /opt/asr/rollback.sh; the "Rollback production" workflow runs
# it over SSH, or run it by hand on the VPS.
#
# Every deploy leaves the image it started as asr-api:deploy-<sha> (the newest
# five are kept; a deploy that never came up healthy is not tagged) and points
# asr-api:current at the live one. A rollback re-points `current` at a kept
# image and recreates the api container from it -- nothing is built -- then
# waits until the container is healthy and /v1/health answers with that
# image's commit. If the target never comes up, `current` is pointed back at
# the image that was running and that one is recreated, so a failed rollback
# does not leave the API down.
#
# It does not touch the database. The schema stays where the newest release
# migrated it, and that is safe because a migration must keep working with
# the previous release's code (docs/DEPLOYMENT.md, "Rollback"). The pre-deploy
# dumps are in backups/ if a schema really has to go back.
set -euo pipefail

ROOT="${ASR_ROOT:-/opt/asr}"
IMAGE="${ASR_IMAGE:-asr-api}"
HEALTH_URL="${ASR_HEALTH_URL:-http://127.0.0.1:3001/v1/health}"
WAIT_TIMEOUT="${ASR_WAIT_TIMEOUT:-180}"
HEALTH_TRIES="${ASR_HEALTH_TRIES:-24}"
COMMIT_LABEL="io.joinasr.commit"

say() { echo "rollback: $*"; }
die() { echo "rollback: $*" >&2; exit 1; }
usage() { sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'; }

mode=rollback
yes=""
want=""
for arg in "$@"; do
  case "$arg" in
    --list) mode=list ;;
    --dry-run) mode=dry-run ;;
    --yes|-y) yes=1 ;;
    -h|--help) usage; exit 0 ;;
    -*) die "unknown option $arg" ;;
    *) [ -z "$want" ] || die "one commit at most"; want="$arg" ;;
  esac
done
if [ -n "$want" ]; then
  want="${want#deploy-}"
  [[ "$want" =~ ^[0-9a-f]{7,40}$ ]] || die "not a commit: $want"
fi

cd "$ROOT"

# One line per kept release: created|tag|image id|commit. Newest first;
# CreatedAt sorts as text because docker prints every one in the same zone.
created_of() { cut -d'|' -f1 <<<"$1"; }
tag_of()     { cut -d'|' -f2 <<<"$1"; }
id_of()      { cut -d'|' -f3 <<<"$1"; }
commit_of()  { cut -d'|' -f4 <<<"$1"; }

# The commit baked into an image; empty for images from before the label.
image_commit() {
  docker inspect -f "{{index .Config.Labels \"$COMMIT_LABEL\"}}" "$1" 2>/dev/null || true
}

kept=()
while IFS= read -r line; do
  [ -n "$line" ] || continue
  kept+=("$line|$(image_commit "${line##*|}")")
done < <(docker images "$IMAGE" --no-trunc --format '{{.CreatedAt}}|{{.Tag}}|{{.ID}}' \
           | awk -F'|' '$2 ~ /^deploy-/' | sort -r)

# The container serving now, the image it was created from, and the commit
# it reports (its environment: baked into new images, set at start on old).
running_cid=$(docker compose ps -q api 2>/dev/null | head -n1 || true)
[ -n "$running_cid" ] || running_cid=$(docker compose ps -q -a api 2>/dev/null | head -n1 || true)
running_img=""
running_commit=""
if [ -n "$running_cid" ]; then
  running_img=$(docker inspect -f '{{.Image}}' "$running_cid")
  running_commit=$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$running_cid" \
                     | sed -n 's/^ASR_COMMIT=//p')
  running_commit="${running_commit%%$'\n'*}"
fi
current_img=$(docker images "$IMAGE:current" --no-trunc --format '{{.ID}}' | head -n1 || true)

tag_for_id() {
  local l
  for l in ${kept[@]+"${kept[@]}"}; do
    if [ "$(id_of "$l")" = "$1" ]; then tag_of "$l"; return; fi
  done
  echo "not a kept release"
}

# Picks the release to go to. Sets `target` (a kept line) or `reason`.
target=""
reason=""
choose() {
  local l short passed running_is_kept c
  if [ -n "$want" ]; then
    short="${want:0:7}"
    for l in ${kept[@]+"${kept[@]}"}; do
      if [ "$(tag_of "$l")" = "deploy-$short" ]; then target="$l"; break; fi
    done
    [ -n "$target" ] || { reason="no kept release for $want"; return; }
    c=$(commit_of "$target")
    if [ -n "$c" ] && [[ "$c" != "$want"* ]]; then
      reason="$IMAGE:deploy-$short was built from $c, not $want"; target=""; return
    fi
    return
  fi

  running_is_kept=""
  for l in ${kept[@]+"${kept[@]}"}; do
    [ "$(id_of "$l")" = "$running_img" ] && running_is_kept=1
  done
  if [ -n "$running_is_kept" ]; then
    passed=""
    for l in ${kept[@]+"${kept[@]}"}; do
      if [ "$(id_of "$l")" = "$running_img" ]; then passed=1; continue; fi
      if [ -n "$passed" ]; then target="$l"; return; fi
    done
    reason="the running release is the oldest one kept; nothing older to go back to"
    return
  fi
  # The running image is not a kept release: a deploy that never came up
  # healthy (releases are tagged only after that), or one from before
  # releases were kept. Back to the newest release that did serve.
  for l in ${kept[@]+"${kept[@]}"}; do
    if [ "$(id_of "$l")" != "$running_img" ]; then target="$l"; return; fi
  done
  reason="no kept release to go back to"
}
choose

describe() { printf '%s (%s, built %s)' "$(tag_of "$1")" "$(commit_of "$1" | grep . || echo "commit unknown")" "$(created_of "$1" | cut -c1-19)"; }

list() {
  if [ -n "$running_cid" ]; then
    echo "running:  ${running_commit:-commit unknown} ($(tag_for_id "$running_img"))"
  else
    echo "running:  nothing; there is no api container"
  fi
  echo "current:  $IMAGE:current -> $(tag_for_id "${current_img:-none}")"
  echo "kept releases, newest first:"
  local l mark
  for l in ${kept[@]+"${kept[@]}"}; do
    mark=" "; [ "$(id_of "$l")" = "$running_img" ] && mark="*"
    printf '  %s %-16s %-40s %s\n' "$mark" "$(tag_of "$l")" "$(commit_of "$l" | grep . || echo "commit unknown")" "$(created_of "$l" | cut -c1-19)"
  done
  [ ${#kept[@]} -gt 0 ] || echo "  (none)"
  if [ -n "$target" ]; then
    echo "a rollback ${want:+to $want }would go to: $(describe "$target")"
  else
    echo "a rollback ${want:+to $want }is not possible: $reason"
  fi
}

case "$mode" in
  list) list; exit 0 ;;
  dry-run) list; [ -n "$target" ] || exit 1; say "dry run; nothing changed"; exit 0 ;;
esac

[ -n "$target" ] || die "$reason (see --list)"
target_tag=$(tag_of "$target")
target_id=$(id_of "$target")
target_commit=$(commit_of "$target")
if [ -n "$running_img" ] && [ "$target_id" = "$running_img" ]; then
  # Already running, but `current` may point elsewhere: a deploy that built
  # and then failed before starting leaves it on a build that never served,
  # and the next plain `up -d` would start that. Naming the running release
  # puts the pointer back on it; nothing is recreated.
  if [ "$current_img" != "$running_img" ]; then
    docker tag "$IMAGE:$target_tag" "$IMAGE:current"
    say "$target_tag is already running; $IMAGE:current pointed back at it (it was on $(tag_for_id "${current_img:-none}"))"
    exit 0
  fi
  die "$target_tag is the release already running; nothing to do"
fi

say "running  ${running_commit:-commit unknown} ($(tag_for_id "${running_img:-none}"))"
say "target   $(describe "$target")"
if [ -z "$yes" ]; then
  if [ -t 0 ]; then
    read -r -p "Roll back now? [y/N] " answer
    case "$answer" in y|Y|yes|YES) ;; *) say "nothing changed"; exit 1 ;; esac
  else
    die "no terminal to confirm on; pass --yes"
  fi
fi

health() {
  if command -v wget >/dev/null 2>&1; then
    wget -qO- --timeout=5 "$HEALTH_URL" 2>/dev/null && return
  fi
  if command -v curl >/dev/null 2>&1; then
    curl -sS --max-time 5 "$HEALTH_URL" 2>/dev/null && return
  fi
  return 1
}

# The image that was running is what a failed rollback goes back to. Kept
# by ID, because `current` is about to point elsewhere.
revert() {
  [ -n "$running_img" ] || { say "there was nothing running before; leaving it"; return; }
  say "pointing $IMAGE:current back at what was running and recreating it"
  docker tag "$running_img" "$IMAGE:current"
  if docker compose up -d api --no-build --force-recreate --wait --wait-timeout "$WAIT_TIMEOUT"; then
    say "the previous container is back up; nothing changed in the end"
  else
    say "the previous container did not come back either; look at: docker compose ps; docker compose logs api"
  fi
}

say "pointing $IMAGE:current at $target_tag and recreating api"
docker tag "$IMAGE:$target_tag" "$IMAGE:current"
if ! docker compose up -d api --no-build --force-recreate --wait --wait-timeout "$WAIT_TIMEOUT"; then
  say "the rolled-back container did not come up healthy; its logs:"
  docker compose logs --tail 100 api || true
  revert
  exit 1
fi

# Healthy is not enough; it has to be THIS image answering. The container's
# image is the proof, and the commit in the health body agrees with it when
# the image knows its commit.
verified=""
for _ in $(seq 1 "$HEALTH_TRIES"); do
  body=$(health || true)
  cid=$(docker compose ps -q api 2>/dev/null | head -n1 || true)
  img=""; [ -n "$cid" ] && img=$(docker inspect -f '{{.Image}}' "$cid")
  if [ "$img" = "$target_id" ] && [[ "$body" == *'"ok":true'* ]] \
     && { [ -z "$target_commit" ] || [[ "$body" == *"\"commit\":\"$target_commit\""* ]]; }; then
    verified=1
    break
  fi
  sleep 5
done
if [ -z "$verified" ]; then
  say "api is up but /v1/health does not answer for $target_tag: ${body:-<no answer>}"
  revert
  exit 1
fi

printf '%s rollback %s -> %s (%s)\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  "${running_commit:-unknown}" "${target_commit:-unknown}" "$target_tag" >> releases.log

say "now serving ${target_commit:-commit unknown} ($target_tag)"
newest_dump=$(ls -t backups/pre-deploy-*.dump 2>/dev/null | head -n1 || true)
say "the database was not touched${newest_dump:+; the newest pre-deploy dump is $newest_dump}"
