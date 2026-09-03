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
{ "error": "pact_active", "message": "You already have an active pact." }
```

| Status | Meaning |
|---|---|
| 400 | Body failed validation (`error: "invalid_body"`, `issues: [...]`) |
| 401 | Missing or expired token |
| 403 | Not your resource, or witness access not granted |
| 404 | Not found |
| 409 | State conflict (`pact_active`, `locked_by_pact`, `invite_used`, `daily_cap_reached`) |
| 429 | Rate limited; `Retry-After` header set |

## Rate limits

Per user (or per IP before auth), enforced in Redis:

| Scope | Limit |
|---|---|
| sign up / sign in / password reset | 10 per 15 min per IP |
| invite creation | 20 per day |
| public invite lookup | 60 per minute per IP |
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

## Pacts

### `POST /pacts`

```json
{
  "device_id": "…",
  "duration_days": 7,
  "timezone": "Asia/Dhaka",
  "snapshot": { "apps": [...], "reset_time": "04:00", "activities": {...} }
}
```

`201` with the pact. `409 pact_active` if one exists. Writes a
`started` event and notifies witnesses with `notify_start`.

### `GET /pacts/current`

The active pact or `404`.

### `GET /pacts?cursor=&limit=`  **witness**

History, newest first, cursor pagination on `(created_at, id)`.

### `GET /pacts/{id}`  **witness**

Pact with its events and activities (activities: id, type, target, reward, status).

### `POST /pacts/{id}/events`

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
`activity_completed`, `restored`. A `broken` or `completed` event closes the
pact and triggers witness notifications. Anything else on a closed
pact returns `409`.

### `POST /pacts/{id}/give-up`

Shortcut for a deliberate early exit from the app's own UI. Body
`{ "id": "<uuidv7>" }`. Same as posting a `broken` event with reason
`user_gave_up`.

### `POST /pacts/{id}/summary`

Daily aggregate, sent once per day per app while active.

```json
{ "day": "2026-09-03", "apps": [ { "package": "…", "minutes_used": 27, "limit_min": 30, "earned_min": 10 } ] }
```

`204`. Upserts `daily_summary`. `409 day_out_of_range` outside the pact's
days (in its timezone), `409 app_not_in_pact` for a package not in the
snapshot.

## Activities (earn your time)

### `POST /pacts/{id}/activities`

```json
{ "id": "<uuidv7>", "type": "walk_steps", "started_at": "…", "deadline_at": "…" }
```

`201` with the activity. Target and reward minutes come from the pact
snapshot's activity rules, never from the request. `409
activity_not_allowed` if the pact has no rule for that type, `409
daily_cap_reached` if pending plus completed activities of that type already
reach the day's cap (in the pact's timezone), `409 deadline_too_far` if the
deadline is more than 24 h after the start. `200` with the existing row when
the id was seen before.

### `GET /pacts/{id}/activities`

`{ "items": [...] }`, newest first.

### `POST /activities/{id}/complete`

```json
{ "event_id": "<uuidv7>", "occurred_at": "…" }
```

`201` with `{ activity, event }`; the event is `activity_completed` carrying
the reward minutes. `200` with the same pair on a retry of the same
`event_id`. `409 activity_closed` if it was already completed or cancelled.

### `POST /activities/{id}/cancel`

`204`. No penalty; used when the user abandons a waiting period. Frees its
share of the daily cap.

Failed activities are set by the server watchdog when `deadline_at` passes
without completion; the app learns about it on next sync.

## Witnesses

### `POST /witnesses/invites`

```json
{ "relationship": "sibling", "email": "optional@example.com" }
```

`relationship` is one of `parent`, `sibling`, `spouse`, `partner`, `friend`,
`mentor`, `colleague`, `other`; it personalises invite copy and
notifications. `201`:

```json
{ "id": "…", "invite_code": "K7M2P9XQ4T", "relationship": "sibling", "url": "https://joinasr.com/w/K7M2P9XQ4T" }
```

If `email` is given the delivery worker also sends the link by email.

### `GET /witnesses/invites/{code}`

Public (no auth, per-IP limit). Returns `{ "inviter_name", "relationship" }`
so the accept screen can say "Ariyan wants you as a witness". `404` once the
invite is answered. Never returns anything else.

### `POST /witnesses/invites/{code}/accept`

Authenticated as the witness. `200` with the `witness` row. `409
invite_used` if already answered, `409 own_invite` for your own code, `409
already_witness` if you already witness this person. The inviter gets a
`witness_accepted` notification.

### `POST /witnesses/invites/{code}/decline`

`204`.

### `GET /witnesses`

Two lists: people witnessing me (pending and accepted), people I witness
(accepted). `mutual` is true when the two of you witness each other.

```json
{
  "my_witnesses": [ { "id": "…", "status": "accepted", "relationship": "sibling", "user": { "id": "…", "name": "…" },
                      "invite_code": null, "invite_url": null, "notify_failure": true, "roast_mode": false,
                      "views_progress": true, "mutual": true, "invited_at": "…", "responded_at": "…" } ],
  "i_witness":    [ { "id": "…", "relationship": "friend", "user": { "id": "…", "name": "…" },
                      "notify_failure": true, "roast_mode": true, "views_progress": true, "mutual": false } ]
}
```

Pending invites in `my_witnesses` carry `invite_code` and `invite_url` so the
user can re-share them.

### `PATCH /witnesses/{id}`

Each side edits only its own fields; asking for the other side's is `403`.

- The witness edits `notify_start`, `notify_success`, `notify_failure`,
  `notify_digest`, `roast_mode`.
- The user edits `views_progress`, `relationship`.

`200` with the row.

### `DELETE /witnesses/{id}`

Either side. Sets `status = removed`. `204`.

### `GET /witnesses/{id}/progress`  **witness**

What the witness dashboard shows about the user; same shape as
`GET /me/progress`. `403` unless `status = accepted` and
`views_progress = true`.

### `POST /witnesses/{id}/reactions`  **witness**

```json
{ "event_id": "…", "emoji": "tomato" }
```

One reaction per witness per event; sending again replaces it. `emoji` is
one of `laugh`, `haha`, `shoe`, `tomato`, `clap`. The event must belong to
the witnessed user. `200` with the reaction; the user gets a `reaction`
notification.

### `DELETE /witnesses/{id}/reactions`

`{ "event_id": "…" }`. `204`.

## Me

### `GET /me`

```json
{
  "id": "…", "name": "Ariyan", "email": "…", "email_verified": true,
  "timezone": "Asia/Dhaka", "notify_email": true, "notify_push": true,
  "date_of_birth": "2000-02-29", "country": "BD", "gender": "male",
  "created_at": "…", "device_count": 1
}
```

Subscription status joins this response once Play Billing lands.

### `PATCH /me`

Any subset of `name`, `timezone`, `notify_email`, `notify_push`,
`date_of_birth` (YYYY-MM-DD, 13+), `country` (ISO 3166-1 alpha-2), `gender`
(`male` | `female` | `other` | `prefer_not_to_say`). The three profile
fields accept `null` to clear them. `200` with the same shape as `GET /me`.
The "About You" screen after sign-up is one `PATCH`.

### `GET /me/progress`

```json
{
  "user": { "id": "…", "name": "…" },
  "current": {
    "pact_id": "…", "day": 3, "of": 7, "status": "active", "starts_at": "…", "ends_at": "…",
    "apps": [ { "label": "Instagram", "package": "com.instagram.android", "limit_min": 30 } ],
    "apps_within_limits_today": { "within": 2, "total": 3 }
  },
  "streak_days": 3,
  "longest_streak_days": 21,
  "completed": 4,
  "broken": 1,
  "recent_events": [ { "id": "…", "pact_id": "…", "type": "activity_completed", "minutes": 10, "received_at": "…" } ]
}
```

`current` is `null` with no active pact. `streak_days` is the day number of
the active pact; `longest_streak_days` the most days any pact survived.
`apps_within_limits_today` comes from today's daily summary if the phone
sent one, otherwise from today's `limit_hit` events.

### `GET /me/reactions?limit=`

Reactions witnesses left on my events, newest first:

```json
{ "items": [ { "id": "…", "emoji": "shoe", "reacted_at": "…", "witness_id": "…", "relationship": "sibling",
               "witness_user_id": "…", "witness_name": "Bob", "event_id": "…", "event_type": "broken", "event_at": "…", "pact_id": "…" } ] }
```

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

1. Mark `protection_lost` for active pacts whose device has been silent
   for 24 h.
2. Mark `activity_failed` for pending activities past their deadline.
3. Mark `completed` for active pacts past `ends_at` with no break.
4. Drain the notification queue (push via FCM, email via Resend), recording
   `UNREGISTERED` tokens on the device and writing an `uninstalled` event
   when that happens during an active pact.
5. Write its finish time to Redis for `/health`.
