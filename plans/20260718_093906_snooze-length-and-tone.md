# Fix snooze length and snooze tone

**Status:** completed

## The issue

Two user-reported problems, both caused by the same broken function
`ActiveAlarmState.scheduleSnooze()` in `ui/Receivers.kt`.

1. **Snooze length is ignored.** Every snooze entry point calls `scheduleSnooze()`
   without passing `snoozeMinutes`, so it always uses the hardcoded fallback `= 5`.
   The configured "Default snooze length" setting and the per-alarm `snoozeMinutes`
   value never reach the scheduler. The `ActiveAlarm` object does not even carry the
   snooze length. Result: snooze is always ~5 minutes, regardless of the setting.

2. **Wrong tone after snooze.** The snooze re-scheduler copies only `customToneName`
   and never `customToneUri`. It has no `uri` parameter, and no caller passes one.
   So an alarm using a custom sound (a picked file or a device ringtone) loses its
   URI on snooze. When it re-rings, `AudioEngine.playAudio()` gets an empty URI,
   can't find the name in its built-in list, and falls back to the system default
   alarm sound.

The plan carries **both** the snooze length and the tone URI through the whole
snooze flow, so the snooze fires after the configured minutes with the correct tone.

## Files to change

1. `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/Receivers.kt`
   - Add `snoozeMinutes: Int` and keep `uri` on the `ActiveAlarmState.ActiveAlarm`
     data class (uri already exists; add `snoozeMinutes` with default 5).
   - Add a `uri` parameter to `scheduleSnooze(...)` and set `customToneUri = uri` on
     the temp snooze `Alarm`.
   - In `scheduleSnooze`, use the passed `snoozeMinutes` (already a parameter) — no
     change to the signature there beyond adding `uri`.
   - `AlarmReceiver.onReceive`: when building the `ActiveAlarm`, also read
     `SNOOZE_MIN` from the intent and pass it into the `ActiveAlarm` (so the ringing
     alarm knows its own snooze length). The intent already carries `SNOOZE_MIN`.
   - `AlarmSnoozeReceiver.onReceive`: read `SNOOZE_MIN` and `URI` from the intent and
     pass `snoozeMinutes` + `uri` into `scheduleSnooze`.
   - `snooze(...)` helper: pass `current.uri` through to `scheduleSnooze`.

2. `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmService.kt`
   - Carry `SNOOZE_MIN` through the service intent (`startIntent` / `onStartCommand`)
     so the `ActiveAlarm` built by the service also has the snooze length.
   - Add `SNOOZE_MIN` and `URI` extras to the notification `snoozeIntent` sent to
     `AlarmSnoozeReceiver` (URI extra name already exists as `EXTRA_URI`).

3. `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmActivity.kt`
   - In `onSnooze`, pass `ring.uri` and `ring.snoozeMinutes` into `scheduleSnooze`.

4. `app/src/main/java/in/sreerajp/chronotune_smart_clock/ClockAppScreen.kt`
   - In the overlay `onSnooze`, pass `ring.uri` and `ring.snoozeMinutes` into
     `scheduleSnooze`.

## The plan for the fix

1. Extend `ActiveAlarm` with `snoozeMinutes: Int = 5` (it already has `uri`).
2. Populate `snoozeMinutes` wherever an `ActiveAlarm` is built from an intent that
   carries `SNOOZE_MIN` (AlarmReceiver, AlarmService).
3. Add a `uri: String?` parameter to `scheduleSnooze` and set `customToneUri` on the
   temp alarm so custom tones survive.
4. Update every `scheduleSnooze(...)` call site to pass the real `snoozeMinutes` and
   `uri`:
   - `AlarmActivity.onSnooze` → `ring.snoozeMinutes`, `ring.uri`
   - `ClockAppScreen` overlay `onSnooze` → `ring.snoozeMinutes`, `ring.uri`
   - `AlarmSnoozeReceiver` → intent `SNOOZE_MIN`, intent `URI`
   - `ActiveAlarmState.snooze()` helper → `current.snoozeMinutes`, `current.uri`
5. Make the notification `snoozeIntent` in `AlarmService` include `SNOOZE_MIN` and
   `URI` so the notification-action snooze path has the same data.

Defaults stay at 5 minutes everywhere so behavior is unchanged for alarms that don't
set a value.

## Verification

- Build the app.
- Create an alarm with a **custom tone (device ringtone / picked file)** and a
  non-default snooze length (e.g. 15 min). Let it ring, snooze from the full-screen
  alarm, and confirm: (a) it re-rings after ~15 min, and (b) it plays the same custom
  tone, not the system default.
- Repeat snoozing from the notification action and the in-app overlay.
- Confirm a built-in-tone alarm still snoozes correctly.

## Out of scope

- The seconds-truncation (snooze time floored to the whole minute) is left as-is; it
  keeps snooze aligned to the minute and is not part of the reported problem.
