# Deployment

Asr runs on the same VPS as Bookween for now, in its own directory, its own
compose project, its own PostgreSQL and Redis containers, and its own nginx
site. Nothing under `/opt/bookween` is modified, and Bookween's
`check-infra-drift` keeps passing because its compose file is untouched.

```
/opt/bookween/                 (existing, not touched)
  docker-compose.yml           postgres :5432, redis :6379, web :3000
/opt/asr/                      (new)
  docker-compose.yml           postgres :5433, redis :6380, api :3001, migrate
  .env
  backup.sh
  rollback.sh                  one command back to a kept release
  releases.log                 one line per deploy and per rollback
  src/                         git checkout of byariyankhan/asr
  backups/
```

Host port bindings are all `127.0.0.1:` and never collide. Container names
are prefixed by the compose project name (`asr-postgres-1`, ...), so the two
stacks cannot be confused on the Docker side either.

## First-time setup

The repository is private, so the VPS is never given GitHub credentials:
the GitHub Actions runner has the checkout and ships it over SSH. Nothing on
the VPS ever clones or pulls.

Add these repository secrets (Settings → Secrets and variables → Actions).
Two are required and the rest each switch on one thing; a missing optional
one degrades that feature and nothing else. `VPS_HOST` and `VPS_SSH_KEY` are
read by `deploy.yml`, the rest by `bootstrap.yml` (which writes `/opt/asr/.env`)
and `android.yml`:

| Secret | Value |
|---|---|
| `VPS_SSH_KEY` | a private key whose public half is in the VPS's `root` `authorized_keys` |
| `VPS_HOST` | `187.52.122.99` |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | the whole Admin SDK JSON, pasted as-is |
| `CERTBOT_EMAIL` | optional; set it and TLS is issued unattended |
| `RESEND_API_KEY` | optional; set it and the bootstrap writes it into `.env` |
| `ANDROID_CERT_SHA256` | optional; the app's signing certificate fingerprints, comma-separated. Served at `/.well-known/assetlinks.json`, and without it an invitation link opens a browser instead of the app. The Android CI job prints the debug one at the end of every run |
| `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET` | optional; Cloudflare R2 for profile photos. Without them `POST /v1/me/avatar` answers 503. **See the shapes below** — three of these four are hex strings of different lengths and the page they are copied from also shows an API token, which is none of them |

### Before pushing backend changes

```
cd backend && pnpm test:db
```

`pnpm test` alone skips every test that needs a database — `describe.skipIf`
reports them as *skipped* and exits 0, which is more than a third of the
suite. `test:db` starts a throwaway Postgres, migrates it and runs
everything, so it fails where CI fails.

And **look at CI, not only at Deploy**. Deploy is gated on CI passing, so a
red CI shows up as a deploy that was *skipped* rather than one that failed —
which is easy to read as "nothing to deploy". Five commits once went out
that way.

### What the four R2 values look like

Cloudflare's "Manage API token" page shows four things at once and only two
of them belong here. The photo upload failed for days on an API token pasted
into `R2_ACCOUNT_ID`, so:

| Variable | Shape | Where it is |
|---|---|---|
| `R2_ACCOUNT_ID` | 32 hex characters, nothing else | R2 → Overview, right-hand column; also the first label of the S3 endpoint and the id in the dashboard URL |
| `R2_ACCESS_KEY_ID` | 32 hex characters | "Access Key ID" on the token page |
| `R2_SECRET_ACCESS_KEY` | 64 hex characters | "Secret Access Key" on the token page |
| `R2_BUCKET` | the bucket's name | R2 → Overview |

The **token value** on that page — the long string beginning `cfat_` — is
used by Cloudflare's own API and by nothing here. It is not the account id,
and it is a credential: if it ends up in the wrong variable, roll it rather
than moving it.

`GET /v1/health/storage` writes and deletes an object and says which of these
is wrong before anybody has to read a log:
`{"configured":true,"writable":false,"error":"account_id_is_not_a_hex_id"}`.
A pasted S3 endpoint URL is accepted and the id parsed out of it, because
that is the one thing the dashboard makes easy to copy.

Then run **Actions → Bootstrap the VPS → Run workflow**. It ships the source
to `/opt/asr/src`, hands the Firebase key over on stdin (never on a command
line, where `ps` would show it), runs `infra/first-time-setup.sh`, shreds the
key file, and finishes by fetching `https://api.joinasr.io/v1/health` from
the public internet. The host key is pinned from `infra/vps-host-key.pub`, so
a changed key fails the run rather than being accepted.

The script it runs is idempotent and refuses to overwrite an existing
`.env`, so re-running the workflow is safe.

### What the script does

1. **Checks its preconditions**: DNS resolves for `joinasr.io` and
   `api.joinasr.io`, and reports on ports 5433/6380/3001.
2. **Installs** `docker-compose.yml`, `backup.sh` and `rollback.sh` into
   `/opt/asr`.
3. **Writes `/opt/asr/.env`** (mode 600) with `PG_PASS`, `REDIS_PASS`,
   `BETTER_AUTH_SECRET` and `INTERNAL_SECRET` from `openssl rand`, the URLs,
   and the three Firebase fields read out of the service-account JSON.
   `RESEND_API_KEY` and the `PLAY_*` values are left empty, which is a
   working state: email is logged instead of sent, and
   `/v1/subscription/verify` answers 503.

   The script writes `.env` once and never touches it again — it must not be
   able to clobber live credentials — so a secret added later needs another
   way in. That is the workflow's **Fill in RESEND_API_KEY** step: it
   rewrites that one line, leaves the rest of the file byte for byte, then
   recreates `api` and proves the running container has the value (its
   length, never the value) and still answers `/v1/health`. It refuses a
   value that is not key-shaped, because a stray quote or newline in `.env`
   silently corrupts every variable after it.
4. **Builds** `api` then `migrate` (sequentially — see the deploy job's
   comment for why), **migrates**, and **starts** the stack.
5. **Waits for `/v1/health`** to actually answer before going on, so
   "the script finished" and "the API works" are the same statement.
6. **Installs the nginx site**, and issues the certificate if
   `CERTBOT_EMAIL` is set. Two guards sit around certbot, because
   `certbot --nginx` edits whichever server block claims the hostname and
   falls back to the default server when none does: it refuses to run
   unless nginx really serves `api.joinasr.io`, and afterwards it proves
   no site file but `asr-api` changed. The first bootstrap that reached
   this step had `api.joinasr.com` still written in the site file, and
   certbot deployed Asr's certificate into Bookween's.
7. **Schedules the nightly backup** at 02:30 by writing `/etc/cron.d/asr-backup`
   (Bookween's is at 02:00, scheduled the same way).

### `FIREBASE_PRIVATE_KEY`, if you ever write `.env` by hand

One line, single-quoted, with literal `\n` where the key has newlines.
Compose keeps single-quoted values verbatim; double quotes would turn `\n`
into a real newline and break the line. `server/fcm.ts` converts them back.

```bash
python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); print("FIREBASE_PRIVATE_KEY=\x27" + d["private_key"].replace(chr(10), "\\n") + "\x27")' key.json
```

`docker compose config -q` must pass before anything else; it fails on any
missing variable.

## Every deploy

A push to `master` runs CI; a green CI run triggers `deploy.yml`, which
deploys **the commit CI passed**, not whatever `master` points at by then.

It ships the checkout to `/opt/asr/src` (unpacking beside the live tree and
swapping it in, so a half-finished transfer is never live, and `src.old`
stays one `mv` away), then over SSH:

```bash
cd /opt/asr
docker compose build api
docker compose build migrate
docker compose exec -T postgres pg_dump -U asr -d asr -Fc > backups/pre-deploy-$(date -u +%Y%m%d-%H%M%S).dump
ls -t backups/pre-deploy-*.dump | tail -n +6 | xargs -r rm
docker compose run --rm migrate
docker compose up -d api --force-recreate
```

Then it keeps the image that came up as `asr-api:deploy-<sha>` (the newest
five stay; a build that never came up healthy is not kept, so every kept tag
is a release that served), notes the deploy in `/opt/asr/releases.log`, and
prunes older releases, dangling images and build cache over 5GB — all after
the new container is up, and in a block that cannot fail the deploy.

Finally it fetches `https://api.joinasr.io/v1/health` from the public
internet. A deploy that does not end in a healthy answer **for this commit**
is a failed deploy. The commit is baked into the image by the Dockerfile (an
`ASR_COMMIT` build arg, kept as an ENV and a label), so the answer can only
be right if the container really was recreated from the new build.

## Rollback

One command, on the VPS:

```bash
/opt/asr/rollback.sh            # the release before the one running
/opt/asr/rollback.sh <sha>      # a specific kept release, full or 7-char sha
/opt/asr/rollback.sh --list     # what is kept, and what a rollback would do
```

Or without a shell: **Actions → Rollback production → Run workflow**, with
the commit left empty (the previous release) or filled in, and *dry run*
ticked to only look. It runs the same script over the deploy's pinned SSH
and then checks `/v1/health` from the public internet the way a deploy does.
It shares the deploy's concurrency group, so the two never overlap.

What it does: re-points `asr-api:current` at the kept image, recreates
`api` from it without building anything, and waits until the container is
healthy and `/v1/health` answers with that image's commit. A target that
does not come up is undone — `current` goes back to the image that was
running and that one is recreated — so a failed rollback leaves the API
where it was, not down. Every rollback is a line in `/opt/asr/releases.log`.
With no argument it only ever goes further back; an explicit `<sha>` can go
forward again. `infra/rollback.test.sh` runs the whole thing against a real
docker daemon in CI, with a stub API standing in for the image.

What it does not do: **touch the database.** The schema stays where the
newest release migrated it. That is safe because of the rule below; if a
schema really has to go back, the choices are, from least to most loss:

1. Leave it. An additive migration is harmless to the older code.
2. `docker compose run --rm migrate pnpm db:migrate:down` undoes the newest
   migration. Whatever was written into what it drops is gone.
3. Restore the pre-deploy dump from `/opt/asr/backups`. Everything written
   since that deploy is gone.

**A migration must keep working with the previous release's code.** Add a
column, table or index in one release and start writing it in that same
release; rename or drop only in a later one, once nothing that runs reads
the old shape. That is what makes an image-only rollback always safe, and
it is also what keeps the seconds between `migrate` and the container swap
from failing requests, because the old container is still serving while the
migration runs. CI's up/down/up is the other half: a migration whose `down`
fails cannot be undone at all.

## Resource budget

Measured on Bookween's stack: Postgres idle ≈ 40 MB, Redis ≈ 10 MB, a Next.js
standalone API ≈ 150–250 MB. Asr adds roughly 300 MB to a machine with about
6.5 GB free. Nothing to tune until the VPS graph moves.

## Monitoring

- `GET /v1/health` checked externally every minute (same uptime monitor as
  Bookween, separate check).
- `docker compose logs -f api`: the API writes one JSON line per `/v1` and
  `/api/auth` request -- time, method, path, status, duration, and the user
  id once a session was resolved (`backend/src/lib/request-log.ts`). Healthy
  answers to the health endpoints are not logged; query strings never are,
  and an invite code in a path is masked. Container logs are capped by the
  compose file at five files of 20 MB per service.
- The watchdog writes its last successful run time to Redis; `/v1/health`
  reports `watchdog_stale: true` if that is older than 30 minutes.
- `/opt/asr/releases.log`: one line per deploy and per rollback, with the
  commits, so "what was running last Tuesday" has an answer.
- Backup failure alerts reuse Bookween's `alert.sh` (email), with a distinct
  subject.

## Moving to a dedicated server later

1. Provision the new VPS, install Docker and nginx, copy `/opt/asr` minus
   `backups/` (the Postgres volume is not copied; the dump is).
2. On the old server: `docker compose exec -T postgres pg_dump -U asr -d asr -Fc > asr-move.dump`.
3. On the new server: `docker compose up -d postgres`, then
   `docker compose exec -T postgres pg_restore -U asr -d asr --clean --if-exists < asr-move.dump`.
4. Start `api`, run `certbot`, verify `/v1/health` on the new IP directly.
5. Lower DNS TTL a day before; switch `A` records; watch both servers' logs
   for an hour.
6. Stop the old stack; keep `/opt/asr` there for a week; then remove.

Redis does not need to move. Nothing on the Bookween side changes.
