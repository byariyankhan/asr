# Run and stairs (earn time)

Two more ways to earn the same +10 minutes, measured from the motion
sensors while the app is not open: a run (`run_steps`, 1,000 steps at a
running cadence) and a climb (`stairs`, 10 floors on foot). The founder's
pick from a longer list, for the person who runs anyway or lives up
several flights.

## Why the service, not the walk's trick

A walk is the step counter's total, read whenever the app next looks:
the difference from the baseline is the walk, and nothing is listened to
in between. A run is steps *at a pace* and a climb is a rise *while
stepping*, and both need the readings as they happen, with their times.
So `earn/MotionMonitor.kt` lives in the foreground service beside the
phone-free monitor and listens to the sensors only while a run or a
climb is the active activity, with a report latency of ten seconds: the
sensor hub batches the readings and the phone in a pocket sleeps between
batches. Every reading carries its own timestamp, and the counters go by
that, so a batch delivered late is judged as if on time. The two
sensors' batches arrive separately, so readings are held for twelve
seconds and judged in timestamp order: a rise is never judged before
the steps taken during it have been seen. Progress runs that far behind
the feet and never loses anything. The step counter's wake-up variant is
asked for first, so a batch that fills while the phone sleeps is
delivered rather than dropped.

Progress is written to the activity as it is earned, the reward is
applied on the phone first and reported to the server after, and a
notification says it is done, as a phone-free session's does. Nothing
about the readings leaves the phone: no route, no pressure log, only the
count and the completion the ledger already carries for a walk.

## The rules

Both in `earn/MotionCounters.kt`, pure Kotlin, tested without sensors in
`MotionCountersTest`.

**Run** (`RunCounter`): the total is read in windows of twenty seconds. A
window with 140 steps a minute or more is running and its steps are
credited; a slower one is walking and its steps are not. Twenty seconds
is long enough that a burst of three fast steps is not a run and short
enough that a run that stops for a light loses one window, not a minute.
No GPS and no distance; cadence is the whole of it, which is how a watch
tells the two apart as well. A total below the last one is a reboot, and
starts the window over.

**Stairs** (`StairsCounter`): the barometer's reading becomes an altitude
by the standard atmosphere, smoothed a little; a rise of 0.8 m or more
since the reference is banked as ascent *if stepping is going on as it is
seen* (three steps in the last five seconds and one in the last two and
a half), and moves the reference either way. One recent step would not
do: somebody who walks into a lift has a step a few seconds old for the
first floors of the ride. A rise without steps (a lift) and a descent
move the reference and bank nothing, so the lift up and the stairs down
earn nothing, and the stairs up after the lift earn only the stairs. A
run or a climb that reaches its 12-hour deadline is stood down by the
monitor on its next tick, sensors off, the same way the view model
stands down an overdue walk. A floor is 2.8 m, a little under a typical
storey, so ten real floors do not come out as nine through the
smoothing's lag or a building with low ceilings. Readings are absolute,
so the phone may be anywhere in the building when the activity starts,
and floors add up across the day until the deadline.

Offered only where the sensors are: a phone without a step counter gets
neither, one without a barometer gets no stairs. Both need
`ACTIVITY_RECOGNITION` on API 29+, the walk's permission, asked the same
way.

## What it does not do

- No GPS. A run is cadence; a phone shaken at 140 a minute is a run, as
  a phone shaken on a walk is a walk. The mechanism is honesty.
- No pace, speed, distance or route on screen or on the server: the
  count is the only figure.
- No activity recognition API, Google Fit or Health Connect: nothing to
  install, no account, no data leaving the phone. Cycling and swimming
  are the activities those would add, and are a founder decision.

## Device acceptance checks

1. Choose Run with the step permission not yet granted: the tracking
   screen asks for it as it does for a walk, and a grant starts the run.
2. Run for six minutes with the phone in a pocket, screen locked: the
   count reaches 1,000, the notification arrives, the app shows +10 on
   return. Walk the same six minutes: nothing is credited.
3. Climb ten flights with the phone on you: the floors count as you go
   (the display can lag a pocketed phone by ten seconds). Take the lift
   up ten floors: nothing. Take the stairs down: nothing.
4. Start a climb on the ground floor, take the lift to the fifth, then
   the stairs to the eighth: three floors.
5. On a phone with no barometer, Stairs is on the chooser, dimmed, and
   its sheet says why.
