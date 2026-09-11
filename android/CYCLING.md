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

Speed and distance. Metres between one fix and the next count when the
speed between them is a bicycle's, 8 to 45 km/h: slower is walking the
bike or a red light, faster is a car. A runner can hold a bicycle's
speed, so the step counter is read as well and a stretch at 100 steps a
minute or more counts for nothing. A slow drive in city traffic would
pass. The phone cannot tell a bicycle from a car at 20 km/h, and the
mechanism is honesty; the sheet says as much.

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

Fixes come from `LocationManager`'s GPS provider once a second, with the
platform's own clock (`elapsedRealtimeNanos`), and go to
`earn/RideCounter.kt`, pure Kotlin, tested in `RideCounterTest`:

- A fix worse than 30 m is not a place, and is ignored; the last good
  fix stays the reference.
- A gap longer than 15 s between accepted fixes (a tunnel, the process
  killed) is a stretch nobody measured, and credits nothing; the ride
  goes on from the next fix.
- The great-circle distance between fixes over the time between them is
  the speed; within 8 to 45 km/h the distance is credited, in whole
  metres, to the activity as it accumulates.
- Steps over the last 20 s at 100 a minute or more make the stretch a
  run, not a ride.

The counter remembers one fix, the last, and only until the next. No
route is written and nothing about where the phone was is sent; the
server learns that the ride was completed, as it does for a walk.

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
without GPS shows the tile dimmed with the reason.

## Device acceptance checks

1. Choose Cycle with no location permission: the screen asks; a grant
   starts the ride and the notification appears. Grant only approximate
   on Android 12+: the chooser says precise was refused, and the next
   tap's button opens Settings.
2. Ride 3 km with the phone in a pocket, screen locked: the count rises
   at a cycling pace, the ride completes, the notification and the +10.
3. Walk the bike for a block: nothing. Drive at motorway speed: nothing.
   Stop at a light: nothing, and the ride resumes when you move.
4. Run with the ride active: nothing credited while the cadence is a
   run's.
5. Revoke location in Settings mid-ride and return: the location screen
   asks again; a grant resumes the same ride at the same distance.
6. Leave a ride running with the phone on a table: after half an hour
   the notification goes and GPS is off; the activity is still there to
   resume from the dashboard.
