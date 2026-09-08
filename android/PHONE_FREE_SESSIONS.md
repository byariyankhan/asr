# Phone-free Earn Time

The focus activity requires 20 uninterrupted minutes with Android's keyguard
showing. Opening any app after dismissing the keyguard resets it to 20:00;
the next lock automatically starts a fresh interval. Time spent unlocked
before that lock earns nothing. The selected app, +10-minute reward,
per-app daily cap, and existing server activity contract are unchanged.

## Android signals and lifecycle

- `ACTION_USER_PRESENT` is the native keyguard-dismissal broadcast. It is
  registered dynamically in the existing foreground service, with a
  non-exported receiver. `ACTION_USER_UNLOCKED` is deliberately not used:
  that broadcast concerns credential-encrypted storage after boot.
- `KeyguardManager.isKeyguardLocked` supplies the initial state and a
  fallback query. Screen on/off broadcasts only request another query;
  display state is never an input to the session rule.
- Keyguard can remain logically showing while a call or alarm covers it.
  Answering a normal call over the keyguard therefore keeps the interval.
  Deliberately dismissing keyguard during a call still resets it. There is
  no blanket dialer exemption and no call, contact, or phone-state access.
  A manufacturer that actually dismisses keyguard to answer a call will
  report an unlock; this cannot safely be distinguished without more access.
- `elapsedRealtime` includes deep sleep and cannot be advanced by changing
  the date, time, or timezone. A listener-based exact alarm (no special
  permission) helps outside Doze; `setAndAllowWhileIdle` supplies a wakeup
  fallback in Doze. Android can delay the notification while idle. Unlocking
  at or after a verified interval's deadline preserves the reward.
- The monitor belongs to the service, not Compose. Closing the activity or
  dismissing recents does not stop monitoring if the service stays alive.
  After process/service death, reboot, or upgrade, an unfinished interval
  restarts conservatively: unlocks during the monitoring gap cannot be
  verified. Persisted timestamps are display hints, never evidence.
- Completion and the existing capped reward are committed in one DataStore
  edit. Duplicate callbacks cannot award twice or revive cancelled work.
  The completion screen survives closing the UI. Notification denial or a
  muted completion channel does not prevent the reward.
- No manifest permissions, analytics payloads, or network fields were added.
  Lock/unlock observations are not sent to the server or witnesses.

References:
- https://developer.android.com/reference/android/app/KeyguardManager#isKeyguardLocked()
- https://developer.android.com/reference/android/content/Intent#ACTION_USER_PRESENT
- https://developer.android.com/develop/background-work/services/alarms

## Device acceptance checks

These require physical devices; JVM tests do not prove OEM event delivery.
Run on an API 26 device/emulator and recent Pixel/Samsung devices, including
an Android 13+ device with notification permission denied.

1. Start focus while unlocked; wait/use an uncontrolled app. Return: 20:00,
   no reward. Lock; unlock after a few minutes: 20:00. Relock: a full new
   20 minutes is required. Repeat with PIN, fingerprint, swipe and face
   unlock. Face authentication that leaves keyguard showing must not reset.
2. While locked, wake the screen, receive notifications, enable AOD, and
   dismiss a ringing alarm. None should reset the original interval.
3. Answer a normal incoming call from the lock screen, talk and hang up.
   Verify the interval continues. Unlock deliberately during another call:
   verify it resets, even while the dialer stays in front.
4. Leave the UI closed for the full session. Expect one completion alert and
   +10 only for the selected controlled app. Tap the alert: completion screen.
   Reopen again, or trigger another wake: no duplicate reward.
5. Test just-before-deadline versus at/after-deadline unlocks, including a
   delayed completion alarm. Only the former resets. Force Doze with adb,
   then verify notification delivery (possibly deferred) and one reward.
6. Cancel then immediately start another activity; an old timer must not
   reset or reward the replacement. Verify walking still awards normally
   and daily per-app caps and next-day expiry are unchanged.
7. Kill/restart the service/process or reboot partway through: an unfinished
   attempt requires a new full locked interval. Close only the UI without
   killing the service: the original interval continues.
8. Disable lock screen entirely: a screen-off phone without keyguard must
   never earn focus time. Changing wall time cannot fast-forward the timer.
9. Deny notifications: reward and completion screen still work. Re-enable
   notifications for a new session and verify the separate completion
   channel alerts while the protection notification remains quiet.
