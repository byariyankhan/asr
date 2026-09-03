# Architecture

## One sentence

The phone enforces and measures; the server remembers promises and tells
witnesses what happened.

## Responsibilities

```
┌──────────────────────────────┐        ┌──────────────────────────────┐
│ Android app (per device)     │        │ Backend (api.joinasr.com)    │
│                              │  HTTPS │                              │
│ • reads usage (UsageStats)   │───────▶│ • accounts + sessions        │
│ • blocks apps (overlay)      │        │ • pact ledger          │
│ • keeps limits, reset times  │        │ • outcome events             │
│ • runs activities            │        │ • witness graph              │
│ • computes streaks locally   │◀───────│ • heartbeat watchdog         │
│ • sends: heartbeats, events, │  FCM   │ • notifications (FCM/email)  │
│   daily summary              │        │ • Play Billing verification  │
└──────────────────────────────┘        └──────────────────────────────┘
                                                      │
                                       ┌──────────────┴──────────────┐
                                       │ PostgreSQL (asr)  Redis     │
                                       │ own containers, own volumes │
                                       └─────────────────────────────┘
```

### The phone owns

- Which apps are controlled, their limits, reset times (also mirrored to the
  server inside the pact snapshot, so a reinstall can restore them).
- Real-time foreground detection and blocking.
- Per-app usage counters and their daily reset.
- Activity progress (steps, focus timer, waiting period).
- Deciding that a rule was broken, and reporting it as an event.
- Streak and history screens (computed from the local event log; the server
  copy exists only so witnesses can see the same numbers).

### The server owns

- Identity: accounts, sessions, devices, FCM tokens.
- The pact ledger: what was promised, when, for how long, with which
  apps and limits, and how it ended.
- Outcome events reported by the device, deduplicated by idempotency key.
- Detecting silence: no heartbeat during an active pact means
  protection was lost or the app was removed.
- Witness relationships and their notification preferences.
- Sending notifications and remembering that they were sent.
- Subscription state, verified against Google Play.

## The data boundary

This is the decision that shapes everything else.

**Raw usage does not leave the device.** No per-session app usage rows, no
list of installed apps, no app icons, no event details JSON. The server stores
only what a witness is allowed to see and what is needed to notice that a
user went quiet:

| Leaves the device | Does not |
|---|---|
| Pact snapshot (package names + limits) | Installed app list |
| `broken` / `completed` events with a reason code | Per-session start/end times |
| Activity completed / failed | Step counts, locations |
| Daily summary: minutes per controlled app per day (optional) | Which app was opened when |
| Heartbeat (device alive, protection on, app version) | Anything about apps not under a pact |

Why:

- It is what the product promised ("usage counts, never content").
- Play Store review for `PACKAGE_USAGE_STATS` is far easier when the
  declaration can say "usage data is processed on-device".
- Server load and storage stay tiny. A user generates a handful of rows per
  day, not thousands.
- There is nothing sensitive to leak.

If a future feature needs more (for example a weekly per-app chart on the
witness dashboard), it is added as a new, explicit, opt-in aggregate, never
by uploading raw events.

## Server entities

Definitions and DDL are in `DATABASE.md`.

| Table | Purpose |
|---|---|
| `user` | Account, timezone, notification preferences (Better Auth also owns `session`, `account`, `verification`) |
| `device` | Android install: FCM token, app version, `last_heartbeat_at`, protection flag |
| `pact` | One promise: duration, start/end, status, JSON snapshot of controlled apps and limits at lock time |
| `pact_event` | Ledger rows: `completed`, `broken`, `protection_lost`, `uninstalled`, `restored`, ... with reason code, device time, and server receive time |
| `activity` | One earn-your-time attempt: type, target, reward minutes, deadline, outcome |
| `witness` | Directed relationship user → witness, status, per-witness notification settings, invite code |
| `reaction` | A witness's one emoji per event (tomato, shoe, clap...) shown back to the user |
| `notification` | Every message sent to anyone, with delivery result |
| `subscription` | Play Billing state |
| `daily_summary` | Optional per-day minutes per controlled app, for the witness view |

Nothing references Bookween.

## Key flows

### Locking a pact

1. User picks apps and limits on the phone, chooses a duration, confirms.
2. App `POST /v1/pacts` with the snapshot. Server refuses if another
   pact is active.
3. Server returns the pact id. From now until `ends_at` the app refuses
   to raise limits or remove controlled apps; the server refuses the same on
   its side if an API call tries.
4. Witnesses get "X started a 7-day pact" (if they opted in).

### Breaking a pact

1. The phone detects a breach: limit exceeded and the user chose to continue,
   a controlled app was removed, protection was turned off in settings, or
   the usage permission was revoked.
2. The phone writes the event to its local outbox with a fresh UUIDv7 as
   idempotency key, then `POST /v1/pacts/{id}/events`.
3. Server inserts the event (primary key is the device-generated id, so
   retries are harmless), marks the pact `broken`, and enqueues witness
   notifications.
4. If the phone was offline, the outbox drains on next connectivity. The
   event carries `occurred_at` from the device clock; the server also stores
   `received_at`. Witness messages use `received_at` for "when we found out"
   and show the device time as "reported time".

### Detecting silence (protection lost or uninstalled)

The device sends `POST /v1/devices/{id}/heartbeat` roughly every 6 hours via
WorkManager, plus on every app open and after every event. The watchdog
(`server/watchdog.ts`, started by `instrumentation.ts`, single-flight via a
Redis lock) runs every 15 minutes:

```
for each active pact:
  if latest heartbeat older than 24h:
     insert pact_event(type='protection_lost') if not already present
     notify witnesses
```

Uninstall is detected two ways, either of which is enough:

- FCM returns `UNREGISTERED` for the device token when we try to send
  anything. That is a definitive signal from Google that the app is gone.
- Heartbeats stop and the user has no other active device.

On reinstall and login the device registers again, a `restored` event is
written, and the pact continues if it has not ended. V1 rule: any
`protection_lost` during an active pact counts as a break.

### Activities

1. Activity rules (which types, reward minutes, daily cap) are part of the
   pact snapshot, so they cannot be changed mid-pact.
2. The phone runs the activity and reports `completed` with the earned
   minutes, or the server marks `failed` when the deadline passes with no
   completion event (same 15-minute job).
3. Earned minutes are applied on the phone immediately and recorded on the
   server for the witness view.

### Witness invite

1. User creates an invite: `POST /v1/witnesses/invites` returns a code.
2. App shares `https://joinasr.com/w/<code>` via the system share sheet.
3. The link is an Android App Link. If Asr is installed it opens the accept
   screen; otherwise it opens the Play Store listing with the code in the
   install referrer, and the app picks it up on first launch.
4. Witness accepts: `POST /v1/witnesses/invites/{code}/accept`. The witness
   needs an account but does not need to run any pact themselves.
5. Witnesses choose what they want to hear about: start, success, failure,
   daily digest, roast mode (harsher copy on failure). Preferences live on the
   `witness` row, not on the user.

## Stack and why

| Choice | Reason |
|---|---|
| **Next.js 16 API routes** | Same as Bookween. One framework to know. API-only here; no pages except the `/w/<code>` fallback and the landing page. |
| **Kysely + hand-written migrations** | Same as Bookween. Reuse the migration runner, rate limiter, UUID helper and test patterns. Two ORMs across two products is not worth it. |
| **PostgreSQL 17** | Same as Bookween, own container. |
| **Better Auth with the bearer plugin** | Same library as Bookween, but the app authenticates with a bearer session token stored in Android EncryptedSharedPreferences. No cookies, no separate JWT scheme. |
| **ioredis** | Rate limiting, the watchdog's lock and last-run marker, notification dedupe. Own container. |
| **Firebase Admin SDK** | FCM push to devices. The `UNREGISTERED` error doubles as uninstall detection. |
| **Resend** | Email verification, password reset, and witness notifications for witnesses who turn off push. |
| **Zod** | Every request body validated at the edge. |
| **UUIDv7** | Time-ordered primary keys, generated in the app (`uuidv7` package) so the device can pre-assign ids for idempotency. |
| **Google Play Billing** | Mandatory for in-app subscriptions on Android. The server verifies purchase tokens with the Play Developer API. Not Paddle. |

Android choices are in `ANDROID.md`.

## Time

- Every timestamp column is `timestamptz`.
- The user's IANA timezone is stored on `user` and copied into every
  pact so daily reset and "day N of 7" are computed the way the user
  experiences them, DST included.
- Device clocks are untrusted. The server stores `received_at` alongside any
  device-supplied `occurred_at` and uses server time for deadlines.

## Security

- Passwords hashed by Better Auth (scrypt). Session tokens are random, stored
  hashed, rotated on privilege changes.
- Rate limits per route via Redis (signup, login, invite creation, event
  ingestion). Same helper as Bookween.
- All device-originated writes are scoped to the authenticated user's own
  devices and pacts; ids in the path are checked against ownership
  before any read.
- Witness reads are scoped by the `witness` row's `status = accepted` and the
  user's `views_progress` flag.
- No secrets in the repo. `infra/docker-compose.yml` uses `${VAR}`
  interpolation from `/opt/asr/.env`.
- Account deletion is a hard delete after a 7-day grace window; data export is
  a JSON of the user's ledger.

## Moving to a separate server

Because nothing is shared with Bookween, the move is:

1. `pg_dump -Fc` the `asr` database from the current container.
2. On the new server: copy `/opt/asr` (compose, `.env`, nginx site), restore
   the dump, start the stack.
3. Point `api.joinasr.com` and `joinasr.com` at the new IP.
4. Redis content is disposable (rate-limit counters, watchdog marker); it
   does not need to be migrated.

No code changes, no Bookween changes.
