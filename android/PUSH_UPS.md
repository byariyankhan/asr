# Push-ups (earn time)

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
without a camera (`PushUpCounterTest`):

- A rep is the elbow angle going above 150° (straight), below 95° (bent),
  and above 150° again, in that order, after a straight start. The gap
  between the thresholds stops an arm hovering at 120° from flickering.
- The shoulder-to-hip line has to be within 45° of horizontal, so the hips
  have to be in the picture and the body has to be in a plank. Standing
  up and bending the arms earns nothing.
- Half push-ups earn nothing; two reps closer than 600 ms are one rep; a
  phase has to hold for two frames in a row before it is believed.
- The arm the model is less sure of is ignored: from a side view the far
  arm is guessed through the torso.
- The model's normalised coordinates are stretched back by the frame's
  aspect ratio before any angle is measured. In a 4:3 frame a straight arm
  reads as bent otherwise.

The counter lives with the screen and starts from zero every time it is
opened; the activity carries the count (`EarnViewModel.onPushUp`), so
stepping away and coming back resumes at the same number. The view model
awards the minutes on the seventh, in the same DataStore edit as a walk's
completion, and reports to the server afterwards.

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
2. Prop the phone up on its side, front camera facing you, whole body in
   view from the side. Do seven push-ups at a normal pace: the count
   reaches seven, the reward screen appears, the selected app has +10.
3. Do push-ups standing against a wall, kneeling, or seated: nothing
   counts and the coaching line says why.
4. Bounce the arms quickly from the down position; do half push-ups; hover
   at half depth: nothing counts.
5. Press back mid-set, reopen from the Earn button: the count is where it
   was, the camera restarts, the remaining reps complete the set.
6. Lock the phone, take a call, and switch apps mid-set: the camera is
   released each time (no green camera indicator on Android 12+ while Asr
   is not in front) and counting resumes on return.
7. Revoke the camera permission in Settings mid-activity, then return: the
   camera screen asks again; a grant resumes the set at the same count.
8. Confirm with the file manager and the privacy dashboard that nothing
   was written and no upload happened.
9. Verify walking and the phone-free session are unchanged, and the daily
   cap still holds across all three kinds for one app.
