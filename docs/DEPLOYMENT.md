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

Add four repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `VPS_SSH_KEY` | a private key whose public half is in the VPS's `root` `authorized_keys` |
| `VPS_HOST` | `187.52.122.99` |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | the whole Admin SDK JSON, pasted as-is |
| `CERTBOT_EMAIL` | optional; set it and TLS is issued unattended |

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
2. **Installs** `docker-compose.yml` and `backup.sh` into `/opt/asr`.
3. **Writes `/opt/asr/.env`** (mode 600) with `PG_PASS`, `REDIS_PASS`,
   `BETTER_AUTH_SECRET` and `INTERNAL_SECRET` from `openssl rand`, the URLs,
   and the three Firebase fields read out of the service-account JSON.
   `RESEND_API_KEY` and the `PLAY_*` values are left empty, which is a
   working state: email is logged instead of sent, and
   `/v1/subscription/verify` answers 503.
4. **Builds** `api` then `migrate` (sequentially — see the deploy job's
   comment for why), **migrates**, and **starts** the stack.
5. **Waits for `/v1/health`** to actually answer before going on, so
   "the script finished" and "the API works" are the same statement.
6. **Installs the nginx site**, and issues the certificate if
   `CERTBOT_EMAIL` is set.
7. **Schedules the nightly backup** at 02:30 (Bookween's is at 02:00).

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

Then it tags the live image `asr-api:deploy-<sha>`, keeps the newest three,
and prunes dangling images and build cache over 5GB — all after the new
container is up, and in a block that cannot fail the deploy.

Finally it fetches `https://api.joinasr.io/v1/health` from the public
internet. A deploy that does not end in a healthy answer is a failed deploy.

**Rollback.** To a kept image:

```bash
docker tag asr-api:deploy-<sha> <the compose image tag>
docker compose up -d api --force-recreate
```

Further back: re-run the workflow from an older commit. Neither undoes a
migration — restore a pre-deploy dump from `/opt/asr/backups` if the schema
changed.

## Resource budget

Measured on Bookween's stack: Postgres idle ≈ 40 MB, Redis ≈ 10 MB, a Next.js
standalone API ≈ 150–250 MB. Asr adds roughly 300 MB to a machine with about
6.5 GB free. Nothing to tune until the VPS graph moves.

## Monitoring

- `GET /v1/health` checked externally every minute (same uptime monitor as
  Bookween, separate check).
- `docker compose logs -f api` for errors; the API logs one JSON line per
  request with user id, route, status, duration.
- The watchdog writes its last successful run time to Redis; `/v1/health`
  reports `watchdog_stale: true` if that is older than 30 minutes.
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
