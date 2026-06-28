# Change Log — Global Fade-In Setting + Stereo Synth Tones

**Implements:** `plans/20260628_190407_global-fadein-stereo-tone.md`
**Date:** 2026-06-28

## Summary
Added a global alarm fade-in (gradual volume) setting surfaced as a new **"Alarm"** tab on the
Settings & Info page, and made the built-in synth alarm tones play in **stereo** (they were
mono). Build verified with `:app:compileDebugKotlin` (BUILD SUCCESSFUL).

## Changes by file

### `AppPrefs.kt`
- Added `KEY_FADE_IN` pref and `FADE_IN_MS = 20_000L` constant.
- Added `fadeInEnabled: StateFlow<Boolean>` (default `false`) + `setFadeInEnabled(...)`.
- `init(...)` now loads the fade-in pref alongside `is24Hour`.
- Added `getFadeInMs(context): Long` — a direct `SharedPreferences` read (returns `FADE_IN_MS`
  when enabled, else `0L`) so it is correct even when an alarm fires in a cold process.

### `audio/AudioEngine.kt`
- Added a `fadeJob` field, cancelled first in `stop()` so a fade can't touch a released player.
- `playAudio(...)` gained a `fadeInMs: Long = 0L` parameter.
  - MediaPlayer paths (custom URI + default-ringtone fallback) now call `applyInitialVolume(...)`
    (starts at a low floor when fading) and `startMediaFade(...)` on prepared, which steps the
    volume from the floor up to target over `fadeInMs` in ~50 ms increments, guarding against a
    released player.
  - Synth path passes `fadeInMs` through.
- `playSynthArpeggio(...)` gained `fadeInMs` and now:
  - outputs **stereo** (`CHANNEL_OUT_STEREO`, interleaved L/R buffer);
  - applies a per-sample fade gain (`totalFrames / fadeFrames`, capped at 1f);
  - widens the tone via a subtle right-channel detune (`stereoDetune = 1.004`).
- `playSynthArpeggioOnce(...)` (music playlist) is now **stereo** too (kept flat — no fade).

### `ui/Receivers.kt`
- `ActiveAlarmState.triggerAlarm` ALARM branch now passes
  `fadeInMs = AppPrefs.getFadeInMs(context)` to `playAudio`. MUSIC stays flat.

### `SettingsScreen.kt`
- Added a 4th tab **"Alarm"** (`Icons.Default.Alarm`, index 3).
- New tab content: a `ChunkyAppearanceToggle` ("Gradual volume (fade-in)") bound to
  `AppPrefs.fadeInEnabled` / `setFadeInEnabled`, plus an explanatory caption.

## Notes / deviations from plan
- Fade duration is fixed at 20 s (the plan default; the on/off switch was chosen over a
  selectable-duration control).
- The Alarm tab was appended as index 3 (rather than inserted at index 1) to leave the existing
  About/Appearance/Permissions `when` branches untouched.
- No database migration, no new intent extras, no manifest changes (setting is global, read at
  ring time).

## Verification
- `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL.
- Recommended manual smoke test: toggle fade-in ON, fire an alarm on a synth tone (should ramp
  over ~20 s and sound stereo-wide) and on a custom file (should ramp); toggle OFF → instant
  full volume as before.
