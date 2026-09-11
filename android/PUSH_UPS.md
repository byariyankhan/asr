# Push-ups, and the other camera activities (earn time)

Seven push-ups, counted by the camera, earn the same +10 minutes as a
2 km walk or a 20-minute phone-free session. The selected app, the reward,
the per-app daily cap, the 12-hour deadline and the server activity
contract are the ones the other two activities use; the only new thing is
how the proof is gathered.

Server type name: `push_ups`. Rule in the pact snapshot:
`"push_ups": { "target": 7, "reward_min": 10, "daily_cap_min": 30 }`.

## How it is counted

`earn/PoseCamera.kt` opens the front camera through CameraX and hands
every frame to MediaPipe's Pose Landmarker, running on the phone from the
model bundled at `app/src/main/assets/pose_landmarker_lite.task`. The
model answers with 33 body points; the eight a push-up is judged by
(shoulders, elbows, wrists, hips) are kept and the frame is dropped.

`earn/PushUpCounter.kt` is the rule, in pure Kotlin so it can be tested
without a camera (`PushUpCounterTest`). Two views, picked frame by frame
from what the model can see:

**From the floor** (the phone flat, screen up, just ahead of the hands:
where the first person to try it put the phone, and the default the
screen now teaches):

- A face: both eyes seen by the model. Nobody has to look at the phone.
  The face is measured two ways, across the eyes and from the eyes to the
  mouth, and the larger is taken: a head bowed to the floor shrinks the
  second, a head turned aside shrinks the first, never both, so where the
  person looks changes the reading little.
- Distance is read from that size, against the smallest it has been while
  up. Down is 1.35× closer, up is back within 1.12×. The baseline is
  whatever "up" the person has, so the phone can lie anywhere and a set
  begun from the bottom counts from the next rep. A nod towards the phone
  (about 1.2×) is between the two and counts for nothing.
- A body in a plank seen from the side is judged as a side view first;
  otherwise a face is the floor view, even when the model guesses an
  upright arm and hip out of a foreshortened body.
- "Down" held for four seconds is a phone that was moved, not a push-up:
  where the face is now becomes the new up, uncounted.

**From the side** (the phone propped up across the room):

- A rep is the elbow angle going above 150° (straight), below 95° (bent),
  and above 150° again, in that order, after a straight start. The gap
  between the thresholds stops an arm hovering at 120° from flickering.
- The shoulder-to-hip line has to be within 45° of horizontal, so the hips
  have to be in the picture and the body has to be in a plank. Standing
  up and bending the arms earns nothing.
- The arm the model is less sure of is ignored: from a side view the far
  arm is guessed through the torso.

Both views: half push-ups earn nothing; two reps closer than 600 ms are
one rep; a phase has to hold for two frames in a row before it is
believed; switching views mid-rep drops that rep. The model's normalised
coordinates are stretched back by the frame's aspect ratio before any
distance or angle is measured; in a 4:3 frame a straight arm reads as
bent otherwise.

The counter lives with the screen and starts from zero every time it is
opened; the activity carries the count (`EarnViewModel.onPushUp`), so
stepping away and coming back resumes at the same number. The view model
awards the minutes on the seventh, in the same DataStore edit as a walk's
completion, and reports to the server afterwards. A set whose 12-hour
deadline has passed is stood down on the phone before its last rep, with
a message, rather than awarded here and refused by the server after.

## What the person sees

Built for somebody on the floor, half a metre from the screen, hands busy:

- The camera comes first, portrait like the sensor, directly under the
  back chevron. The count sits on the picture at 112sp (a deliberate
  departure from `docs/DESIGN.md`'s 38-44 display range, for a number read
  from a plank), with a row of seven dots above it and the next thing to
  do on a strip along the bottom. Everything for the standing reader
  (reward, placement, progress card, exits) is below; there is no title
  over them, because the strip, the reward card and the placement card
  between them already say what to do, what for, and where.
- The frame's colour says what the counter sees: grey for nobody, green
  while counting, a green wash at the bottom of every rep, amber for a
  body that is not in a plank, and a flash for every counted rep. The
  number pops on each count.
- Every counted rep ticks (the platform's own `ToneGenerator`, a generated
  tone, no asset) and pulses (view haptics, no permission); the last one
  gets a two-note rise from the reward screen, which opens on the target
  number at floor size for a moment before the receipt. A phone on silent
  or vibrate hears nothing and sees the colour instead.
- "Starting the camera" until the first frame is judged, so a quick first
  push-up is not lost to a model still loading. If the camera or the model
  cannot start, one plain line, the cause under it, and "Try again".
- A minute without a body and the camera is put down: "Paused, tap to
  continue". The lens being off is the promise on the permission screen;
  the count is untouched.
- The screen stays on while the camera is up (`keepScreenOn` on the
  preview view) and not a moment after.
- Two exits that say what they do: "Finish later" keeps the count and says
  so; "Give up this set" asks once when there is a count to lose. The
  dashboard row reads "Continue 3/7" over a paused set, and the block
  screen's Earn button reopens it rather than a chooser whose rows would
  do nothing.
- A refused camera permission is said on the chooser, and once Android
  stops showing the dialog the camera screen's button opens the app's
  page in Settings, as the notification flow does.

## The plank and the wall sit

Two more activities on the same camera, forty-five seconds each: the easy
end of the list, for the person who would give the challenge up if every
way to earn were a walk. Server types `plank` and `wall_sit`, rules
`{ "target": 45, "reward_min": 10, "daily_cap_min": 30 }` (seconds).

The screen is the push-up screen (`CameraActivityScreen`), told what it is
running by a `CameraSpec` (`earn/CameraActivities.kt`): the judge that
watches the poses, the noun for the units, and where the phone goes. A
judge is a `PoseJudge`: one frame in, how many units it earned out, and
the next thing to do as a title and a line. The push-up counter is one
(`PushUpJudge` wraps it); the two holds are `HoldJudge` with a position
rule from `HoldPositions`:

- **Plank**, head-on (`HoldPositions.plankFront`): the phone stood
  upright on the floor a step ahead of the hands, screen facing the
  person, which keeps it in portrait like the rest of the app and needs
  no room. That view sees the face, the shoulders, and the hips behind
  them, small; the legs are behind the torso and are not asked about.
  The rule: both eyes and both shoulders seen; the shoulders level within
  30°; the eyes no more than 0.3 of a shoulder width above the shoulder
  line (the head hangs in line with the spine in a plank; standing,
  kneeling up or sitting puts the eyes well above the shoulders); and the
  hips, when the model has them, between the shoulders across the picture
  (within 0.6 of a width of centre) and no more than 0.35 of a width
  below the shoulder line. A body on its feet or a chair has its hips a
  torso below the shoulders, which is the one thing this view sees
  plainly. And the arms hold the body up: an elbow the model can see
  hangs at least 0.45 of a shoulder width below its shoulder, which it
  does on the hands (halfway to the floor) and on the forearms (on it)
  and does not for a body lying face down with its head raised to look
  at the phone, whose shoulders are a hand above the floor; no elbow
  seen is no plank. Without hips the face, shoulders and arms decide,
  which lets a standing person with their head bowed, their hips out of
  the picture and their elbows held low through; the copy asks for the
  hips in. A kneeling plank passes, as it did from the side; that is the
  easy end.
- **Plank**, from the side (`HoldPositions.plankSide`), still accepted
  when that is how the phone is placed: shoulder, hip and ankle on one
  side of the body all seen, the shoulder-to-ankle line sloping down to
  the feet by 6° to 45° (lying flat is under, sitting up is over), and
  the hip within 14% of that line's length from it (sagging or piked hips
  are off it; sitting with the legs out has the hips far below it).
  Forearms or hands are not asked about. `plank` is either. The plank's
  body predicate (`hasBody`) accepts a face with both shoulders, so the
  head-on view coaches "Get into a plank"; the wall sit's (`wholeSide`)
  wants shoulder to ankle on one side first, so a face with the legs
  cropped is "Looking for you" with the shoulders-to-feet line, not
  "Slide down the wall".
- **Wall sit**, head-on (`HoldPositions.wallSitFront`): the phone stood
  upright on the floor or a low stool a couple of steps in front of the
  wall, no higher than the knees, with the person in the picture from
  shoulders to feet. Eight points: both shoulders, hips, knees and
  ankles. The shoulders level within 30° and between 0.35 and 1.5 torsos
  apart (under is side-on, over is a torso folded down towards the lens);
  the back upright (shoulders above the hips, at least 60° from
  horizontal); each knee close under its hip across the picture (within
  half a torso) and, seen from low down, level with the hip or above it
  (a knee nearer the lens than the hip at the same height is drawn
  higher; standing and a half squat put the knees below); and each shin
  dropping from knee to ankle, at least 0.45 of a torso long and within
  35° of vertical (a cross-legged sitter's ankles are up by the knees).
  A phone above the hips looks down on the thighs and draws the knees
  below the hips, which is why the copy says low. A chair passes, as it
  did from the side.
- **Wall sit**, from the side (`HoldPositions.wallSitSide`), still
  accepted: the shoulder-to-hip line at least 55° from horizontal, the
  hip-to-knee line within 30° of it, and the knee between 65° and 125°.
  A chair would pass too. The camera cannot see what is behind the legs,
  and the mechanism is honesty. `wallSit` is either.

Both are timed, not counted: the clock runs while the position holds and
stops while it does not, and whole seconds are handed to the activity as
they complete, so leaving and coming back resumes at the same number. A
break pauses the clock rather than resetting it. A change of phase has to
hold for 400 ms before it is believed, so a hip the model loses for a
frame is not a break; the settling time before a hold is believed is not
counted, and the time before a break is believed is, which is the smaller
error and the kinder one. Every fifth second ticks and pulses, and the
last one; every second would be a metronome. Tested without a camera in
`HoldJudgeTest`.

Both placement cards now put the phone upright, in portrait, in front
of the person: the plank's "about a step ahead of where your hands go",
the wall sit's "a couple of steps in front of the wall, shoulders to
feet in the picture". The side view is the note under the steps on each.

## The meditation

The fourth camera activity, and the one that is not exercise: seven
minutes sitting upright in front of the phone, facing it, keeping still,
in one go. Same screen, same `CameraSpec`, a judge of its own
(`MeditationJudge`) whose position is seated-and-still rather than a
shape, and the one difference in the timing: a break starts the count
over rather than pausing it (`CameraSpec.continuous`, `PoseJudge.brokeOff`,
`EarnViewModel.onStartedOver`). `android/MEDITATION.md` has the rules and
what they do and do not prove.

## What it does not do

- It cannot tell whose push-ups they are. Neither can the step counter
  tell whose steps. The mechanism is friction and honesty; the reward is
  ten minutes of Instagram.
- It does not store or send a single frame. No file is written, the
  bitmap is dropped after inference, and `PoseTracker` has no network
  call to make. The camera is bound to the composition's lifecycle: back,
  home, the reward screen replacing it, or the process dying all release
  it. There is no way to keep the lens open from anywhere else.
- It does not run in the background. Push-ups are done with the screen
  open, unlike a walk. Minimising the screen (back chevron) pauses
  counting; the activity and its count survive.
- No lock/unlock, pose, or camera observation reaches the server or the
  witnesses. What they see is the same `activity_completed` event a walk
  produces.

Left out on purpose, so the next agent does not build them:

- No photo, clip or "share my set" for witnesses. The permission screen
  promises no photo or video is ever saved; a single frame leaving the
  phone is a founder decision (AGENTS.md) and a Play data-safety change.
- No form grades or "bad rep" verdicts. The counter refuses half reps,
  bounces and standing, and nothing more; a counter that argues with the
  person is surveillance, not friction. Live feedback (colour, the wash at
  the bottom) is not a verdict.
- No streaks, personal bests or "faster than last time": that makes
  push-ups the product and pulls people back into the app that is meant
  to be put down.
- No discount when the counter struggles. The price is locked in the pact
  snapshot; the answer to a missed rep is better counting, not a cheaper
  set.
- No camera off-screen or in the background. The lifecycle binding in
  `PushUpCameraView` is the promise on the permission screen.
- No spoken counting through TextToSpeech yet: it depends on an engine and
  voice the app does not ship, takes a second to initialise, and makes the
  app talk in a shared flat. The tick and the colour do the job silently;
  revisit only if a floor test says they are not enough.
- No per-rep analytics. `extra_time_earned` is the one event.
- No Crashlytics report of a pose-model failure without the founder's
  say-so: it is a thing leaving the phone.

## Why MediaPipe Pose Landmarker

The references (PUSHapp, Workout Samurai, FitToll, PoseUp, GabpaWang) fall
into two families: apps that count the face coming close to a phone on
the floor, and apps that run a pose model on a side view and count elbow
angles. The first family needs no permission beyond the camera and
nothing to download, but it cannot tell a push-up from a nod, and cannot
see a plank at all. The second is what the two that describe their
method (PoseUp, GabpaWang) do, and both name MediaPipe.

MediaPipe Pose Landmarker, as checked on 2026-09-09:

| | |
|---|---|
| Library | `com.google.mediapipe:tasks-vision:1.0.0` (Google Maven, published 2026-07) |
| Model | `pose_landmarker_lite.task`, 5.8 MB, float16; full is 9.4 MB, heavy 30.7 MB |
| Native library | 11 MB arm64-v8a, 7.7 MB armeabi-v7a; Play delivers one ABI per phone |
| Licence | Apache 2.0, library and model (`LICENSES/MediaPipe-Apache-2.0.txt`) |
| Inference | On the phone. No account, key, server, or network use |
| Runs on | CPU delegate; the lite model keeps up with a push-up on a mid-range phone |
| minSdk | 24 (this app is 26) |
| Alternative | ML Kit Pose Detection: same model family, same landmarks, but a Google Play Services dependency and a larger download at first use. MediaPipe ships the model in the APK, which is what makes "nothing is downloaded" true |

The lite model is used because a push-up is a slow, large movement seen
from the side; the accuracy the heavier models add is in fingers and
faces, which this feature discards.

## The server side

`push_ups` is an activity type like the other two (`backend/src/lib/schemas.ts`,
`ACTIVITY_TYPES`), with a snapshot rule `{ target, reward_min, daily_cap_min }`
the phone sends when a pact starts. Start, complete, cancel, the per-app
daily cap, the `activity_completed` ledger event and the `time_earned`
witness notification are the existing code paths, untouched; the witness
copy names the app and the minutes, not the exercise.

Migration `0013_push_ups` widens the `activity.type` check and adds a
`push_ups` rule to every pact already on the ledger, priced like that
pact's walk. The snapshot is locked when a challenge starts so a phone
cannot renegotiate a price mid-way, which is exactly why a rule added
later has to be added by the server or it is not there at all. The
founder's decision, pre-launch with no real users: push-ups are on for
every challenge, running or future, with no compatibility carve-out.

## Device acceptance checks

These need a phone; JVM tests prove the rule, not the camera.

1. Choose push-ups with the permission not yet granted: the camera screen
   shows, "Allow" opens the system dialog, a grant starts the activity
   without a second tap, a denial returns to the chooser. On a phone with
   no camera the row is present and disabled.
2. Put the phone on the floor, screen up, just ahead of your hands. Do
   seven push-ups at a normal pace: the count reaches seven, the reward
   screen appears, the selected app has +10. Repeat with the phone propped
   on its side a few steps away, whole body in view from the side.
   For the plank: stand the phone upright a step ahead of your hands,
   facing you, and hold a plank: the frame goes green and the clock runs;
   kneel up, sit back or stand and it stops. The same with the phone on
   its side across the room. For the wall sit: stand the phone upright on
   the floor a couple of steps in front of the wall and slide down it:
   green, and the clock runs; stand up, or stop half way, and it stops.
   The same from the side.
3. Do push-ups standing against a wall, kneeling, or seated: nothing
   counts and the coaching line says why.
4. Bounce the arms quickly from the down position; do half push-ups; hover
   at half depth: nothing counts.
5. Press back (or "Finish later") mid-set: the dashboard row reads
   "Continue 3/7"; reopen from it, and from the block screen's Earn
   button: the count is where it was, the camera restarts, the remaining
   reps complete the set. "Give up this set" asks once, then discards.
6. Lock the phone, take a call, and switch apps mid-set: the camera is
   released each time (no green camera indicator on Android 12+ while Asr
   is not in front) and counting resumes on return.
7. Revoke the camera permission in Settings mid-activity, then return: the
   camera screen asks again; a grant resumes the set at the same count.
8. Confirm with the file manager and the privacy dashboard that nothing
   was written and no upload happened.
9. Verify walking and the phone-free session are unchanged, and the daily
   cap still holds across all three kinds for one app.
10. Every counted rep ticks and pulses; on silent and on vibrate it does
    neither and the frame flashes instead. Leave the phone on the floor
    with nobody in view: after a minute the frame says Paused and the
    camera indicator goes out; a tap resumes at the same count.
11. Refuse the camera permission twice: the chooser says so, and the
    camera screen's button now opens Settings.
