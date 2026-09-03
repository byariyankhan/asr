# API

Base URL `https://api.joinasr.com/v1`. JSON in, JSON out. All times are
ISO 8601 with offset. Ids are UUIDs.

## Authentication

Better Auth with the bearer plugin. The Android app calls the Better Auth
routes under `/api/auth/*` (sign up, sign in, verify email, reset password,
sign out) and receives a session token in the `set-auth-token` response
header. Every `/v1/*` call sends it:

```
Authorization: Bearer <session token>
```

Tokens are stored in Android `EncryptedSharedPreferences`. Sessions expire
after 30 days of inactivity and are refreshed automatically by Better Auth
on use.

Routes marked **witness** may be called by an accepted witness of the user in
the path; everything else is owner-only.

## Envelope

Success: `200`/`201` with the resource, or `204` with no body.

Error:

```json
{ "error": "commitment_active", "message": "You already have an active commitment." }
```

| Status | Meaning |
|---|---|
| 400 | Body failed validation (`error: "invalid_body"`, `issues: [...]`) |
| 401 | Missing or expired token |
| 403 | Not your resource, or witness access not granted |
| 404 | Not found |
| 409 | State conflict (`commitment_active`, `locked_by_commitment`, `invite_used`, `daily_cap_reached`) |
| 429 | Rate limited; `Retry-After` header set |

## Rate limits

Per user (or per IP before auth), enforced in Redis:

| Scope | Limit |
|---|---|
| sign up / sign in / password reset | 10 per 15 min per IP |
| invite creation | 20 per day |
| event ingestion | 120 per hour per device |
| everything else | 300 per minute |

## Devices

### `POST /devices`

Register or update this install. Called on every app start after login.

```json
{ "install_id": "…", "model": "Pixel 8", "os_version": "15", "app_version": "1.0.0", "fcm_token": "…" }
```

Returns the `device` row. Idempotent on `(user, install_id)`.

### `POST /devices/{id}/heartbeat`

```json
{ "protection_enabled": true, "app_version": "1.0.0", "fcm_token": "…" }
```

`204`. Updates `last_heartbeat_at`, `protection_enabled`, and the token if it
changed. Called every ~6 h by WorkManager, on app open, and after every event
post.

### `DELETE /devices/{id}`

Logout from this device. `204`.

## Commitments

### `POST /commitments`

```json
{
  "device_id": "…",
  "duration_days": 7,
  "timezone": "Asia/Dhaka",
  "snapshot": { "apps": [...], "reset_time": "04:00", "challenges": {...} }
}
```

`201` with the commitment. `409 commitment_active` if one exists. Writes a
`started` event and notifies witnesses with `notify_start`.

### `GET /commitments/current`

The active commitment or `404`.

### `GET /commitments?cursor=&limit=`  **witness**

History, newest first, cursor pagination on `(created_at, id)`.

### `GET /commitments/{id}`  **witness**

Commitment with its events and challenges.

### `POST /commitments/{id}/events`

Device reports an outcome.

```json
{
  "id": "0192f1c2-…",
  "type": "broken",
  "reason": "limit_exceeded",
  "app_package": "com.instagram.android",
  "occurred_at": "2026-09-03T14:02:11+06:00"
}
```

`id` is a UUIDv7 generated on the device and is the idempotency key. `201`
with the event, or `200` with the existing event if the id was seen before.
Allowed device types: `broken`, `completed`, `limit_hit`,
`challenge_completed`, `restored`. A `broken` or `completed` event closes the
commitment and triggers witness notifications. Anything else on a closed
commitment returns `409`.

### `POST /commitments/{id}/give-up`

Shortcut for a deliberate early exit from the app's own UI. Body
`{ "id": "<uuidv7>" }`. Same as posting a `broken` event with reason
`user_gave_up`.

### `POST /commitments/{id}/summary`

Daily aggregate, sent once per day per app while active.

```json
{ "day": "2026-09-03", "apps": [ { "package": "…", "minutes_used": 27, "limit_min": 30, "earned_min": 10 } ] }
```

`204`. Upserts `daily_summary`.

## Challenges

### `POST /commitments/{id}/challenges`

```json
{ "id": "<uuidv7>", "type": "walk_steps", "target": 3000, "reward_min": 10, "started_at": "…", "deadline_at": "…" }
```

`201`. The server checks the type and reward against the commitment
snapshot's challenge rules and the daily cap; `409 daily_cap_reached` if
exhausted.

### `POST /challenges/{id}/complete`

```json
{ "event_id": "<uuidv7>", "occurred_at": "…" }
```

`200` with the challenge. Writes a `challenge_completed` event carrying the
reward minutes.

### `POST /challenges/{id}/cancel`

`204`. No penalty; used when the user abandons a waiting period.

Failed challenges are set by the server watchdog when `deadline_at` passes
without completion; the app learns about it on next sync.

## Witnesses

### `POST /witnesses/invites`

```json
{ "email": "optional@example.com" }
```

`201`:

```json
{ "id": "…", "invite_code": "K7M2P9XQ4T", "url": "https://joinasr.com/w/K7M2P9XQ4T" }
```

If `email` is given the server also sends the link by email.

### `GET /witnesses/invites/{code}`

Public (no auth). Returns the inviter's display name so the accept screen can
say "Ariyan wants you as a witness". Never returns anything else.

### `POST /witnesses/invites/{code}/accept`

Authenticated as the witness. `200` with the `witness` row. `409 invite_used`
if already accepted or declined.

### `POST /witnesses/invites/{code}/decline`

`204`.

### `GET /witnesses`

Two lists: people witnessing me, people I witness.

```json
{ "my_witnesses": [ { "id": "…", "user": { "id": "…", "name": "…" }, "status": "accepted", "notify_failure": true, "roast_mode": false } ],
  "i_witness":    [ { "id": "…", "user": { "id": "…", "name": "…" }, "status": "accepted", "views_progress": true } ] }
```

### `PATCH /witnesses/{id}`

Either side edits the fields that belong to them:

- The witness edits `notify_start`, `notify_success`, `notify_failure`,
  `notify_digest`, `roast_mode`.
- The user edits `views_progress`.

`200` with the row.

### `DELETE /witnesses/{id}`

Either side. Sets `status = removed`. `204`.

### `GET /witnesses/{id}/progress`  **witness**

What the witness dashboard shows about the user:

```json
{
  "user": { "id": "…", "name": "…" },
  "current": { "commitment_id": "…", "day": 3, "of": 7, "status": "active", "apps": [ { "label": "Instagram", "limit_min": 30 } ] },
  "streak_days": 12,
  "longest_streak_days": 21,
  "completed": 4,
  "broken": 1,
  "recent_events": [ { "type": "challenge_completed", "minutes": 10, "received_at": "…" } ]
}
```

`403` unless `status = accepted` and `views_progress = true`.

## Me

### `GET /me`

Profile, timezone, notification flags, subscription status, device count.

### `PATCH /me`

`{ "name", "timezone", "notify_email", "notify_push" }`.

### `GET /me/progress`

Same shape as the witness progress view, for the user's own screens
(the app computes this locally too; this is the reconciliation source).

### `GET /me/notifications?cursor=&limit=`

Inbox of notifications sent to me (as a witness or about my own account).

### `POST /me/notifications/read`

`{ "ids": [...] }` or `{ "all": true }`. `204`.

### `POST /me/export`

`202`. Generates a JSON export of the user's ledger and emails a download
link valid for 24 hours.

### `DELETE /me`

`{ "password": "…" }`. Schedules hard deletion in 7 days, revokes all
sessions, notifies witnesses that the relationship ended. `204`. Logging in
again within 7 days cancels the deletion.

## Subscription

### `POST /subscription/verify`

```json
{ "product_id": "asr_plus_monthly", "purchase_token": "…" }
```

Server verifies with the Google Play Developer API, upserts `subscription`,
returns `{ "status": "active", "expires_at": "…" }`. Play Real-time Developer
Notifications hit `POST /webhooks/play` (Pub/Sub push, verified by the
Pub/Sub JWT) to keep the row current without the app polling.

## Internal

### `GET /health`

`{ "ok": true, "db": true, "redis": true, "watchdog_stale": false }`. Used by
the container healthcheck and uptime monitoring. No auth, no details.

### Watchdog

Not an endpoint. A loop started by the API process on boot runs every
15 minutes (single-flight via a Redis lock, so a second replica is safe):

1. Mark `protection_lost` for active commitments whose device has been silent
   for 24 h.
2. Mark `challenge_failed` for pending challenges past their deadline.
3. Mark `completed` for active commitments past `ends_at` with no break.
4. Drain the notification queue (push via FCM, email via Resend), recording
   `UNREGISTERED` tokens on the device and writing an `uninstalled` event
   when that happens during an active commitment.
5. Write its finish time to Redis for `/health`.
