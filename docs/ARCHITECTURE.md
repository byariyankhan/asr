# Architecture

## One sentence

The phone enforces and measures; the server remembers promises and tells
witnesses what happened.

## Responsibilities

```
┌──────────────────────────────┐        ┌──────────────────────────────┐
│ Android app (per device)     │        │ Backend (api.joinasr.io)    │
│                              │  HTTPS │                              │
│ • reads usage (UsageStats)   │───────▶│ • accounts + sessions        │
│ • blocks apps (overlay)      │        │ • pact ledger          │
│ • keeps limits, reset times  │        │ • outcome events             │
│ • runs activities            │        │ • witness graph              │
│ • shows what the server has  │◀───────│ • heartbeat watchdog         │
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
- Blocking an app whose limit is spent, and reporting that it was spent.
  Not deciding that the challenge failed: going over a limit is blocked and
  recorded, never punished. See `ENFORCEMENT.md`.
- Nothing else. History and streaks were meant to be computed here from a
  local event log; there is no local event log, because the day is rebuilt
  from Android's event stream on every poll and nothing needs keeping. Both
  screens read `GET /me/progress`, which is the same query the witness
  screens use -- so the person and the people watching cannot be shown two
  different numbers.

### The server owns

- Identity: accounts, sessions, devices, FCM tokens.
- The pact ledger: what was promised, when, for how long, with which
  apps and limits, and how it ended.
- Outcome events reported by the device, deduplicated by idempotency key.
- Which single phone a challenge is running on, and moving it when somebody
  signs in on another one.
- Detecting silence, and telling an uninstall apart from a phone that is
  merely switched off.
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

The same boundary holds for the one thing that leaves the device for
somewhere other than the server: the ten product events
`android/.../analytics/Analytics.kt` sends to Firebase Analytics (sign-up,
challenge started, invitation sent, challenge completed or broken, and so
on). None carries an app, a minute, a name, an address or a witness; a test
holds every event to that, and the advertising identifier is switched off.
See `ANDROID.md`, "Product analytics".

## Server entities

Definitions and DDL are in `DATABASE.md`.

| Table | Purpose |
|---|---|
| `user` | Account, timezone, notification preferences (Better Auth also owns `session`, `account`, `verification`) |
| `device` | Android install: FCM token, app version, `last_heartbeat_at`, protection flag, `removal_suspected_at` |
| `pact` | One promise: duration, start/end, status, the `device_id` running it, `protection_pending_since`, JSON snapshot of controlled apps and limits at lock time |
| `pact_event` | Ledger rows: `completed`, `broken`, `limit_hit`, `moved`, `protection_lost`, `uninstalled`, `activity_completed`, `activity_failed`, with reason code, device time, and server receive time |
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

### Reaching a limit, and ending a pact

Going over a limit does not end a challenge. It never did anything the
product promised by doing so: a person who scrolled past thirty minutes was
told their word was worthless, for something the app was supposed to prevent
and had just failed to. So a spent limit blocks the app and posts a
`limit_hit` event, which is what a witness sees on the progress screen, and
the challenge carries on.

Three things end one:

| Ending | How |
|---|---|
| **Completed** | The last day passes. The phone reports it; `completeElapsedPacts` catches the ones whose phone never did. |
| **Given up** | The person presses Give up. `POST /v1/pacts/{id}/give-up`, `broken` with reason `user_gave_up`. There has to be a front door, and it is not free: the witnesses hear about it in their own words. |
| **Abandoned** | The app was removed, or nothing has enforced the challenge for a day. Detected by the server; see below. |

1. The phone writes the event to its local outbox with an id derived from the
   pact, the app and the day, so reporting it twice is reporting it once.
2. `POST /v1/pacts/{id}/events`.
3. Server inserts the event (primary key is the device-generated id, so
   retries are harmless), marks the pact `broken` when the event is one of
   the endings, and enqueues witness notifications.
4. If the phone was offline, the outbox drains on next connectivity. The
   event carries `occurred_at` from the device clock; the server also stores
   `received_at`. Witness messages use `received_at` for "when we found out"
   and show the device time as "reported time".

### One account, one phone

A phone can measure its own screen and nothing else's, so two phones
enforcing the same thirty minutes is an hour. Registering a device is
therefore signing in on it, and signing in on it signs out everywhere else:
`takeOverOnPhone` pushes `kind=signed_out` to the old phone while its token
still works, clears that token, deletes every other session, and moves the
pact across with `movePactToDevice` — which records a `moved` event and tells
the witnesses, because parking a challenge on a handset nobody uses is the
one escape that would otherwise leave nothing behind.

`GET /v1/pacts/current` carries the day's minutes so the new phone does not
hand back a fresh allowance, and `protection_pending_since` starts running
because the permissions are per install. Two hours unprotected and the
witnesses are told. The whole rule and its reasoning are in
`ENFORCEMENT.md`.

### Detecting silence (protection lost or uninstalled)

The enforcement service sends `POST /v1/devices/{id}/heartbeat` every 30
minutes while it runs — including while the screen is off, which is the only
work it does then. The watchdog (`server/watchdog.ts`, started by
`instrumentation.ts`, single-flight via a Redis lock) runs every 15 minutes.

Two rules, for two different facts:

**The app is gone.** FCM answers `UNREGISTERED` only for an installation
Google no longer knows about; a phone that is off, or has data switched off,
has the message accepted and queued. That difference is the whole reason
`probeForRemovals` exists: it asks with a silent data-only push, and an
office afternoon with the phone switched off never starts anything. One
answer is not enough — tokens rotate and phones get restored from backups —
so the first `not-registered` only records `removal_suspected_at`, and it
takes a second one two hours later with no heartbeat in between. Then
`handleDeadDevice` closes the pact as `broken` with reason `fcm_unregistered`.

**Nothing has enforced this for a day.** `markProtectionLost`: the phone that
*owns* the pact has been silent for 24 hours. Ownership is the whole
question — it used to also accept "any other device of theirs is alive",
which let a fresh reinstall vouch for the phone it replaced. The copy for
this one says what it means: we have not heard from this phone in a day.

Signing in on a new phone restores the challenge from the server's snapshot
and moves ownership to it; the permission gate stands in front of the
dashboard until it can actually enforce anything.

### Activities

1. Activity rules (which types, reward minutes, daily cap) are part of the
   pact snapshot, so they cannot be changed mid-pact. The cap is per app per
   day across both kinds of activity, on the phone and on the server alike.
2. The phone runs the activity and reports `completed` with the earned
   minutes, or the server marks `failed` when the deadline passes with no
   completion event (same 15-minute job).
3. Earned minutes are applied on the phone immediately and recorded on the
   server for the witness view.

### Notification delivery

A notification is one row (`notification`, channel `push`) written inside
the transaction that records the event. It is pushed twice over, by design:
once by the request that queued it, the moment its response has gone out
(`after()` in Next, `deliverQueuedNow`), and again by the watchdog's sweep
for anything that first pass could not send -- a token that had just
rotated, a Firebase hiccup, a row queued while another delivery held the
lock. The two never push the same row twice: delivery runs under one Redis
lock (`asr:notifications:delivering`) and the second sender skips. So a
witness hears about a broken pact within seconds of the phone reporting it,
and the sweep is what makes that reliable rather than what makes it happen.

### Witness invite

1. User creates an invite: `POST /v1/witnesses/invites` returns a code.
2. App shares `https://joinasr.io/w/<code>` via the system share sheet.
3. The link is an Android App Link. If Asr is installed it opens the accept
   screen; otherwise it opens the Play Store listing with the code in the
   install referrer, and the app picks it up on first launch.
4. Witness accepts: `POST /v1/witnesses/invites/{code}/accept`. The witness
   needs an account but does not need to run any pact themselves.
5. Witnesses choose what they want to hear about: start, success, failure,
   progress. The tone of every message follows the relationship (a brother
   is roasted, a mother is reassured; `witness-copy.ts`) and the pronoun
   follows the profile. Preferences live on
   the `witness` row, not on the user, because the same person may want
   everything about their brother and only the ending about a colleague.

   `notify_digest` is stored and settable and nothing reads it: no digest is
   ever sent. It is a column waiting for a feature, not a feature.

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
  pact (`pact.timezone`) at lock time; completion is judged in that zone
  and it never moves. Every "today" -- which summary rows are today's,
  "day N of 7", the day an added app counts from, an activity's cap day --
  is computed in `pact.phone_timezone`, the zone the phone last reported
  with its registration, heartbeat or daily summary, so a person who has
  travelled is judged on the calendar they are living in, DST included.
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
3. Point `api.joinasr.io` and `joinasr.io` at the new IP.
4. Redis content is disposable (rate-limit counters, watchdog marker); it
   does not need to be migrated.

No code changes, no Bookween changes.
