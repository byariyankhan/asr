# Development

## Backend

Requirements: Node 22, pnpm (via `corepack enable`), Docker.

```bash
cd backend
cp .env.example .env            # local values; never commit .env
docker compose -f ../infra/docker-compose.dev.yml up -d   # postgres :5433, redis :6380, local only
pnpm install
pnpm db:migrate
pnpm dev                        # http://localhost:3001
```

Checks that must pass before a push (CI runs the same):

```bash
pnpm lint
pnpm type-check
pnpm test
pnpm build
```

### Layout

```
backend/
  src/
    app/
      api/auth/[...all]/route.ts     Better Auth handler
      v1/...                         one folder per resource, route.ts each
    server/
      db/            kysely instance, migrate.ts, migrations/, types
      auth.ts        Better Auth config (bearer plugin, email via Resend)
      redis.ts
      rate-limit.ts
      fcm.ts         Firebase Admin wrapper; maps UNREGISTERED to device flag
      play.ts        Play Developer API verification
      watchdog.ts    the 15-minute job
      commitments.ts, challenges.ts, witnesses.ts, notifications.ts
    lib/
      uuid.ts        uuidv7() and isUuidLike()
      time.ts        timezone helpers (day boundaries in an IANA zone)
      schemas.ts     Zod schemas shared by routes and tests
  test/              vitest; DB tests run against the dev Postgres
```

### Conventions

- One migration per change, SQL in a Kysely `sql` template, with a `down`.
- Every route: parse with Zod, check ownership, then call a function in
  `src/server/*`. Route files contain no business logic.
- Every server function that writes takes the caller's user id as its first
  argument and scopes the query with it.
- Device-originated ids are validated with `isUuidLike` before touching the
  database.
- Tests cover: idempotent event ingestion, the one-active-commitment rule,
  watchdog transitions, witness scoping, and the snapshot lock rules.

## Android

Requirements: Android Studio (current stable), JDK 17, an Android 13+ device
or emulator (the usage-access permission flow is hard to test on old
emulators).

```bash
cd android
./gradlew :app:assembleDevDebug
./gradlew :app:testDevDebugUnitTest
```

The `dev` flavor points at `http://10.0.2.2:3001` (emulator → host). Set a
LAN IP in `local.properties` (`asr.devApiUrl`) for a physical device.

## Infra

`infra/docker-compose.yml` is the production file of record; copy it to
`/opt/asr/docker-compose.yml` on the VPS as `DEPLOYMENT.md` describes.
`infra/docker-compose.dev.yml` runs only Postgres and Redis for local work.
