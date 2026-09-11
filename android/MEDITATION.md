# Meditation (earn time)

Ten minutes with a breathing guide on the screen and the phone lying
still, for the same +10 minutes. Server type `meditation`, rule
`{ "target_min": 10, "reward_min": 10, "daily_cap_min": 30 }`; on the
phone the target is kept in seconds (600), like the holds, so the clock
on the screen moves.

## What is proved, and what is not

Nothing on a phone can tell whether somebody meditated. What it can tell
is that the phone was put down and left alone for ten minutes with the
guide up, which is the shape of the thing and is what the sheet says:
"It cannot know whether you meditated; it knows the phone was put down
and left." The founder's choice over a camera eyes-closed check, which
would have been stronger proof at the cost of a second model in the APK
and the camera on for the length of it.

## How it works

`ui/screens/MeditationScreen.kt` shows a ring that swells for four
seconds and settles for six, with "Breathe in" / "Breathe out" inside it
and the time left under it, and gives a small haptic tap at each turn
(`RepFeedback.breath`) so the eyes can close. The screen stays on for the
length of it.

The proof is `earn/StillnessJudge.kt`, pure Kotlin, tested in
`StillnessJudgeTest`: the accelerometer, listened to only while the
screen is resumed. A sample is still when the acceleration vector has
moved less than 0.35 m/s² since the last sample, which a phone on a table
or a lap does not exceed and a phone in a hand does. The `HoldTimer`
behind the plank does the timing: a change is believed after a second
(a bump is not a pick-up), whole seconds go to the activity through
`EarnViewModel.onCounted` as they complete, and a gap in the samples
longer than a second (the screen locked, another app in front) is worth
nothing. Picking the phone up pauses the clock and the ring goes grey
with "Put the phone down"; putting it back resumes. Leaving with the
back chevron keeps the seconds; "Give up this session" asks once.

The view model awards the minutes on the six-hundredth second, exactly
as it does for the camera activities, and the receipt plays the finish
chime.

## What it does not do

- No sound, no music, no voice: a tap at each turn of the breath and
  nothing else. The phone is meant to be left alone.
- No check that the person is there. Somebody who puts the phone down
  and walks away earns the same ten minutes, as they would from a
  phone-free session. The mechanism is honesty.
- No new permission. The accelerometer needs none.
- No record of the session leaves the phone but its completion.

## Device acceptance checks

1. Choose Meditate: the guide appears; put the phone flat and the ring
   turns green and starts breathing, with a tap at each turn. The clock
   counts up.
2. Pick the phone up: "Put the phone down", the ring greys, the clock
   stops within a second. Put it down: it resumes where it was.
3. Lock the phone or switch apps mid-session, come back: the count is
   where it was, nothing was credited for the time away.
4. Let it run to ten minutes: the finish chime, the receipt, +10 on the
   app.
