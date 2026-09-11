# Meditation (earn time)

Seven minutes sitting still in front of the camera, in one go, for the
same +10 minutes. Server type `meditation`, rule
`{ "target_min": 7, "reward_min": 10, "daily_cap_min": 30 }`; on the
phone the target is kept in seconds (420), like the holds, so the clock
on the screen moves.

## What is proved, and what is not

Nothing on a phone can tell whether somebody meditated. What the camera
can tell is that somebody sat upright in front of it, facing it, and
kept still for seven minutes without getting up, which is the shape of
the thing and is what the sheet says: "It cannot know whether you
meditated; it knows you sat there."

The first version of this activity had the phone lying still on a table
under a breathing guide, proved by the accelerometer. The founder
replaced it: a phone left on a table proves nothing about the person,
and anybody could put it down and walk off. The camera version asks
more of the phone (the lens open for seven minutes, propped up) and of
the person (be there, be still), and is the harder thing to fake.

## How it works

The screen is the push-up screen (`CameraActivityScreen`) with a
`CameraSpec` for the meditation (`earn/CameraActivities.kt`), so the
placement card, the coaching strip, the exits and the receipt are the
same as a plank's. The judge is `earn/MeditationJudge.kt`, pure Kotlin,
tested without a camera in `MeditationJudgeTest`. Each frame it asks two
things of the pose:

- **Seated, facing the phone** (`SeatedPose`): the nose and both
  shoulders seen; both hips seen (the picture must reach that far down,
  or sitting up cannot be told from slumping; the coaching says "Move
  back a little"); the shoulders level within 25° and at least 0.35 of a
  torso apart (a body side-on or lying on its side is neither); the
  shoulders above the hips with the line between them at least 60° from
  horizontal (slumped or lying back is not).
- **Sitting, by the legs** (`SeatedPose.legsFolded`): both knees seen,
  and each thigh either goes sideways (cross-legged on the floor: the
  hip-to-knee line within 40° of horizontal) or comes towards the camera
  and looks short (on a chair: no longer than 0.55 of a torso). A body
  on its feet has a thigh that hangs straight down and as long as the
  torso, and fails both; an ankle the model sees below a straight leg
  1.45 torsos long is the last word. The founder's rule is that a body
  standing in front of the phone never earns a second, so knees out of
  the picture is not the position either ("Show your knees"), and
  kneeling up or sitting back on the heels, whose thighs look long and
  vertical from the front, is asked to sit another way ("Sit down").
  The one placement this refuses a real sitter is a phone high above a
  chair looking down, which stretches the thighs in the picture; the
  copy says no higher than the chest.
- **Still** (`BodyMotion`): over the last second the nose may drift by
  0.2 of a torso and the point between the shoulders by 0.12, measured
  in torso lengths (shoulders to hips) so the rule is the same near the
  phone and across the room and the model's own jitter, a few pixels, is
  a small fraction of it at any distance. A breath, a slow sway and a
  nod are under; a head turned to look at something, a shift on the
  cushion and getting up are over.
- **In the seat**: where the hips were when the clock started is the
  seat, and hips more than 0.6 of a torso from it have got up, however
  still the body now is; that is out of position until the sitting
  starts over, when the seat is wherever it sits next. Getting up from
  a chair or a cushion moves the hips by a torso and more.

Both together are the position, and a `HoldJudge` times it exactly as it
times a plank: a change of phase has to hold for 400 ms before it is
believed, whole seconds go to the activity through `EarnViewModel.onCounted`
as they complete, and a gap in the frames longer than 500 ms is worth
nothing. Every sixtieth second ticks and pulses, so a person with their
eyes closed knows a minute went by, and the last one does.

### In one go

The one way this differs from the holds: the ask is seven minutes in one
sitting, and a break starts the count over rather than pausing it
(`CameraSpec.continuous`). The judge believes a break after three
seconds out of position, or three seconds without a frame: a scratch, a
cough, a model that lost the hips for a moment cost nothing but the
seconds themselves; getting up, walking off, or the camera closing cost
the sitting. On that frame `PoseJudge.brokeOff` is true once, the screen
gives the small "found you" tap and tells the view model
(`onStartedOver`), which puts the activity's progress back to zero and
leaves it running, so the next sitting starts from 0:00 on the same
screen rather than from the chooser. Leaving the screen is a break too:
the count goes to zero when the screen goes (so the dashboard never
offers to "Continue 3:00/7:00" a sitting that cannot be continued) and
again when a fresh judge finds progress to its name, whether the camera
was retried or the absence pause was resumed. "Finish later" says so
("Leaving starts the sitting over") instead of promising the seconds are
saved.

The view model awards the minutes on the four-hundred-and-twentieth
second, as it does for every camera activity, and the receipt shows
"7:00" at floor size with the finish chime before the card.

## Anti-cheat, and its limits

- A phone on a table with nobody in front of it: nothing. The judge
  needs a face, shoulders, hips and knees.
- Sitting for four minutes, going to make tea, sitting for three more:
  nothing. Two sittings are not a meditation; the count started over.
- Standing in front of the phone, from the start or after getting up,
  however still: nothing. The legs are not a sitter's, and if the knees
  are out of the picture the clock does not run at all. Getting up
  mid-sitting starts the count over three seconds later, and sitting
  back down starts a fresh one.
- The phone knocked mid-sitting so the picture shifts by more than 0.6
  of a torso: the hips appear to have left the seat, and the sitting
  starts over. A nudge does less than that; a phone knocked over ends
  the sitting anyway.
- Lying on the sofa with the phone propped to look at you: the shoulders
  are one above the other, or the torso is horizontal; not the position.
  Lying on the back with the phone held above you would pass the torso
  rule, and is left to honesty.
- Sitting still and reading a book, or watching television: passes. The
  camera sees a person sitting still, which is what was asked; that it
  is also what a meditation looks like is the point of the mechanism,
  and the rest is honesty, as it is for a plank on the knees.
- A photograph or a paused video propped in front of the camera: passes.
  A liveness check was considered and left out: the model's own jitter on
  a still picture looks, frame to frame, like the breathing of a still
  person, and a rule strict enough to fail the picture would fail real
  meditators. A face-liveness model would be a second model in the APK
  and a different camera pipeline; not worth it for a +10 that a
  phone-free session gives for less.

## What it does not do

- No sound, no music, no voice: a tick a minute, and the finish chime.
- No new permission: the camera one, already asked for push-ups.
- No photo or video is saved; every frame is dropped after it is judged,
  and nothing from the camera leaves the phone but that the meditation
  was completed. Same as `PUSH_UPS.md`.
- No record of when the sittings broke off leaves the phone either.
- A meditation begun on the still-phone release and still active after
  the update comes back as a fresh camera sitting (`EarnStore.upgraded`):
  target 420, progress 0. On the server, a rule still at ten minutes
  (written by the previous release between the migrate step and the
  container swap) is read as seven when it becomes an activity's target
  (`currentRule` in `pacts.ts`).

## Device acceptance checks

1. Choose Meditate: the camera permission screen (if not yet granted)
   reads "7 MINUTES", then the camera screen with the placement card.
2. Stand the phone upright a couple of steps away, no higher than your
   chest, and sit cross-legged or on a chair facing it with your knees in
   the picture: the frame goes green, "Sit still", and the clock counts
   up as m:ss. A tick and a pulse at 1:00. Stand in front of it instead:
   "Sit down", and nothing counts, however still you are.
3. Scratch your nose, cough: the clock stops for a moment and goes on
   from where it was. Turn to look at something for a second: "Settle";
   look back and be still: the clock goes on.
4. Stand up and stay standing: "Sit down", after three seconds the
   clock is at 0:00, and it stays there while you stand. Stand up and
   sit straight back down: the clock goes on from where it was. "Give up this sitting" and "Finish later:
   leaving starts the sitting over" read as such.
5. Leave with the back chevron mid-sitting and reopen: 0:00.
6. Sit for seven minutes: "7:00 ✓ DONE", the finish chime, the receipt,
   +10 on the app.
7. Prop the phone so only the head and shoulders are in the picture:
   "Move back a little", and no seconds until the hips are in; with the
   hips in but the knees out, "Show your knees", and still none.
