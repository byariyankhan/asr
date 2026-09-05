# Asr

**Asr - Protect Your Time & Focus.**

Store listing name: **Asr - Protect Your Time & Focus**. Operated by Ariyan
Khan, Dhaka; contact `hi@ariyankhan.com`. The launcher label stays the one
word, because a home screen truncates anything longer.

Asr is an Android app for people who want to use certain apps less and cannot
do it alone. You pick the apps, set daily limits, lock them behind a
pact (7, 14, 21 or 30 days, or a custom length), and name one or more **witnesses**. If you break the
pact, disable protection, or uninstall the app, your witnesses are told.
If you keep it, they are told that too.

The name comes from Surah Al-Asr (Quran 103), a short chapter about time
running out and people saving each other from loss by reminding one another of
truth and patience. That is the whole product: a promise, a limit, and people
who hold you to it.

## What this repository is

| Path | Contents |
|---|---|
| `backend/` | Next.js API (`api.joinasr.io`): accounts, pact ledger, witnesses, notifications |
| `android/` | Kotlin + Jetpack Compose app: the whole product, enforcement loop included — see `docs/ANDROID.md` |
| `docs/` | Design and operations documents (start with `ARCHITECTURE.md`) |
| `infra/` | Production `docker-compose.yml`, nginx site, backup and rollback scripts: version of record for the VPS |

## Read this before writing code

1. [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md): what runs on the phone, what runs on the server, and why. The **data boundary** section is the most important decision in the project.
2. [`docs/ENFORCEMENT.md`](docs/ENFORCEMENT.md): the rules this product is. What ends a challenge and what does not, why one account runs on one phone, how often the loop looks and why, and how an uninstall is told apart from a phone that is switched off. Several of these were got wrong first; the last section lists what must not come back.
3. [`docs/DATABASE.md`](docs/DATABASE.md): the server tables and the migration convention.
4. [`docs/API.md`](docs/API.md): every endpoint the Android app calls.
5. [`docs/ANDROID.md`](docs/ANDROID.md): enforcement loop, permissions, Play policy, offline queue.
6. [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md): how it runs on the VPS next to Bookween without touching Bookween, and how it moves to its own server later.
7. [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md): local setup and conventions.
8. [`docs/DESIGN.md`](docs/DESIGN.md): the colours, type and measures, read out of
   Figma. Anything that needs a colour uses these and invents nothing.
9. [`docs/FIGMA_SCREENS.md`](docs/FIGMA_SCREENS.md): every screen's node id, in build order.

## The three rules

- **The phone is the source of truth for enforcement and usage.** Raw usage never leaves the device. The server is a ledger of promises and outcomes, plus the people to notify.
- **Asr shares nothing with Bookween except the machine.** Separate repo, database, Redis, compose file, domain, and auth. Moving Asr to its own server is a folder copy and a DNS change.
- **Production V1, not MVP.** No throwaway code, no fake data, no planned rewrite. Scope is small on purpose; everything in scope is finished.

## Scope of V1

In: app limits, custom reset time, pacts (presets plus custom, 1 to 90 days) that lock
limits and app lists, "earn your time" activities (steps, focus session,
waiting period), witnesses with invite links, relationship labels, notification preferences
including roast mode, emoji reactions, protection-loss and uninstall detection, streaks and
history, onboarding, dark/light theme, offline handling, account deletion and
data export.

Out (deliberately, not "later if we get to it"): iOS, desktop, watch,
AI coach, public feed, leaderboards, parental controls.

## Stack

Backend: Next.js 16, TypeScript, PostgreSQL 17, Kysely, Better Auth (bearer
sessions), ioredis, Firebase Admin (FCM), Resend, Zod, Vitest. Android:
Kotlin, Jetpack Compose, DataStore, OkHttp with kotlinx.serialization,
Firebase Messaging and Crashlytics. No Hilt, Room, Retrofit or WorkManager, on purpose;
Google Play Billing is planned and not yet in the app. Reasons for each
choice are in `docs/ARCHITECTURE.md` and `docs/ANDROID.md`.

## Domains

- `joinasr.io`: landing page, privacy policy and terms (`/privacy`, `/terms`), and witness invite links (`joinasr.io/w/<code>`)
- `api.joinasr.io`: backend
- `noreply@joinasr.io`: transactional email

Each lives in exactly one config place (`/opt/asr/.env`, the Android build
config, `infra/nginx/asr-api`). Changing the domain later is a config change,
not a code change.
