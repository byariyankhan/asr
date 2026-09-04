# Android

## What exists today

Read this first, because everything after it is a plan.

In the repository right now: a Gradle project (`minSdk 26`, `targetSdk 35`),
the design system taken from Figma (`docs/DESIGN.md`), and a working loop
from sign-up to enforcement — Welcome, Sign Up, Log In, About You, Challenge
Duration, Usage Access, Choose Apps, Set Daily Limits, Protection, the
app-blocking disclosure, the block screen, and the dashboard.

Sign-up and log-in reach the live API and store a session token; About You
sends the profile and uploads a photo. The setup screens read the real
permission state and open the real Settings screens. Choose Apps lists the
phone's real launchable apps, minus the ones that must never be blockable —
the launcher, Settings, the dialer, the SMS app, Asr itself — and Set Daily
Limits gives each chosen app a limit from a fixed ladder.

**The enforcement loop works.** The pact — the chosen apps, their limits and
when it started — is committed at the end of the limits screen and stored on
the phone. `EnforcementService` runs as a foreground service, reads how long
each app has been in front of the person today, and draws the block screen
(Figma 20) over any app that has run out of time. It comes back after a
reboot and after an app update. `ForegroundAccumulator` does the measuring
by walking Android's usage events rather than trusting
`totalTimeInForeground`, `Enforcement` makes the decision, and both are
covered by tests.

The dashboard (Figma 13) shows the day count, the live minutes per app and
whether protection is actually on — the "PROTECTED" pill reads the real
permission state rather than always saying yes. Setup ends when the pact is
committed, not on a flag, so the pact existing *is* what "set up" means.

**All 37 frames are built.** Four tabs — dashboard (13), progress (14),
witnesses (15, now the first tab of 16) and profile (28) — with the bar from
Figma 12 drawn once around them. Setup runs all six of its steps, ends on
the review screen (11) where the pact is committed, and passes through the
started screen (12).

**A challenge can end.** A breach is the block *failing to hold* — an app
used three minutes past a limit that should have stopped it — and it is the
only definition this architecture can measure. Reaching a limit is not a
breach; that is the block working. Completion ends a challenge the same way.
Either writes a `PactOutcome`, queues the event and clears the pact, in that
order, so a phone that dies halfway comes back with a finished challenge and
an event still to send. Figma 26 shows it.

**The server is told.** `Sync` registers the device, creates the pact,
reports events from an outbox and posts today's summary from the enforcement
loop's flush. None of it is on the path of a limit: a challenge starts,
enforces and breaks with no signal at all. Events carry a UUIDv7 made when
they happened, so a week in flight mode reports a week-old breach with the
time it occurred.

**Witnesses work in both directions.** Invites are shared through Android's
share sheet and opened by an App Link on `joinasr.io/w/` (Figma 18, which
works signed out — the person being asked to vouch usually has no account).
Figma 16 lists both halves, 17 shows what a witness may see, 19 is the
inbox, 25 reacts to an event.

**Earning time** (21–24) uses `TYPE_STEP_COUNTER` for walks, which the
sensor hub keeps whether or not this app is running, and the enforcement
loop itself for focus sessions — it is the only thing on the phone that can
see a controlled app come to the front. Earned minutes raise today's
allowance in `decide`, `pollDelayMillis` and `breach`, and never the pact.

Storage is DataStore, not Room. The pact is one small immutable value read
at service start and written once, and the outbox is a short list of events
that have not been sent. Room earns its place with a real usage history, and
that is the change that will bring it in.

What is still missing is not a screen. Push notifications (no FCM token is
ever registered, so witness alerts arrive only by email), the subscription
and Play Billing, and the block screen not appearing on some devices despite
the service running — that last one is a live bug, deferred deliberately.

None of Hilt, Room, WorkManager or Play Billing is a dependency yet. They are
named below because that is what this document is: the design of the app to
be built, written before it was built. Anything phrased as though it already
works describes an intention, not code, until it appears in the section
above.

`docs/FIGMA_SCREENS.md` has the node id of every screen and the order they
are being built in.

## Planned stack

Kotlin, Jetpack Compose, single-activity, MVVM with a repository layer, Hilt
for DI, Room for local persistence, WorkManager for background work, Retrofit
+ OkHttp for the API, Google Play Billing for subscriptions.

## Enforcement loop

The app must know which app is in the foreground and stop the user when a
limit is reached. Two mechanisms exist on Android; we use the one Google is
comfortable with and keep the other as a fallback that is off by default.

### Primary: foreground service + UsageStatsManager + overlay

1. A foreground service (persistent notification, "Asr is protecting your
   time") polls `UsageStatsManager.queryEvents` every 1 second while the screen
   is on. This needs the `PACKAGE_USAGE_STATS` special permission, which the
   user grants in system settings; the onboarding flow explains why and deep
   links there.
2. When a controlled app moves to the foreground, the service starts or
   resumes that app's local usage counter.
3. When the counter reaches the limit, the service shows a full-screen
   overlay (`SYSTEM_ALERT_WINDOW`, also granted in settings) on top of the
   blocked app. The overlay offers: go back, start an activity to earn
   minutes, or (during a pact) "I give up", which records a break.
4. Pressing home or back returns the user to the launcher; the overlay
   re-appears the moment the blocked app is foregrounded again.

This is how most shipping blockers (StayFree, YourHour, ActionDash) work and
it passes Play review with a clear declaration.

### Fallback: AccessibilityService

Faster foreground detection and harder to bypass, but Google rejects apps
whose accessibility usage is not for accessibility. We ship the service
disabled and undeclared in V1. If real users find the polling approach too
easy to evade, we revisit with a proper policy declaration. Do not enable it
casually.

**The Figma file disagrees, and this document wins.** Frame 10 is titled
"ANDROID ACCESSIBILITY" and its body explains an accessibility-based
mechanism. It was built as `BlockingDisclosureScreen` with the same shape and
the same disclosure, rewritten for the overlay: what it says is read, not
read, and shared is now true of what the app does. The reasoning is the
paragraph above -- an app removed from Play helps nobody, and the cost is
that the block screen appears about a second after the app opens rather than
instantly. The designer should be told, because the frame's title and body
are now wrong and somebody will build from them again.

### Known limits (documented in-app, not hidden)

- Uninstalling Asr or revoking the usage permission stops enforcement. That
  is what heartbeats and witnesses are for.
- Split screen and picture-in-picture count as foreground time for the
  visible app.
- Some OEMs (Xiaomi, Oppo, Vivo, Samsung with aggressive battery settings)
  kill foreground services. Onboarding detects the manufacturer and shows
  the specific "allow background activity" steps. The heartbeat carries a
  `protection_enabled` flag that is false if the service was killed and not
  restarted, so silence is noticed.

## Local data (Room)

| Table | Purpose |
|---|---|
| `controlled_app` | package, label, limit, reset time, per-day used seconds |
| `pact` | mirror of the server row plus `locked` flag |
| `activity` | local progress (steps counted, focus seconds elapsed) |
| `outbox` | events waiting to be sent, with the UUIDv7 id, retry count, next attempt |
| `local_event` | full history for the user's own screens (streaks, charts) |

Raw usage never leaves Room. Only `outbox` rows go to the server.

## Outbox and idempotency

Every server-bound event is written to `outbox` first with an id generated
on the device (UUIDv7). A WorkManager job with network constraint drains it
in order, retrying with exponential backoff. The server treats the id as an
idempotency key, so a retry after a timeout cannot double-report a break or
double-award minutes. The row is deleted only after a 2xx.

## Heartbeat

A periodic WorkManager job every 6 hours (the minimum reliable interval on
modern Android is 15 minutes; 6 hours keeps battery impact invisible) posts
the heartbeat. The app also sends one on every cold start and after every
outbox drain. The server's watchdog threshold is 24 hours, which tolerates a
missed period or a night in airplane mode.

## Daily reset

The user picks a reset time (default 04:00) so a limit does not refresh at
midnight while they are still scrolling. Counters reset when the device
clock crosses the reset time in the user's zone. If the phone was off at
that moment, the reset is applied on next wake based on the last reset
timestamp. Changing the reset time is locked during a pact.

## Activities

| Type | Sensor | Verification |
|---|---|---|
| `walk_steps` | `TYPE_STEP_COUNTER` (needs `ACTIVITY_RECOGNITION` on API 29+) | Delta since activity start; capped at 200 steps/min to reject shaking |
| `focus_session` | None: timer with screen-on and no controlled app foregrounded | Any controlled app foreground cancels the session |
| `waiting_period` | None: countdown | Nothing to verify; it is friction, not proof |

Reward minutes are applied locally the instant the activity completes and
reported to the server with the `activity_completed` event. The daily cap
is enforced locally and re-checked by the server.

## Witness invite (App Links)

`https://joinasr.io/w/<code>` is declared as an Android App Link with
`autoVerify`, backed by `/.well-known/assetlinks.json` on `joinasr.io`. If
the app is installed, the link opens the accept screen. If not, the fallback
web page shows the inviter's name and a Play Store button with
`referrer=w_<code>`; the app reads the install referrer on first launch and
opens the accept screen after sign-up.

## Permissions requested

| Permission | Why | When |
|---|---|---|
| `PACKAGE_USAGE_STATS` | Detect the foreground app and count time | Onboarding step 2 |
| `SYSTEM_ALERT_WINDOW` | Show the block screen over other apps | Onboarding step 3 |
| `POST_NOTIFICATIONS` | Witness and reminder notifications | Onboarding step 4 |
| `ACTIVITY_RECOGNITION` | Step activities | First time a step activity is started |
| `FOREGROUND_SERVICE_SPECIAL_USE` | The protection service | Manifest |
| `RECEIVE_BOOT_COMPLETED` | Restart protection after reboot | Manifest |

No location, contacts, camera, microphone, or SMS.

## Play policy notes

- The usage-access declaration form: "Screen-time management. Usage data is
  processed on-device to enforce user-set limits. Only daily totals for apps
  the user chose to limit are sent to our servers, to show to accountability
  partners the user invited."
- The Data safety form lists: email, name, app usage totals (user-chosen
  apps only), device identifiers (install id, push token). All encrypted in
  transit; user can request deletion.
- Subscriptions use Play Billing. No external payment links.
- The overlay must never obscure system dialogs or the permission screens.

## Build configuration

The API base URL is a `BuildConfig` field set in
`android/app/build.gradle.kts`, never a literal in Kotlin source. Both build
types currently point at `https://api.joinasr.io`. A `dev` flavor aimed at a
local server is worth adding when someone actually runs the backend locally,
and does not exist yet.

The app cannot be built in every environment this project is worked in — see
`docs/DEVELOPMENT.md`. `.github/workflows/android.yml` is the build of
record and uploads an installable debug APK on every run.
