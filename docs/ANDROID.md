# Android

## What exists today

Read this first, because everything after it is a plan.

In the repository right now: a Gradle project (`minSdk 26`, `targetSdk 36`),
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

**One thing a running challenge still takes: one more app.** The last row
under Today's limits is "Add an app". It opens a picker (the setup list
minus the apps already in the challenge, one choice, the same limit ladder
as setup) and asks the server to append the app to the pact; the pact that
comes back replaces the stored one whole, so the enforcement loop is
measuring the new app within the second. The new limit applies to the whole
of today, so an app already over it locks at once -- the day's usage, not a
breach, the same as starting a challenge in the afternoon. Witnesses are not
told. Nothing else about a running challenge changes: no app leaves and no
limit moves. The day an app came in is kept (`PactApp.addedOn`) so the
progress screen does not judge the days before it by a limit that did not
exist yet. Online only, unlike the rest of a challenge: the server's copy is
what the witnesses read, and the two should never disagree about which apps
are in it.

**Notifications reach the phone, not only the inbox.** The `alerts` channel
is created in `AsrApplication.onCreate`, before the first push can arrive: a
push that lands while the app is in the background is posted by Firebase
itself on the channel the manifest names, and if that channel did not exist
yet Firebase invented a default one with default importance, silently, for
good. The Notifications screen checks on every resume whether the app may
notify at all and whether that channel has been switched off, and when it
cannot shows a card with the switch: the system dialog where Android still
offers one, the app's own notification settings otherwise. Every password
field has a Show/Hide toggle, per field, kept across rotation.

**Signing in lands on the dashboard.** Setup is entered from a button on
it, not by having no pact. Half of this product's users never run a
challenge at all — somebody invited to witness a friend signs up to answer
the invitation — and sending them through six screens of permissions and app
pickers to reach the one screen they came for was asking the wrong person
for six answers. Every tab works with no pact: the dashboard offers to start
one, Progress says there is nothing to track yet, Witnesses shows both
directions, and notifications arrive regardless.

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
allowance in `decide`, `pollDelayMillis` and `overLimit`, and never the pact.

Storage is DataStore, not Room. The pact is one small immutable value read
at service start and written once, and the outbox is a short list of events
that have not been sent. Room earns its place with a real usage history, and
that is the change that will bring it in.

**Notifications are push, and only push.** The server queues one row per
witness who asked for that kind and the watchdog delivers it through FCM;
email carries sign-up verification and password resets and nothing else.
The Android half is now here: the token is registered with the device on
sign-in, refreshed by `onNewToken` and repaired by every heartbeat, and
dropped on sign-out so the next person to use the phone does not receive
somebody else's breaches.

It needs `android/app/google-services.json` from the Firebase console to
work. The Gradle plugin is applied only when that file exists, so CI keeps
building without it, and `Push.available()` reports the truth at runtime
rather than the app pretending.

What is still missing is not a screen: the subscription and Play Billing,
and the block screen not appearing on some devices despite the service
running — that last one is a live bug, deferred deliberately.

This document was written before the app was, and the sections below have
been brought back to what shipped. Where they still describe an intention
rather than code they say so in place -- the configurable reset hour and the
AccessibilityService fallback are the two. Play Billing is the one dependency
still named and not present.

The rules the enforcement loop follows, and why several of them are the
opposite of what was first built, are in `ENFORCEMENT.md`.

`docs/FIGMA_SCREENS.md` has the node id of every screen and the order they
are being built in.

## Planned stack

Kotlin, Jetpack Compose, single-activity, view models over small stores.
DataStore for local persistence, OkHttp with kotlinx.serialization for the
API, Firebase Messaging for push, Google Play Billing for subscriptions.

No Hilt, no Room, no Retrofit, no WorkManager. Each was considered and each
is listed here as an absence on purpose: three view models and a handful of
stores do not need a DI container, one immutable pact and a short outbox do
not need a database, a dozen calls do not need code generation, and the work
that matters cannot be deferred to a scheduler -- it is a foreground service
because a limit enforced whenever Android feels like it is not a limit.

## Enforcement loop

The app must know which app is in the foreground and stop the user when a
limit is reached. Two mechanisms exist on Android; we use the one Google is
comfortable with and keep the other as a fallback that is off by default.

### Primary: foreground service + UsageStatsManager + overlay

1. A foreground service (persistent notification, "Asr is protecting your
   time") polls `UsageStatsManager.queryEvents` at a rate that follows what
   is at stake. This needs the `PACKAGE_USAGE_STATS` special permission,
   which the user grants in system settings; the onboarding flow explains
   why and deep links there.

   | Situation | Delay |
   |---|---|
   | Screen off | the loop does not run at all; it waits on `ACTION_SCREEN_ON` and does the half-hourly errand |
   | Nothing limited in front, nothing spent | 15s (`IDLE_MILLIS`) |
   | A limited app is open with time left | 5s (`WATCHING_MILLIS`) |
   | Open and inside its last two minutes | 1s (`CLOSE_MILLIS`) |
   | **Spent, and not open** | 1s — it is one tap away |

   The last row is not an optimisation, it is the fix for a hole: the delay
   used to be read from the app in front *right now*, so a spent limit on the
   home screen got the idle fifteen seconds and opening it bought whatever
   was left of them. Every time, all day.

   The rate never affects the *count*. Minutes come from event timestamps,
   not from how often we look, so a slow poll delays the block screen and
   loses nothing from the day.
2. When a controlled app moves to the foreground, the service starts or
   resumes that app's local usage counter.
3. When the counter reaches the limit, the service shows a full-screen
   activity (`SYSTEM_ALERT_WINDOW`, also granted in settings, because a
   background activity launch needs it) on top of the blocked app. It offers
   going back or earning minutes. Giving up is not on it: a way out offered
   at the moment somebody is frustrated is not a decision, it is a button,
   and it lives on the dashboard instead.
4. Pressing home or back returns the user to the launcher; the overlay
   re-appears the moment the blocked app is foregrounded again.

This is how most shipping blockers (StayFree, YourHour, ActionDash) work and
it passes Play review with a clear declaration.

### Digital Wellbeing, and why it cannot be used

The question comes up on every phone that already has app timers built in,
so the answer is written down. Digital Wellbeing's timers, Focus mode and
Bedtime mode have **no public API**: the observers that make them work
(`UsageStatsManager.registerAppUsageLimitObserver` and friends) are
`@SystemApi`, need the `OBSERVE_APP_USAGE` permission, and are granted only
to apps signed with the platform key or preinstalled by the manufacturer. A
Play-distributed app cannot set a timer, read one, or be told when one is
hit. Family Link is the same machinery from the other side, and Samsung's
Digital Wellbeing is a fork with the same closed door.

What is public is what this app already uses: `UsageStatsManager` to read
the event stream, `SYSTEM_ALERT_WINDOW` to be allowed in front, and a
foreground service to be running when it matters. Digital Wellbeing's
visible behaviour -- the greyed icon and the "app paused" dialog -- is
achieved by pausing the app at the system level, which no third-party app
can do either. The closest an ordinary app can come is what the section
above describes, and it is what every blocker on the store does.

### When the activity is dropped: the overlay

A background activity launch that Android refuses does not throw; the call
returns and nothing appears. The overlay grant exempts this app on stock
Android, but MIUI keeps a separate switch for pop-ups from the background,
and a handful of other skins do the same under other names -- which was the
block screen "not appearing on some devices despite the service running".

So the loop no longer believes a launch. `BlockWatch` remembers what it did
and when; if the blocked app is still what is in front 2.5 seconds after the
activity was asked for, the launch was dropped, the failure is recorded for
the dashboard, and `BlockOverlay` draws the same screen as a
`TYPE_APPLICATION_OVERLAY` window instead. That window needs only the
overlay grant and no exemption from anybody: full-screen, touch-modal and
focusable, so it takes every touch and the back key. It comes down on the
next pass once the person has left the app. If neither route works, the
loop tries again every ten seconds rather than never.

The activity stays the first choice because it is the better block: it
takes the blocked app out of the foreground, so its minutes stop counting
and the back stack behaves. The overlay does not pause the app underneath,
so a person sitting on it is still "using" the app as far as Android's
counters go. That is the cost of a block that works everywhere, and it is
bounded by how long anybody stares at a wall.

### Keeping the loop alive

Android's foreground service is a promise Android keeps and several
manufacturers do not. Xiaomi, Oppo, Vivo, Huawei and Samsung all ship a
second battery layer that stops the service when the screen goes off, and
from then on nothing is blocked while the dashboard says LOCKED. Three
things answer that:

- **The keep-it-running screen** (`BackgroundActivityScreen`), reached from
  the Protection step of setup and from the dashboard's "protection is not
  running" warning. It shows whether Android's battery optimisation is off
  for Asr and opens the list to change it; and where this phone's
  manufacturer has a switch of its own, it names it, says what to set once
  there, and opens it (`OemSettings`, with the component names the
  community has kept for years). No new permission: the battery list and
  the app's own details page need none, and the per-app request dialog,
  which needs one Play grants by exception, is deliberately not used.
- **The server's probe.** The watchdog already pings any phone it has not
  heard from in 45 minutes -- which on one of these phones is exactly a
  phone whose loop is dead. A high-priority push is one of the few things
  Android lets start a foreground service from the background, so the ping
  handler starts the loop again. A killed service is back within the hour
  without anybody opening the app.
- **The truth in the heartbeat.** `protection_enabled` is usage access
  *and* the overlay grant, and it goes out whether or not usage access is
  on. A permission revoked mid-challenge reaches the server as a heartbeat
  saying so, and two hours later the witnesses are told; it used to be
  skipped entirely, which was silence, which takes a day.

What none of this closes: Force stop, and a manufacturer's setting the
person declines to change. Both leave a phone that stops heartbeating, and
the server's day of silence is still the backstop for those.

### Completion is asked, not announced

Finishing is the one ending that is a date arriving rather than a thing the
person did, and the date is the easiest thing on a phone to change. When the
loop's calendar says the challenge is over it asks the server first, which
checks the same calendar rule against its own clock and answers
`pact_not_elapsed` if they disagree. Then nothing ends: the day's count
starts again and the question is asked every fifteen minutes. When the
server cannot be reached, a phone that takes its time from the network is
trusted to finish offline, and one whose time was set by hand waits. The
loop also listens for the clock, the zone and the date changing under it and
recounts the day from what the system reports.

### Crash reports

Crashlytics, through the Firebase project push already uses; nothing else
in the app reports anything. Crashes report themselves. The failures that
matter more do not crash: the enforcement loop catches everything, and what
it catches now goes to Crashlytics as a non-fatal with the place it happened
(`diagnostics/Crash.kt`), which is the first time a phone in the field can
say why a limit was not enforced. Inert without `google-services.json`, like
push. The privacy policy names it and what a report carries.

### Product analytics

Firebase Analytics, through the same project, and only through
`analytics/Analytics.kt`: ten product events, so it is possible to see how
many people sign up, finish onboarding, start challenges, invite witnesses,
earn time, and finish or break what they started. The whole catalogue and
what each event may carry:

| Event | Carries | Sent when |
|---|---|---|
| `sign_up`, `login` | `method` (`email`) | the auth call succeeds |
| `onboarding_complete` | nothing | the profile goes from incomplete to complete (About You saved) |
| `pact_created` | `duration_days` | the challenge is committed on the phone (Review, Start) |
| `pact_started` | `duration_days` | the server accepts the new pact; the 409 adoption path does not count |
| `witness_invite_sent` | nothing | the server issues the invitation (before the share sheet) |
| `witness_invite_accepted` | nothing | the witness's phone accepts an invitation |
| `extra_time_earned` | `activity_type` | an activity completes and the minutes are awarded |
| `challenge_completed` | `duration_days` | the phone ends the challenge as completed (server-confirmed or trusted clock) |
| `challenge_broken` | `reason`, `duration_days` | the person gives up (`user_gave_up`) |

Never an app name, a minute, a name, an email address, a witness or
anything typed: `AnalyticsTest` holds every event to the four parameters
above and fails the build on a fifth. No user id is set, and the manifest
removes the advertising-id permission and switches ad-id collection and ad
personalisation off, so an event arrives with a random installation id,
the app version, the phone model, country and language. Endings the server
decides on its own (a day of silence, an uninstall) are not events here,
because the phone that would send them is off or gone; the ledger is the
count of record for those. Inert without `google-services.json`. The
privacy policy names it and what it receives.

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
- Split screen counts only the pane Android last resumed; the other pane
  is not counted while it sits there, and picture-in-picture is not counted
  at all. The accumulator has a single-open-app model, which is right for
  every other case and wrong for these two.
- Web versions of a blocked app are not blocked. instagram.com in Chrome is
  Chrome.
- Some manufacturers kill foreground services. The keep-it-running screen
  above is the answer, and the server's probe restarts a killed loop within
  the hour; a person who declines both is noticed by the heartbeat stopping.

## Local data (DataStore)

Room was the plan and is not what shipped. There is no usage history on the
phone to query -- the day is rebuilt from Android's own event stream on every
poll -- so what is left to store is a handful of small values, and a schema,
a compiler plugin and a migration path for those is a cost with nothing on
the other side of it.

| Store | Holds |
|---|---|
| `asr_pact` | the pact, as one JSON value. Written once when it is committed; read at service start. Half a pact is a state the loop must never see, so it is one atomic write |
| `asr_sync` | install id, the server's device id, push token, the server's pact id keyed to the pact's start time, and the outbox |
| `asr_carried` | minutes spent today on a phone this one is not, until the day rolls over |
| `asr_usage_floor` | the highest reading each limited app has had, per day, for the last eight -- so a day cannot be emptied by uninstalling the app that spent it |
| `asr_witness` | the witness list, so the circle screen is drawn before the first request answers |
| `asr_outcome` | how the last challenge ended, until the person has been shown |
| `asr_auth` | the session token |
| `asr_protection` | when the loop last ran, and when a block screen failed to launch |

Raw usage never leaves the phone. Only the outbox and the daily summary go to
the server, and the ten product events above go to Firebase Analytics with
no usage in them.

## Outbox and idempotency

Every server-bound event is written to the outbox first with an id generated
on the device -- UUIDv7 for one-off events, and for anything that can be
noticed repeatedly (a spent limit) an id derived from the pact, the app and
the day, so the loop noticing it forty times posts one event.

The enforcement loop drains it, oldest first, and stops at the first one that
does not go through: a phone coming back on a train must not report a breach
before the challenge it belongs to. What happens to an event the server
refuses is `OutboxPolicy`'s decision, and the line is whose fault it is: a
4xx is the server saying no to *this event*, and it will say no again -- a
409 on a closed pact, a 400 on a body this build sends wrongly -- so those
are dropped. Anything that says "not now" is kept: 401, 408, 425, 429, and
every 5xx. The first version dropped on a 502, and every deploy serves a
minute of those; a person who gave up in that minute saw "SENT" and nobody
was told.

Every event is stamped with the start time of the pact it happened in, and a
drain sends only one pact's events under that pact's server id (`Outbox`).
The queue can hold two challenges' worth -- a give-up that happened offline,
then a challenge started the same afternoon, then a limit reached in it --
and drained as one list it deadlocked: creating the new pact answered 409
while the old one was still open on the server, and the event that would
have closed it was sitting behind that refusal. So the loop drains the last
ending first if it is still owed, then the running challenge; and an active
pact on the server is adopted as this challenge's own only when nothing
queued belongs to another.

## Heartbeat

The enforcement loop posts it every 30 minutes, alongside draining the
outbox — including while the screen is off, when it is the only work the loop
does, and whether or not usage access is on. It carries `protection_enabled`
read from the system rather than assumed -- usage access *and* the overlay
grant, because either one missing is a challenge nothing enforces -- and the
server starts its two-hour clock on a `false`. A heartbeat that always says
true is worse than none, because it is what a witness would be trusting.

Not a WorkManager job. The service is already running -- it has to be -- so a
scheduler would add a second mechanism to keep alive for something the first
one can do in a line.

The server's 24-hour threshold tolerates a night in airplane mode. Telling an
uninstall apart from a flat battery is a separate mechanism and a faster one;
see `ENFORCEMENT.md`.

## Daily reset

Local midnight, in the phone's own zone. The pact snapshot carries
`reset_time` and the phone always sends `00:00`, because the server counts
days for the witness screen and a server counting from a different hour than
the phone would show two different numbers for the same day.

A configurable reset hour was in the design and is not built. It is one
value in the snapshot when it is wanted; what it cannot be is different on
the two sides.

Counters are not "reset" so much as recomputed: the accumulator is built for
a named day and reads events from that day's midnight, so a phone that was
off at midnight comes back with the right total rather than with a reset it
missed.

## Activities

| Type | Sensor | Verification |
|---|---|---|
| `walk_steps` | `TYPE_STEP_COUNTER` (needs `ACTIVITY_RECOGNITION` on API 29+) | Delta since activity start; capped at 200 steps/min to reject shaking |
| `focus_session` | None: timer with screen-on and no controlled app foregrounded | Any controlled app foreground cancels the session |
| `waiting_period` | None: countdown | Nothing to verify; it is friction, not proof |

Reward minutes are applied locally the instant the activity completes and
reported to the server with the `activity_completed` event. The daily cap
-- the most bonus time one app can have in a day, across both kinds of
activity -- is enforced locally and re-checked by the server on the same
rule. The phone also sends its IANA zone with its registration, every
heartbeat and every summary; the server computes the challenge's "today"
in it, so days stamped here and days judged there are on one calendar.

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

### API level

`compileSdk 36`, `targetSdk 36`, `minSdk 26`, on AGP 8.9.3 (the first line
that supports compileSdk 36 is 8.9.1). Play requires new apps to target
Android 16 (API 36) from 31 August 2026, and this app reaches Play after
that. The target level changes how Android 16 phones treat the app and
nothing about which phones can install it; that is `minSdk`, which stays at
Android 8.

What Android 16 changes for apps targeting it, and where it is handled:

- **Edge-to-edge cannot be opted out of.** Both activities call
  `enableEdgeToEdge()` and pad by the system bars; the block overlay does the
  same with `windowInsetsPadding`.
- **Predictive back is on by default.** The app's screens use Compose's
  `BackHandler` and `BlockActivity` the `onBackPressedDispatcher`, both of
  which go through the system dispatcher. The block overlay is a plain
  window, and a window that registers nothing with its
  `OnBackInvokedDispatcher` is simply not asked; `BlockOverlay.OverlayRoot`
  registers a callback on attach so back still does what the button does.
  The key-event path stays for Android 12 and older.
- **Orientation and resizability restrictions are ignored on screens of
  600dp and wider.** No screen here locks its orientation, so nothing
  changes.

Bumping the level next year: change the two numbers, read
`developer.android.com/about/versions/<n>/behavior-changes-<n>`, and add to
this list.
