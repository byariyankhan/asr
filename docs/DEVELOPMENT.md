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
      pacts.ts, activities.ts, witnesses.ts, notifications.ts
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
- Tests cover: idempotent event ingestion, the one-active-pact rule,
  watchdog transitions, witness scoping, and the snapshot lock rules.

## Android

Requirements: Android Studio (current stable), JDK 17, and a device or
emulator on API 26+ — the usage-access permission flow is hard to exercise
on an old emulator, so prefer a recent image or a real phone.

**Open `android/`, not the repository root.** The Gradle project lives in
that subdirectory. Opening the repository root gives you a file tree with
no app module, no Gradle sync, and a toolbar that says "Add Configuration"
instead of "app" — which looks like a broken project and is really just
the wrong folder. This has already cost someone an evening.

```bash
cd android
./gradlew :app:assembleDebug
./gradlew :app:test
```

`API_BASE_URL` is a `BuildConfig` field, set per build type in
`app/build.gradle.kts`, so it is never a literal in Kotlin. Both build
types currently point at production (`https://api.joinasr.io`); a `dev`
flavor aimed at a local server is worth adding when someone actually runs
the backend locally, and does not exist yet.

Note that some environments used on this project cannot build the app at
all — no Android SDK, and Google's Maven unreachable — so
`.github/workflows/android.yml` is the build of record. If you cannot
compile locally, push a branch and read the CI run; it also uploads an
installable debug APK.

## Infra

`infra/docker-compose.yml` is the production file of record; copy it to
`/opt/asr/docker-compose.yml` on the VPS as `DEPLOYMENT.md` describes.
`infra/docker-compose.dev.yml` runs only Postgres and Redis for local work.
