# Cycling (earn time)

Three kilometres at a cycling speed, measured by GPS, for the same +10
minutes. Server type `cycling`, rule
`{ "target": 3000, "reward_min": 10, "daily_cap_min": 30 }` (metres).

The founder's choice of proof, from three: GPS on the phone (this), a
Health Connect session recorded by a watch or another app (more honest,
but only for people who already use one), and pedalling detection from
the accelerometer (unreliable, and a shaken phone passes). GPS is the
first activity in this app that needs location, and the manifest says
why and what is done with it.

## What is proved, and what is not

A window of thirty seconds is credited only when everything about it
looks like riding and nothing looks like a vehicle, a runner, or a
fake. Speed alone cannot tell a bicycle from a car at 20 km/h, so the
GPS, the step counter and the accelerometer are read together:

- **Pace.** The window's mean speed is 8 to 40 km/h (slower is walking
  the bike or a light; faster, sustained, is a motor) and no stretch
  between fixes exceeds 45 km/h (a downhill).
- **Jostle.** A bicycle shakes the phone, in a pocket, a bag, or on the
  bars; a phone resting on a car seat, or on a desk under a spoofed
  route, does not. The accelerometer's magnitude has to vary by at
  least 0.3 m/s² over the window.
- **Smoothness.** Legs cannot change speed by 3 m/s in two seconds; a
  car braking for a light or pulling away can. Two such changes in a
  window is a vehicle.
- **Feet.** Steps at 100 a minute or more over the window is a runner,
  whose speed can be a bicycle's.
- **Honest fixes.** A fix from a mock provider spoils its window. A fix
  worse than 20 m is not a place and is ignored. Where the GPS chip
  reports its own speed (Doppler, which no position edit can fake), the
  window's mean of it has to agree with the distance covered to within
  2.5 m/s, which is what catches a spoofed path with the phone at rest.
  A gap of more than 15 s between fixes adds no distance, and a jump
  faster than a bicycle spoils its window; riding on afterwards counts.
  Without the chip's speed, consecutive positional speeds stand in for
  the smoothness check at 4.5 m/s, wider because positions are noisier.
- **Its own window.** Every reading goes into the window its own
  timestamp falls in, and a window is judged only once a fix arrives
  six seconds past its end: the step counter and the accelerometer are
  delivered in batches a few seconds late, and a step taken just before
  a boundary belongs to the window before it, whenever it arrives.

Reviewed against the founder's list: a car or motorbike in traffic
(braking, pulling away, and a phone lying on the seat), GPS spoofing
(mock provider, teleport, a smooth fake path with a still phone), poor
accuracy, and a phone kept still in a vehicle. Each is refused by at
least one of the above; the tests in `RideCounterTest` play each one.
None of this is proof: a car crawling in stop-free traffic with the
phone in a pocket on a rough road could pass. The aim is that cheating
takes more effort than cycling, not that it is impossible, and the
mechanism is honesty; the sheet says as much. A refused window is
logged on the phone with its reason, and nothing about it goes to the
server.

## How it works

`earn/RideService.kt` is a foreground service of type `location`, its
own rather than a monitor inside the enforcement service, because
Android 14 wants a service that reads location to say so in its type and
the enforcement service is a special-use one that runs all day; GPS must
run only for a ride. It is started by the view model when a ride starts,
after the screen has checked the permission (a location service may not
start without it), and stops itself when the ride completes, is given
up, runs out of time, or has credited nothing for half an hour, which is
a phone forgotten with GPS on. Its notification says a ride is being
measured and how far it has got, for the whole of it.

The ride's screen, while it is open, keeps the display on
(`keepScreenOn` on its view, in `ActivityProgressScreen`, as the camera
screens do on theirs), because a phone on a handlebar cannot be tapped
awake every half minute and a dark screen is a ride that cannot be
followed. The same goes for the walk, the run and the climb; the
phone-down session is the one screen that does not, since its point is
a phone that goes dark and stays locked. Locking the phone to pocket it
still works, and the service measures on either way.

Fixes come from `LocationManager`'s GPS provider once a second, with the
platform's own clock (`elapsedRealtimeNanos`), the chip's own speed and
its mock flag; the step counter and the accelerometer come through the
same service at a five-second batch latency. All of it goes to
`earn/RideCounter.kt`, pure Kotlin, tested in `RideCounterTest`, which
judges each thirty-second window by the rules above and credits its
metres, whole, to the activity when it closes. Progress therefore runs
half a minute behind the wheels.

The counter remembers one fix, the last, and only until the next, and
the window's sums. No route is written and nothing about where the
phone was is sent; the server learns that the ride was completed, as it
does for a walk.

## Permissions

Two. `ACTIVITY_RECOGNITION` first (API 29+), on the walk's screen with
the ride's copy, because the step counter is what tells a run from a
ride and without it a runner at a bicycle's speed would be a cyclist;
the service refuses to start without it. Then `ACCESS_FINE_LOCATION`,
asked on its own screen the first time cycling is chosen and never at
launch. Precise, because a ride is metres at a
speed and Android 12's approximate half cannot give either: a grant of
the approximate half alone reads as a refusal, and once the dialog will
no longer show, the screen's button goes to the app's page in Settings.
No background location: the service is started from the screen, with
the app in front, and runs with its notification up. A revoke of either
permission mid-ride stops the service; the activity waits, and a fresh
grant restarts the service for the same ride. Opening the ride's screen
with both permissions in hand always (re)starts the service, which is
how a ride resumes after the half-hour idle stop or after Android has
killed the service; a service already running takes no notice. The
idle stop and the 12-hour deadline are checked by a watchdog every
minute as well as on every fix, so a switched-off GPS cannot leave the
service running for want of a fix, and the completion report to the
server is awaited before the service stops, so stopping cannot cancel
it.

Play data safety: location is collected, used for app functionality,
processed ephemerally on the device, not shared. Cycling is declared
optional (`android.hardware.location.gps` not required), and a phone
without GPS, a step counter or an accelerometer shows the tile dimmed
with the reason; the service refuses to start without all three, since
a ride without them cannot be judged.

## Device acceptance checks

1. Choose Cycle with no location permission: the screen asks; a grant
   starts the ride and the notification appears. Grant only approximate
   on Android 12+: the chooser says precise was refused, and the next
   tap's button opens Settings.
2. Ride 3 km with the phone in a pocket, screen locked: the count rises
   at a cycling pace, the ride completes, the notification and the +10.
   Ride with the phone on the handlebar and the ride screen open: the
   screen never times out, and the distance moves every half minute.
3. Walk the bike for a block: nothing. Drive at motorway speed: nothing.
   Stop at a light: nothing, and the ride resumes when you move.
4. Run with the ride active: nothing credited while the cadence is a
   run's.
5. Revoke location in Settings mid-ride and return: the location screen
   asks again; a grant resumes the same ride at the same distance.
6. Leave a ride running with the phone on a table: after half an hour
   the notification goes and GPS is off; the activity is still there to
   resume from the dashboard.
