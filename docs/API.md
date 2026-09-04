# API

Base URL `https://api.joinasr.io/v1`. JSON in, JSON out. All times are
ISO 8601 with offset. Ids are UUIDs.

## Authentication

Better Auth with the bearer plugin. The Android app calls the Better Auth
routes under `/api/auth/*` (sign up, sign in, verify email, reset password,
sign out) and receives a session token in the `set-auth-token` response
header. Every `/v1/*` call sends it:

```
Authorization: Bearer <session token>
```

The token is stored in DataStore in the app's private directory, with
`allowBackup="false"` on the application. This paragraph used to say
`EncryptedSharedPreferences`; that means `androidx.security:security-crypto`,
whose only release carrying the fixes is an alpha Google has stopped
developing, and an unmaintained alpha holding credentials is not obviously
safer than the OS sandbox. What the encryption would add is protection
against extraction from a rooted device, which is the wrong thing to spend
that dependency on for a revocable 30-day token. Revisit if the threat model
changes.

Sessions expire after 30 days of inactivity and are refreshed automatically
by Better Auth on use. A stored token is treated as a claim rather than a
fact: the app calls `GET /v1/me` on every start before showing a signed-in
screen, and a 401 clears it.

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
| 500 | Unhandled. `message` carries the error's class name and a short `trace` id, which is the same id in the server log. The class name — `R2Error`, `DatabaseError` — says which layer broke and nothing about the request. |

## Rate limits

Per user (or per IP before auth), enforced in Redis:

| Scope | Limit |
|---|---|
| sign up / sign in / password reset | 10 per 15 min per IP |
| invite creation | 20 per day |
| public invite lookup | 60 per minute per IP |
| event ingestion | 120 per hour per device |
| everything else | 300 per minute |

## Health

### `GET /health`

`{ ok, db, redis, watchdog_stale }`. No auth; `200` when db and redis both
answer, `503` otherwise. Polled by uptime monitors, so it is cheap.

### `GET /health/storage`

The same, plus `storage: { configured, writable, status?, error? }` — which
is answered by actually writing and deleting a small object in R2, because
read permission is not evidence that a PUT will be accepted, and a token
that can list a bucket and refuses every upload is exactly the failure that
hides. `status` is what R2 gave back: `403` is a token without write
permission, `404` is a bucket that is not there under that name. No bucket
name, account id, or R2 body is returned.

Its own path rather than a parameter on `/health`: a phone's address bar
ate `?probe=storage` the first time somebody tried it, and a diagnostic that
can be half-typed is a diagnostic that lies. `200` when the bucket takes a
write, `503` otherwise. `/health` stays cheap, because uptime monitors poll
it every minute and this costs two round trips to Cloudflare.

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
{ "relationship": "brother", "email": "optional@example.com" }
```

`relationship` is one of `mother`, `father`, `brother`, `sister`, `husband`,
`wife`, `partner`, `friend`, `mentor`, `colleague`, `other`; it personalises
invite copy and notifications. Each names one person, because a witness is
one person and the sender knows which.

`parent`, `sibling` and `spouse` are also accepted and are what the app used
to send. They are not offered any more; rows carrying them still read back.

`mother`, `father`, `husband` and `wife` are singular: one accepted witness
each. A second invite for one already accepted is `409 relationship_taken`,
and so is accepting one — the link is a code that travels through group
chats, so the gate that matters is at accept, not at invite. An unanswered
invite does not hold the slot, so a mother who has not opened her link yet
can be sent another.
`201`:

```json
{ "id": "…", "invite_code": "K7M2P9XQ4T", "relationship": "brother", "url": "https://joinasr.io/w/K7M2P9XQ4T" }
```

If `email` is given the delivery worker also sends the link by email.

### `GET /witnesses/invites/{code}`

Public (no auth, per-IP limit). Returns `{ "inviter_name", "inviter_image", "relationship" }`
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
  "created_at": "…", "device_count": 1,
  "subscription": { "plan": "plus", "status": "active", "product_id": "asr_plus_monthly", "expires_at": "…" }
}
```

### `PATCH /me`

Any subset of `name`, `timezone`, `notify_email`, `notify_push`,
`date_of_birth` (YYYY-MM-DD, 13+), `country` (ISO 3166-1 alpha-2), `gender`
(`male` | `female` | `other` | `prefer_not_to_say`). The three profile
fields accept `null` to clear them. `200` with the same shape as `GET /me`.
The "About You" screen after sign-up is one `PATCH`.

### `POST /me/avatar`

Raw JPEG bytes as the whole body, not multipart: there is one field, and
multipart would mean parsing a format with its own boundary handling to
carry a single blob. `content-type` is ignored; the bytes are checked.

The photo is optional. Figma 03 marks it required; it is not, because
forcing a photo before someone has used the app costs sign-ups and nothing
in the product needs a face to work.

Limits: 1MB, 1024px on the longest side, JPEG only. The client downscales to
512px and re-encodes before uploading, so anything larger did not come from
our client. `400 unsupported_image` for anything that is not a JPEG,
`413 image_too_large` past either limit, `503 storage_not_configured` when
the R2 credentials are absent.

Every APP1-APP15 and comment segment is removed before the object is
stored. That is where EXIF lives, and EXIF on a phone photo routinely
carries the GPS coordinates of where it was taken; a photo is shown to the
witnesses a person invited, and handing them the location of someone's
bedroom along with their face is not a trade anybody agreed to. Stripping
EXIF also removes the orientation tag, so the client must upload upright
bytes — the server does not decode and cannot rotate.

`200` with `{ "image": "/v1/media/avatars/<user-id>/<random>.jpg" }`.
Replacing a photo mints a new key and deletes the old object, so a URL
somebody kept stops working.

### `DELETE /me/avatar`

`200` with `{ "image": null }`. The object is deleted.

### `GET /media/{key}`

The stored photo, to anybody who has the URL. **A profile picture is
public.** The case that settles it is the witness invite: whoever opens
`joinasr.io/w/<code>` has no account yet and has to be shown who is asking,
and that preview already gives the inviter's name without a session.

Two checks remain, and they are what make a photo removable: the key must be
that owner's *current* photo, so replacing it kills the old URL rather than
leaving a face somebody took down still being served; and the owner must not
be deleted, so an account going away goes dark at once instead of at the next
purge. `404` for either, and for a key that is not shaped like one.

Rate limited per IP, because an unauthenticated image route is otherwise a
free image host paid for out of the VPS's bandwidth. Answers with
`Cache-Control: public, max-age=604800, immutable` (a key never changes
contents) and `X-Content-Type-Options: nosniff`.

The bucket stays private and the API streams the object. That is not a
privacy measure -- the photo is public -- it is so that no URL is ever
stored, only a key. Putting a CDN or a media domain in front of this later
changes one function and no data.

### `GET /me/progress`

```json
{
  "user": { "id": "…", "name": "…" },
  "current": {
    "pact_id": "…", "day": 3, "of": 7, "status": "active", "starts_at": "…", "ends_at": "…",
    "apps": [ { "label": "Instagram", "package": "com.instagram.android", "limit_min": 30,
                "minutes_used": 14, "earned_min": 0 } ],
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

`minutes_used` is today's figure from the daily summary, and is `null` when
the phone has not sent one yet. Null rather than zero on purpose: a witness
reading "0 / 20 min" would take it as somebody who has not opened the app,
which is a different fact from not having heard from that phone today.

### `GET /me/reactions?limit=`

Reactions witnesses left on my events, newest first:

```json
{ "items": [ { "id": "…", "emoji": "shoe", "reacted_at": "…", "witness_id": "…", "relationship": "sibling",
               "witness_user_id": "…", "witness_name": "Bob", "event_id": "…", "event_type": "broken", "event_at": "…", "pact_id": "…" } ] }
```

### `GET /me/notifications?cursor=&limit=`

Inbox of notifications sent to me (as a witness or about my own account),
newest first, cursor on `(created_at, id)`:

```json
{ "items": [ { "id": "…", "about_user_id": "…", "event_id": "…", "kind": "pact_broken",
               "title": "…", "body": "…", "deep_link": "/witness/…", "status": "sent",
               "sent_at": "…", "read_at": null, "created_at": "…" } ],
  "next_cursor": null, "unread_count": 3 }
```

### `POST /me/notifications/read`

`{ "ids": [...] }` (up to 200) or `{ "all": true }`. `204`.

### `GET /me/export`

The whole ledger as one JSON document, served directly with a
`content-disposition` attachment header: profile, devices, pacts, events,
activities, witness links (both directions), reactions, notifications and
daily summaries. Limited to 5 per day. Nothing is stored or emailed, so
there is no link to expire.

### `DELETE /me`

`{ "password": "…" }`. The password is verified through Better Auth. On
success: the account is marked deleted, every session is revoked, push
tokens are cleared, witness links in both directions end (the other side is
told), and the row is hard-deleted by the watchdog 7 days later. Signing in
again inside those 7 days cancels the deletion. `200` with
`{ "deletes_at": "…" }`. `403 invalid_password` otherwise.

## Subscription

Subscriptions are sold through Google Play Billing, which is mandatory for
digital goods inside an Android app. The server never trusts what the client
says a purchase is worth: it takes the token's identity and asks Play.

### `POST /subscription/verify`

```json
{ "product_id": "asr_plus_monthly", "purchase_token": "…" }
```

`product_id` is accepted but not read — Play is the authority on what the
token bought. The server calls the Play Developer API
(`purchases/subscriptionsv2`), stores the result, and returns the same shape
`GET /me` carries:

```json
{ "plan": "plus", "status": "active", "product_id": "asr_plus_monthly", "expires_at": "…" }
```

`plan` is `plus` while the purchase entitles the user and `free` otherwise.
Entitlement is status **and** expiry: Play's `CANCELED` means auto-renew is
off, not that access stopped, so a cancelled subscription still entitles
until `expires_at` passes. `paused`, `on_hold` and `pending` do not entitle.

When Play reports a `linkedPurchaseToken` (an upgrade or downgrade issues a
new token pointing at the old one), the old row is marked `expired` in the
same transaction.

Errors: `409 purchase_claimed` if another account already registered that
token, `404 unknown_purchase` if Play does not recognise it, `502
billing_upstream` if Play could not be reached, `503 billing_unavailable`
if this server has no Play credentials configured.

### `POST /webhooks/play`

Google Play Real-time Developer Notifications, delivered by a Pub/Sub push
subscription. Pub/Sub authenticates itself with the shared secret in the
push endpoint's query string (`?token=…`, compared against
`PLAY_PUBSUB_SECRET` in constant time), which is why the endpoint must only
ever be registered over HTTPS. An unauthenticated call gets `404`.

A notification says a token changed, never what it changed to, so the
handler simply re-verifies that token against Play. That is more
trustworthy than mapping the twenty notification types, and it self-heals a
missed event. A token we have never seen, a test notification, or an
unparseable payload all answer `200` — Pub/Sub retries any non-2xx, and
none of those would ever succeed. A verification failure answers `500`, so
it is retried.

## Internal

### `GET /health`

`{ "ok": true, "db": true, "redis": true, "watchdog_stale": false }`. Used by
the container healthcheck and uptime monitoring. No auth, no details.

### `POST /internal/watchdog`

Runs the watchdog once, out of band. Guarded by an `x-internal-secret`
header compared against `INTERNAL_SECRET` in constant time; without a match
the route answers `404`, so its existence is not advertised. Returns the run
report, or `{ "skipped": … }` when another run holds the lock. For ops and
as a cron fallback if the in-process loop is ever replaced.

### Watchdog

Not an endpoint. A loop started by the API process on boot
(`src/instrumentation.ts`) runs every 15 minutes. A Redis lock with a
10-minute TTL makes it single-flight, so a second replica is safe. Every
step is idempotent and scoped by state: running it twice, or after a crash
mid-run, changes nothing the second time.

1. Mark `protection_lost` and break the pact for active pacts whose device
   has been silent for 24 h (reason `heartbeat_timeout`). A pact started
   less than 24 h ago is never touched.
2. Mark `activity_failed` for pending activities past their deadline, and
   write the ledger event if the pact is still active.
3. Mark `completed` for active pacts past `ends_at` with no break.
4. Purge accounts whose 7-day deletion grace window has passed.
5. Expire rows past their retention (notifications 90 days, daily summaries
   400 days, dead devices 180 days).
6. Drain the notification queue via FCM, up to 200 rows per run, sending to
   every live device of the recipient. A token FCM reports as
   `UNREGISTERED` marks that device invalid; if it was the phone running an
   active pact and the user has no other live device, that is an uninstall:
   an `uninstalled` event is written (reason `fcm_unregistered`) and the
   pact breaks. Because that is discovered mid-drain, one extra delivery
   pass follows so the witnesses hear about it in the same run.
7. Write its finish time to Redis, which is what `/health` reads to report
   `watchdog_stale`.

A recipient with `notify_push` off, or with no live device, has the row
marked `failed` / `unregistered` rather than retried forever.
