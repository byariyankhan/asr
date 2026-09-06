# API

Base URL `https://api.joinasr.io/v1`. JSON in, JSON out. All times are
ISO 8601 with offset. Ids are UUIDs.

## Authentication

Better Auth with the bearer plugin. The Android app calls the Better Auth
routes under `/api/auth/*` (sign up, sign in, verify email, reset password;
no confirmation email is sent at sign-up, see `POST /me/email/verify`;
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
| invites created | 20 per day (charged on the invite, not on the attempt) |
| public invite lookup | 60 per minute per IP |
| event ingestion | 120 per hour per device |
| storage probe (`/health/storage`, `/health?probe=storage`) | 12 per hour per IP |
| everything else | 300 per minute |

A 429 carries `Retry-After` and says the wait in its message, because a
refusal that does not say whether to come back in a minute or tomorrow
leaves nothing to do but press the button again.

## Health

### `GET /health`

`{ ok, db, redis, watchdog_stale, push_configured }`. No auth; `200` when db
and redis both answer, `503` otherwise. Polled by uptime monitors, so it is
cheap. `push_configured` says whether the three Firebase Admin values are in
the server's environment: `false` means every notification is written to the
inbox and none reaches a phone.

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

Rate limited per IP (12 per hour), because every hit is a paid write to the
bucket from a request that carries no session. A deploy asks a handful of
times; nobody else needs it more than that. `429` with `Retry-After` past
the limit, and no write is made.

## Pages

The public site, served by this application behind the same nginx site as
the API, so a change to it is a deploy and not a change to the server.

| Path | What it is |
|---|---|
| `/` | The landing page: what Asr is, how a pact works, and a Google Play badge that becomes a link once the listing exists |
| `/privacy` | The privacy policy. The address the Play listing points at |
| `/terms` | The terms of service |
| anything else | A styled 404, except the endpoints below and `/w/<code>` |

The privacy policy and the terms are the same words the app shows on its
own legal screens. They live twice -- `backend/src/lib/legal.ts` and
`android/.../legal/LegalTexts.kt` -- and `legal.test.ts` reads the Kotlin
file and fails when the two differ, so they are changed together or not at
all.

Two more links the product sends by email land on a web page when there is
no app to open them. Both are server-rendered and need no script.

### `GET /verify/<token>`

The email-confirmation link. Opening it is the confirmation: the page hands
the token to Better Auth and says whether the address is now confirmed, the
link has expired (they work for an hour), or it was already used.

### `GET /reset/<token>`

The password-reset link, for a browser. On a phone with the app installed
the same URL is an App Link and opens the app instead. The page is a form
for the new password, posted to a server action that calls
`/api/auth/reset-password`; every other session is signed out on success.

## App Links

### `GET /.well-known/assetlinks.json`

Rewritten to `/v1/assetlinks` in `next.config.ts`. No auth, world-readable by
design: a certificate fingerprint is a public hash of a public certificate.

This is what makes `android:autoVerify="true"` mean anything — Android
fetches it when the app is installed, and only if it names the app's signing
certificate does a tap on `joinasr.io/w/<code>` open Asr rather than a
browser or an "open with" chooser.

The fingerprints come from `ANDROID_CERT_SHA256` because they are not one
value: a debug build signed on a laptop, CI's debug key, and the key Play
signs releases with are three different certificates, and a phone verifies
against whichever signed the app it has. `404` when unset, rather than an
empty list — an empty list is a valid file meaning "no app may claim these
links", and Android caches it.

## Devices

### `POST /devices`

Register this install — which is what signing in on a phone means, and it
signs the account out everywhere else.

```json
{ "install_id": "…", "model": "Pixel 8", "os_version": "15", "app_version": "1.0.0", "fcm_token": "…" }
```

Returns the `device` row. Idempotent on `(user, install_id)`, so an app start
on the phone that is already registered changes nothing. From a phone that is
not, in one request:

1. Pushes `kind=signed_out` to every other device of this account, while
   their tokens still work — a phone that finds out by getting a 401 finds
   out whenever it next has a reason to ask.
2. Clears those tokens and **deletes every session but the caller's**. This
   is the part no app can ignore.
3. Moves the active pact to this device with a `moved` event, tells the
   witnesses, and starts `protection_pending_since` unless this phone has
   already reported its protection on.

One account runs on one phone because a phone can measure its own screen and
nothing else's; two phones enforcing the same thirty minutes is an hour. See
`ENFORCEMENT.md`.

### `POST /devices/{id}/heartbeat`

```json
{ "protection_enabled": true, "app_version": "1.0.0", "fcm_token": "…" }
```

`204`. Updates `last_heartbeat_at`, `protection_enabled`, and the token if it
changed. Also clears `removal_suspected_at`, and — when `protection_enabled`
is true — `protection_pending_since` on this device's active pact: a
heartbeat is the app saying it is here and working.

When `protection_enabled` is **false** and this device holds an active pact,
`protection_pending_since` is set if it was not already: the two-hour clock
a handover starts. The phone reports `false` when either usage access or
"display over other apps" is missing, because either one missing is a
challenge nothing enforces. Two hours of it and the witnesses are told
(`protection_off`); a later `true` clears the clock. The pact is not closed
by it.

Sent by the enforcement loop every 30 minutes, including while the screen is
off, and immediately in answer to the server's silent `kind=ping`.

### `DELETE /devices/{id}`

Logout from this device. `204`.

## Pacts

### `POST /pacts`

```json
{
  "device_id": "…",
  "duration_days": 7,
  "timezone": "Asia/Dhaka",
  "snapshot": { "apps": [...], "reset_time": "00:00", "activities": {...} }
}
```

`201` with the pact. `409 pact_active` if one exists. Writes a
`started` event and notifies witnesses with `notify_start`.

### `GET /pacts/current`

The active pact or `404`. Everything a phone that has never seen this
challenge needs to run it:

```json
{
  "id": "…", "device_id": "…", "device_model": "Galaxy A54",
  "duration_days": 30, "timezone": "Asia/Dhaka", "starts_at": "…",
  "status": "active", "protection_pending_since": null,
  "snapshot": { "apps": [...], "reset_time": "00:00", "activities": {...} },
  "today": { "day": "2026-09-05", "apps": [{ "package": "…", "minutes_used": 22 }] }
}
```

`today` is the day as the last phone reported it, in the pact's timezone.
Without it, changing phones handed back a fresh allowance: the new phone can
only measure its own screen, so it opens on zero. The phone adds these
minutes to what it can see, less its own share of them — a reinstall on the
same handset would otherwise count its own morning twice.

### `POST /pacts/{id}/apps`

One more app under a limit, on a challenge that is running.

```json
{ "package": "com.zhiliaoapp.musically", "label": "TikTok", "daily_limit_min": 20 }
```

`200` with the pact exactly as `GET /pacts/current` returns it, so the phone
can take the whole thing as its new copy. The app is appended to
`snapshot.apps` with `added_on`, today in the pact's timezone; nothing else
in the snapshot changes. This is the one edit a locked snapshot accepts, and
only in this direction: an app can be added, never removed, and no limit
moves. Adding tightens the promise, so witnesses are not notified; their
summary shows one more app from today. The phone counts the new app against
the whole of today at once, so an app already past its limit locks the
moment it is added -- that is the day's usage, not a breach, the same as
starting a challenge in the afternoon.

`409 pact_closed` once the challenge is over, `409 app_already_in_pact`,
`409 too_many_apps` past 100. Rate limited per user (`pact-apps`).

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
pact returns `409 pact_closed`.

A `completed` event is checked against the server's own calendar: it is
refused with `409 pact_not_elapsed` until today, in the pact's timezone, is
on or after the day following the pact's last day (the phone's own rule,
plus two hours of grace for a clock that runs ahead). The date is the
easiest thing on a phone to change, and without this a month moved forward
in Settings finished a challenge on day three. The phone treats this answer
as "keep enforcing", not as an error.

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
invite copy and notifications.

**One link, any number of people.** The invitation is a row that holds the
code and stays open; accepting inserts a *witness* row beside it rather than
consuming it, so the same link works for the second person and the tenth.
Send one "friend" link to a group and everybody in it may take it.

`parent`, `sibling` and `spouse` are also accepted and are what the app used
to send. They are not offered any more; rows carrying them still read back.

The exception is `mother`, `father`, `husband` and `wife`: one accepted
witness each, because nobody has two mothers. A singular link **closes** when
somebody takes it. A second invite for one already accepted is `409
relationship_taken`, and so is accepting one — the link is a code that
travels through group chats, so the gate that matters is at accept, not at
invite. An unanswered invite does not hold the slot, so a mother who has not
opened her link yet can be sent another.

One person is one witness on a challenge whichever link they open: a second
acceptance from the same person is `409 already_witness`.
`201`:

```json
{ "id": "…", "invite_code": "K7M2P9XQ4T", "relationship": "brother", "url": "https://joinasr.io/w/K7M2P9XQ4T" }
```

If `email` is given the delivery worker also sends the link by email.

### `GET /witnesses/invites/{code}`

Public (no auth, per-IP limit). Returns `{ "inviter_name", "inviter_image", "relationship" }`
so the accept screen can say "Ariyan wants you as a witness", plus `gender`
(for the page's own pronouns), `own` (the reader sent it) and `already` (the
reader has accepted this challenge — the app takes them to their circle
rather than asking twice). `404` when the
challenge has ended, when a singular link has been taken, or when the code is
not real — deliberately the same answer to all three, because it answers to
anybody holding a code. Never returns anything else.

### `POST /witnesses/invites/{code}/accept`

Authenticated as the witness. `200` with the newly created `witness` row.
`409 own_invite` for your own code, `409 already_witness` if you already
witness this person on this challenge, `409 relationship_taken` if it is a
singular relationship somebody else has taken, `409 invite_used` once a
singular link has closed, `409 challenge_over` if the challenge ended between
the link being sent and opened. The inviter gets a `witness_accepted`
notification.

### `POST /witnesses/invites/{code}/decline`

`204`. Writes nothing: the link belongs to everybody it was sent to, so one
person declining must not close it for the rest, and "declined" is not a
state the inviter is shown anywhere. Somebody who changes their mind can open
the same link again.

A witness reading this sees **only the challenge they were invited to**, and
only while it runs: `recent_events` carries that pact's events and no other,
and `completed`, `broken` and `longest_streak_days` come back as `0`. Those
are the owner's history, and being asked to watch one challenge is not being
handed it. `GET /pacts/{id}` is scoped the same way — an accepted witness of
one challenge cannot read another by id.

`streak_days` is days in a row, ending yesterday, on which every limit held.
Today never counts, and a day the phone never reported breaks it.

Witness notification copy is composed on the server, per relationship and
per event, and pushed as a finished title and body — so the voice of this
product changes with a deploy, not an app update. Pronouns come from the
profile's `gender`, which sign-up asks for and without which the profile is
not complete; anyone who chose `other` or `prefer_not_to_say`, or has none
recorded, is written about as they/them, verbs included.

### `POST /pacts/{id}/claim`

```json
{ "device_id": "…" }
```

This phone is the one enforcing the challenge from now on; sets the pact's
`device_id`, writes a `moved` event and tells the witnesses. `409
challenge_over` once it has finished, `404` for somebody else's. Claiming
from the phone that already owns it changes nothing and says nothing.

Rarely needed now: `POST /devices` moves ownership by itself, because
registering is signing in. It stays as the explicit form of the same
operation, and as what `movePactToDevice` is called from in tests.

Ownership is what the uninstall check reads: a dead FCM token closes the
challenge only when the dead device is the one that owns it.

### `GET /witnesses`

Two lists: people witnessing me (pending and accepted), people I witness
(accepted). `mutual` is true when the two of you witness each other.

```json
{
  "my_witnesses": [ { "id": "…", "status": "accepted", "relationship": "sibling", "user": { "id": "…", "name": "…" },
                      "invite_code": null, "invite_url": null, "reactions": ["shoe"], "notify_failure": true, "roast_mode": false,
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
  "id": "…", "name": "Ariyan Khan", "first_name": "Ariyan", "last_name": "Khan",
  "email": "…", "email_verified": true,
  "timezone": "Asia/Dhaka", "notify_email": true, "notify_push": true,
  "date_of_birth": "2000-02-29", "country": "BD", "gender": "male",
  "created_at": "…", "device_count": 1,
  "subscription": { "plan": "plus", "status": "active", "product_id": "asr_plus_monthly", "expires_at": "…" }
}
```

### `PATCH /me`

Any subset of `first_name` (1–40 characters), `last_name` (up to 40, or
`null`: one name is a whole name), `timezone`, `notify_email`,
`notify_push`, `date_of_birth` (YYYY-MM-DD, 13+), `country` (ISO 3166-1
alpha-2), `gender` (`male` | `female` | `other` | `prefer_not_to_say`). The
three profile fields accept `null` to clear them. `name` is not accepted:
the display name is composed by the server from the two parts ("First
Last", or just the first) whenever either part arrives, so it can never
disagree with them. `200` with the same shape as `GET /me`. The "About You"
screen after sign-up is one `PATCH`.

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

### `POST /me/email`

`{ "new_email": "…", "password": "…" }`. Changes the address the account
signs in and recovers with, in one step, behind the password (`403
invalid_password`). The new address must not belong to another account
(`409 email_taken`) and must differ from the current one (`400 same_email`).
Stored lowercased and **unconfirmed**; nothing is mailed to the new address
until the person asks for its link (below). If the old address had been
confirmed, it is sent one notice that the change happened, so a change made
by somebody else who knows the password is not silent. Sessions stay signed
in. Answers the profile, as `GET /me`. Limit: 5 per user per day.

Better Auth's own `change-email` is off, and `send-verification-email` is
not offered either (both answer `404` under `/api/auth`): the first would
cost two emails per change, and the second takes any address and mails it
behind only the loose per-IP limit, which is a way to spend the email budget
on strangers' inboxes.

### `POST /me/email/verify`

No body. Sends the confirmation link for the current address; opening it
(`GET /verify/<token>`, above) is the confirmation. `409 already_verified`
once it is. **Sign-up does not send this link**: an address is stored and
confirmed only when the person asks from Email & password, because each
link is a paid email for a step that is not required to use the app. Limit:
3 per user per day, and one every 5 minutes. Answers `{ "sent_to": "…" }`.

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

`{ "ok": true, "db": true, "redis": true, "watchdog_stale": false, "push_configured": true }`.
Used by the container healthcheck and uptime monitoring. No auth, no details.

### `POST /internal/watchdog`

Runs the watchdog once, out of band. Two conditions, both required: an
`x-internal-secret` header equal to `INTERNAL_SECRET` (constant-time
compare; a secret that is unset or shorter than 32 characters matches
nothing), and a request that did **not** come through nginx. nginx sets
`X-Real-IP` and `X-Forwarded-For` on everything it proxies and the API's
port is bound to `127.0.0.1`, so only a caller on the VPS itself qualifies;
everything else answers `404` before the secret is looked at, which is why a
leaked secret is still not enough from the internet. Returns the run report,
or `{ "skipped": … }` when another run holds the lock. For ops on the box
and as a cron fallback if the in-process loop is ever replaced:

```bash
curl -sS -X POST -H "x-internal-secret: $(sed -n 's/^INTERNAL_SECRET=//p' /opt/asr/.env)" http://127.0.0.1:3001/v1/internal/watchdog
```

### Watchdog

Not an endpoint. A loop started by the API process on boot
(`src/instrumentation.ts`) runs every 15 minutes. Single-flight twice over:
a Redis lock (`SET NX` with a 5-minute TTL, renewed every minute while the
run is going, released only by the run that took it — the release and the
renewal check the lock still carries that run's token), so a second replica
is safe and a run that dies with its process frees the lock within five
minutes; and a mutex in the process, so the loop and the manual trigger
cannot overlap even without Redis. Every step is idempotent and scoped by
state: running it twice, or after a crash mid-run, changes nothing the
second time.

0. Write down that it is running (`watchdog_state`). A previous run more
   than 30 minutes old is a gap the server was away for, recorded in
   `server_outage`; every rule below that measures silence subtracts the
   outages inside that silence, plus 45 minutes after each, so a server
   that was down cannot convict the phones that could not reach it
   (`ENFORCEMENT.md`, "When the server itself was away").
1. Mark `protection_lost` and break the pact for active pacts whose device
   has been silent for 24 h of the server being up (reason
   `heartbeat_timeout`). A pact started less than 24 h ago is never touched.
2. Mark `activity_failed` for pending activities past their deadline, and
   write the ledger event if the pact is still active.
3. Mark `completed` for active pacts past `ends_at` with no break.
4. Purge accounts whose 7-day deletion grace window has passed.
5. Expire rows past their retention (notifications 90 days, daily summaries
   400 days, dead devices 180 days).
6. Drain the notification queue via FCM, up to 200 rows per run, sending to
   every live device of the recipient. A token FCM reports as
   `UNREGISTERED` is one not-registered answer under the same two-answer
   rule the probe uses: a first answer starts a clock, and a second one two
   hours of uptime later with no heartbeat in between marks the device
   invalid. If that was the phone running an active pact, that is an
   uninstall: an `uninstalled` event is written (reason `fcm_unregistered`)
   and the pact breaks. Because that is discovered mid-drain, one extra
   delivery pass follows so the witnesses hear about it in the same run.
7. Write its finish time to Redis, which is what `/health` reads to report
   `watchdog_stale`.

A recipient with `notify_push` off, or with no live device, has the row
marked `failed` / `unregistered` rather than retried forever.
