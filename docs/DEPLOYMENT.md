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

`infra/first-time-setup.sh` does all of this. On the VPS, as root:

```bash
curl -fsSL https://raw.githubusercontent.com/byariyankhan/asr/master/infra/first-time-setup.sh -o setup.sh
bash setup.sh /root/firebase-key.json
```

The argument is the Firebase Admin SDK service-account JSON (Firebase
Console → Project settings → Service accounts → Generate new private key).
The script reads it once, folds the three fields into `.env`, and tells you
to delete the file afterwards.

It checks DNS resolves and that ports 5433/6380/3001 are free before
touching anything, never overwrites an existing `.env`, and leaves
`/opt/asr` in a running state with the nightly backup scheduled. Two manual
steps remain, which it prints: `certbot --nginx -d api.joinasr.io`, and
deleting the key file.

What it does, for when you need to do a piece of it by hand:

### 1. DNS

`A` records for `joinasr.io` and `api.joinasr.io` → `187.52.122.99`.
Wait until `getent hosts api.joinasr.io` on the VPS returns the IP.

### 2. Directory and checkout

```bash
mkdir -p /opt/asr/backups
git clone https://github.com/byariyankhan/asr /opt/asr/src
cp /opt/asr/src/infra/docker-compose.yml /opt/asr/docker-compose.yml
install -m 700 /opt/asr/src/infra/backup.sh /opt/asr/backup.sh
```

### 3. Environment

`/opt/asr/.env`, mode 600. Generate each secret with
`openssl rand -base64 32`.

```
PG_PASS=
REDIS_PASS=
BETTER_AUTH_SECRET=
INTERNAL_SECRET=            # x-internal-secret header for POST /v1/internal/watchdog
BETTER_AUTH_URL=https://api.joinasr.io
PUBLIC_SITE_URL=https://joinasr.io
EMAIL_FROM=Asr <noreply@joinasr.io>
RESEND_API_KEY=             # empty: emails are logged, not sent
FIREBASE_PROJECT_ID=
FIREBASE_CLIENT_EMAIL=
FIREBASE_PRIVATE_KEY=       # see below
PLAY_PACKAGE_NAME=io.joinasr.app
PLAY_SERVICE_ACCOUNT_JSON_B64=   # empty: /v1/subscription/verify answers 503
PLAY_PUBSUB_SECRET=              # the ?token= value on the Pub/Sub push endpoint
```

`FIREBASE_PRIVATE_KEY` must be **one line, single-quoted, with literal
`\n`** where the key has newlines. Compose keeps single-quoted values
verbatim (double quotes would turn `\n` into a real newline and break the
line); `server/fcm.ts` converts them back. From the service-account JSON:

```bash
python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); print("FIREBASE_PRIVATE_KEY=\x27" + d["private_key"].replace(chr(10), "\\n") + "\x27")' key.json
```

`docker compose config -q` must pass before anything else; it fails on any
missing variable.

### 4. Build, migrate, start

```bash
cd /opt/asr
docker compose build api
docker compose build migrate
docker compose up -d postgres redis
docker compose run --rm migrate
docker compose up -d api
wget -qO- http://127.0.0.1:3001/v1/health
```

### 5. nginx and TLS

```bash
cp /opt/asr/src/infra/nginx/asr-api /etc/nginx/sites-available/asr-api
ln -s /etc/nginx/sites-available/asr-api /etc/nginx/sites-enabled/asr-api
nginx -t && systemctl reload nginx
certbot --nginx -d api.joinasr.io
```

The landing page and the `/w/<code>` fallback are served by the same API
container under the `joinasr.io` host; add that site the same way when the
page exists (`infra/nginx/asr-site`).

### 6. Backups

```bash
(crontab -l; echo "30 2 * * * /opt/asr/backup.sh >> /opt/asr/backups/backup.log 2>&1") | crontab -
```

`backup.sh` does `pg_dump -Fc` inside the container, keeps the last 14 dumps
in `/opt/asr/backups/`, and (once credentials are set) pushes to the same
offsite bucket Bookween uses under an `asr/` prefix. Bookween's backup runs
at 02:00; this runs at 02:30.

### 7. GitHub secrets

For the deploy workflow: `VPS_SSH_KEY` (a private key whose public half is
in the VPS's `authorized_keys`) and `VPS_HOST` (`187.52.122.99`). The host
key is pinned in `infra/vps-host-key.pub`, not scanned.

## Every deploy

Same shape as Bookween's `deploy.yml`; lives in this repo's
`.github/workflows/deploy.yml` once the backend exists:

```bash
cd /opt/asr/src && git fetch origin main && git reset --hard origin/main
cd /opt/asr
docker compose build api migrate
docker compose exec -T postgres pg_dump -U asr -d asr -Fc > backups/pre-deploy-$(date -u +%Y%m%d-%H%M%S).dump
ls -t backups/pre-deploy-*.dump | tail -n +6 | xargs -r rm
docker compose run --rm migrate
docker compose up -d api --force-recreate
docker image prune -f
```

Rollback: `git reset --hard <previous sha>`, rebuild, restore the pre-deploy
dump if the migration was not backward compatible.

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
