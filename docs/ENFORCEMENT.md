# Enforcement

Every rule here was argued for and several were got wrong first. What the
code does is in the code; this is why, and what it must never go back to.

## What ends a challenge

Three things, and going over a limit is not one of them.

| Ending | Reason code | Who decides |
|---|---|---|
| Completed | — | the last day passes |
| Given up | `user_gave_up` | the person, on a screen that names their witnesses |
| App removed | `fcm_unregistered` | the server, twice, two hours apart |
| Nothing enforcing it for a day | `heartbeat_timeout` | the server |

**Going over a limit blocks the app and says so, and the challenge carries
on.** It used to fail the challenge. That rule failed people for this app's
own missed polls, for minutes spent before the challenge existed, and for
Android reporting a session late — and even when it was right, it told
somebody their word was worthless for something the app was supposed to
prevent and had just failed to prevent. A `limit_hit` event goes to the
witnesses' progress screen instead: *reached a limit on Instagram*.

**Giving up is a front door and it is not free.** Without one the only way
out is uninstalling, which is the worst ending available to everybody in it:
the person loses their history and does not come back, and their witnesses
are told the harshest thing there is — that the app was removed — about
somebody who was merely tired. It is reported as a failed challenge, in the
voice of the relationship, in the same breath a broken limit would have been.
A quiet exit would leave the word "witness" meaning nothing.

## One account, one phone

A phone can measure its own screen and nothing else's. Two phones signed into
one account, each enforcing thirty minutes, is a person with an hour — and
`daily_summary` is an upsert on `(pact, day, app)`, not a sum, so the
witnesses would watch one number overwrite the other all day.

Merging them in real time would put a network request on the path of a limit
being applied, and this product refuses that: pull the plug and the limits
stop. So the newest phone is the phone.

`POST /devices` from an install the account does not know:

1. pushes `kind=signed_out` to the others while their tokens still work,
2. clears those tokens and deletes every session but the caller's,
3. moves the pact across — `moved` event, witnesses told,
4. starts `protection_pending_since` unless the new phone has already
   reported protection on.

The old phone acts on the push in seconds: pact cleared, service stopped,
signed out. A missed push is caught by the loop's own check, where a 401 is
the same answer by a slower road. Offline is not evicted and a 500 is not
evicted; a phone that stops enforcing because a server had a bad minute would
be a worse bug than the one this closes.

**The day travels with the challenge.** `GET /pacts/current` carries today's
minutes as the last phone reported them, and the new phone adds them to what
it can see — less its own share, because a reinstall on the same handset
reads back a total that includes this morning and is about to measure that
morning again. Without this, changing phones handed back a fresh allowance:
thirty minutes of Instagram became sixty for the cost of signing in, once per
phone, every day.

**Not a fresh dashboard, either.** Permissions are granted per install, so a
challenge that arrives on a new phone arrives with none of them. The gate in
front of the dashboard is undismissable while a challenge is running and
either grant is missing; the same gate catches revoking a permission
mid-challenge, which is the same hole from the other side. Both looked from
outside like a perfect day: no breaches, because nothing was watching. Two
hours of it and the witnesses are told in as many words — counted on the
server, so closing the app is not a way out of it.

**What this does not close.** A second phone without Asr installed cannot be
blocked by Asr, and no app can change that. Moving the challenge to a tablet
in a drawer leaves the real phone unenforced. The answer is not prevention,
it is that every move costs a message to the people watching.

## The loop

A foreground service, because the alternatives do not exist: Digital
Wellbeing's timers have no public API, `DevicePolicyManager` needs Device
Owner (a factory reset to set up), and AccessibilityService is what Play
review is strictest about. A background job would run when Android felt like
it, which for a limit is not running at all.

The rate follows what is at stake:

| Situation | Delay |
|---|---|
| Screen off | not running; waits on `ACTION_SCREEN_ON`, does the half-hourly errand |
| Nothing limited in front, nothing spent | 15s |
| A limited app open, time left | 5s |
| Open, last two minutes | 1s |
| Spent, not open | 1s |

The last row is a fix, not a tuning. The delay used to be read from the app
in front *right now*, so a spent limit on the home screen got the idle
fifteen seconds and opening it bought whatever was left of them — every time,
all day. A limit you can have fifteen seconds at a time is a toll.

**The rate never affects the count.** Minutes come from the timestamps in
Android's event stream, not from how often we look. A slow poll delays the
block screen; it loses nothing from the day. That is also why sleeping
through a screen-off costs nothing: the screen going off arrives as an
interruption that closes the open app at the moment it happened, and the
first poll after the screen returns reads it back with its real timestamp.

## What goes to the server, and when

Nothing here is on the path of a limit being applied. A person with no signal
is still blocked on time.

| Sent | When |
|---|---|
| Today's minutes | when a limited app is put down; every 5 min while one is open; a 30-min floor for earned minutes and the day rolling over |
| `limit_hit` | once per app per day |
| Heartbeat, outbox drain, eviction check | every 30 min, screen on or off |
| Endings | immediately, retried from the outbox |

An idle phone sends nothing at all — the figures have not changed, and the
first line returns. A timer would spend a phone's night sending a number that
did not move.

Five minutes while an app is open is not arbitrary: whatever has not been
sent when somebody signs in elsewhere is a piece of the day the next phone
hands back as free minutes. Five minutes is the width of that gap, and it
cannot be closed entirely without putting the network on the enforcement
path.

## Telling an uninstall from a phone that is switched off

The heartbeat cannot. It stops for an uninstall, for a flat battery, and for
an afternoon in an office with the phone off, and those are not the same
thing — which is why the rule built on it waits a full day, and why a day was
long enough to be a strategy.

Firebase can, and that is the only reason to ask it. A phone that is off, or
has data switched off, has the message **accepted and queued**. Only an
installation Google no longer knows about answers `not-registered`. Different
answers, not a slower version of the same one.

`probeForRemovals`, every 15 minutes, on devices running a challenge that
have been quiet for 45 minutes:

- accepted, or any other failure → forget any suspicion. Nothing here is
  evidence.
- `not-registered`, first time → record `removal_suspected_at`. Nothing else.
- `not-registered` again, two hours later, with no heartbeat in between →
  the app is gone.

Two answers because one is not enough to tell somebody's mother they deleted
the app: tokens rotate while an app is offline and phones get restored from
backups. An app that is really running clears the suspicion three times over
in that window — a heartbeat clears it, registering clears it, a probe
getting through clears it — and the app answers the probe with a heartbeat
immediately, so a phone coming back is cleared in seconds.

The probe is silent: data only, no notification block. Nobody gets an empty
line in their shade every half hour for an internal check.

Underneath it, unchanged: 24 hours of silence from the phone that **owns** the
pact closes it, with words that say what is actually known — *we have not
heard from this phone in a day*. Ownership is the whole test. It used to also
accept "any other device of theirs is alive", which had it exactly backwards:
the device most likely to satisfy that is the fresh install that replaced the
one being reported dead, so a reinstall vouched for the phone it replaced and
the pact stayed open with nothing enforcing it.

## Things that must not come back

- A limit that fails a challenge.
- A challenge that lives only on one install, so uninstalling is a quiet way
  out.
- Two phones enforcing the same day.
- A dashboard drawn over a challenge nothing can enforce.
- A poll rate read from the app in front rather than from what is at stake.
- Any accusation built on silence alone.
