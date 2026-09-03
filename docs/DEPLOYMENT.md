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

### 1. DNS

`A` records for `joinasr.com` and `api.joinasr.com` → `187.52.122.99`.
Wait until `getent hosts api.joinasr.com` on the VPS returns the IP.

### 2. Directory and checkout

```bash
mkdir -p /opt/asr/backups
git clone https://github.com/byariyankhan/asr /opt/asr/src
cp /opt/asr/src/infra/docker-compose.yml /opt/asr/docker-compose.yml
cp /opt/asr/src/infra/backup.sh /opt/asr/backup.sh && chmod 700 /opt/asr/backup.sh
```

### 3. Environment

`/opt/asr/.env` (mode 600). Generate secrets with `openssl rand -base64 32`.

```
PG_PASS=
REDIS_PASS=
BETTER_AUTH_SECRET=
BETTER_AUTH_URL=https://api.joinasr.com
PUBLIC_SITE_URL=https://joinasr.com
RESEND_API_KEY=
EMAIL_FROM=Asr <noreply@joinasr.com>
FIREBASE_PROJECT_ID=
FIREBASE_CLIENT_EMAIL=
FIREBASE_PRIVATE_KEY=           # keep the \n escapes; the app unescapes them
PLAY_PACKAGE_NAME=com.joinasr.app
PLAY_SERVICE_ACCOUNT_JSON_B64=  # base64 of the service-account JSON for the Play Developer API
```

`docker compose config -q` must pass before anything else; it fails on any
missing variable.

### 4. Build, migrate, start

```bash
cd /opt/asr
docker compose build api migrate
docker compose up -d postgres redis
docker compose run --rm migrate
docker compose up -d api
docker compose ps
wget -qO- http://127.0.0.1:3001/v1/health
```

### 5. nginx

```bash
cp /opt/asr/src/infra/nginx/asr-api /etc/nginx/sites-available/asr-api
ln -s /etc/nginx/sites-available/asr-api /etc/nginx/sites-enabled/asr-api
nginx -t && systemctl reload nginx
certbot --nginx -d api.joinasr.com
```

The landing page and the `/w/<code>` fallback are served by the same API
container under the `joinasr.com` host; add that site the same way when the
page exists (`infra/nginx/asr-site`).

### 6. Backups

```bash
(crontab -l; echo "30 2 * * * /opt/asr/backup.sh >> /opt/asr/backups/backup.log 2>&1") | crontab -
```

`backup.sh` does `pg_dump -Fc` inside the container, keeps the last 14 dumps
in `/opt/asr/backups/`, and (once credentials are set) pushes to the same
offsite bucket Bookween uses under an `asr/` prefix. Bookween's backup runs
at 02:00; this runs at 02:30.

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
