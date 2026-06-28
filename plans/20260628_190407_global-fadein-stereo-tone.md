# Global Fade-In Setting + Stereo Synth Tones

**Status:** completed

> Supersedes **Feature 1** (per-alarm crescendo) of `plans/20260628_142530_alarm-wake-experience.md`.
> Instead of a per-alarm field + DB migration, fade-in becomes a single **global** toggle in
> Settings & Info. The other features of that plan are untouched.

## What the issue is

1. **No gentle wake / fade-in.** Every alarm plays at full target volume from the first
   millisecond. The user wants a global on/off switch for a gradual volume fade-in, surfaced
   as a new **"Alarm"** tab on the **Settings & Info** page. When enabled, alarms ramp up;
   when off, behavior is exactly as today.
2. **Synth tones are mono.** The built-in synth arpeggios use `CHANNEL_OUT_MONO`
   ([AudioEngine.kt:158](app/src/main/java/in/sreerajp/chronotune_smart_clock/audio/AudioEngine.kt#L158),
   [:284](app/src/main/java/in/sreerajp/chronotune_smart_clock/audio/AudioEngine.kt#L284)).
   Custom-file / default-ringtone playback (the `MediaPlayer` paths) is already stereo if the
   source is. The user wants the synth tones to be stereo too.

---

## Feature A — Global fade-in toggle (Settings & Info → Alarm tab)

**Storage — `AppPrefs.kt`**
- Add a `fadeInEnabled` pref (Boolean, default `false` to preserve current behavior) backed by
  `SharedPreferences`, exposed both as a `StateFlow<Boolean>` (for the Settings UI) **and** a
  direct synchronous getter `getFadeInMs(context): Long` that reads `SharedPreferences` straight
  off disk. The direct getter is required because `ActiveAlarmState.triggerAlarm` can run in a
  **cold process** (fired from `AlarmService` after the app was killed) where the in-memory
  `StateFlow` may not yet be `init`-ed.
- Fade duration: fixed sensible default of **20 s** when enabled (constant `FADE_IN_MS = 20_000L`).
  `getFadeInMs` returns `FADE_IN_MS` when enabled, else `0L`.
- Read `fadeInEnabled` in `AppPrefs.init(...)` alongside the existing `is24Hour` load.

**Audio — `AudioEngine.kt`**
- Add `fadeInMs: Long = 0L` parameter to `playAudio(...)`.
  - *MediaPlayer paths* (custom URI + default-ringtone fallback): launch a small `fadeJob`
    coroutine that steps the player volume from a low floor (`0.05f`) up to the target `volume`
    over `fadeInMs` in ~50 ms increments via `mp.setVolume(v, v)`. Start the player muted-low in
    `setOnPreparedListener`. Store `fadeJob`; cancel it in `stop()`. Guard every `setVolume`
    against the player being released mid-fade.
  - *Synth path* (`playSynthArpeggio`): pass `fadeInMs` through; track a running
    `totalSamplesWritten` counter and compute `gain = (total / fadeInSamples).coerceAtMost(1f)`
    where `fadeInSamples = fadeInMs/1000f * sampleRate`. Multiply into the existing
    `volume * 0.5 * envelope`. When `fadeInMs == 0` gain is immediately `1f` (unchanged behavior).
- `playPlaylist` (MUSIC schedules) is left flat — fade-in is an alarm-wake feature only.

**Trigger plumbing — `ui/Receivers.kt`**
- In `ActiveAlarmState.triggerAlarm`, for the `else` (ALARM) branch only, pass
  `fadeInMs = AppPrefs.getFadeInMs(context)` to `playAudio`. MUSIC keeps flat volume.
- No new intent extras / `ActiveAlarm` fields / DB changes — the setting is global, read at
  ring time from prefs. (Snoozed re-rings naturally pick up the same global setting.)

**UI — `SettingsScreen.kt`**
- Add a 4th tab **"Alarm"** (icon `Icons.Default.Alarm`) to the `PrimaryTabRow`, before/after
  the existing About/Appearance/Permissions tabs (placed as index 1, after About; existing tab
  indices shift — update the `when(selectedTab)` branches accordingly).
- The Alarm tab renders a `ChunkyAppearanceToggle` (reusing the existing component):
  - Title "Gradual volume (fade-in)", subtitle reflects state ("Alarms ramp up over 20 seconds"
    / "Alarms start at full volume"), bound to `AppPrefs.fadeInEnabled` /
    `AppPrefs.setFadeInEnabled(context, it)`.
  - A short explanatory caption below, matching the Appearance tab's styling.

## Feature B — Stereo synth tones

**`AudioEngine.kt`** — both `playSynthArpeggio` (streaming loop) and `playSynthArpeggioOnce`
(single cycle):
- Build the `AudioTrack` with `CHANNEL_OUT_STEREO` instead of `CHANNEL_OUT_MONO`.
- Generate an **interleaved** stereo buffer (`ShortArray(noteDurationSamples * 2)`): for each
  frame write `[Left, Right]`. To make it genuinely stereo (not dual-mono), apply a subtle
  stereo-width effect — a small per-channel phase/detune offset (e.g. right channel uses a
  slightly shifted angle), so the tone has natural width while staying centered and artifact-free.
- The existing per-sample fade gain (Feature A) and decay envelope multiply into both channels
  equally.
- Recompute `bufferSize` via `getMinBufferSize(..., CHANNEL_OUT_STEREO, ...)` and write the
  doubled-length buffer with the correct sample count.

---

## Full list of files to be changed
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/AppPrefs.kt` — fade-in pref (flow +
  direct getter + constant).
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/audio/AudioEngine.kt` — `fadeInMs` ramp
  (MediaPlayer + synth) and stereo synth output.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/Receivers.kt` — pass `getFadeInMs`
  to `playAudio` for ALARM.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/SettingsScreen.kt` — new "Alarm" tab +
  fade-in toggle.

No database migration, no new intent extras, no manifest changes.

---

## Risks & verification
- **Cold-process read:** verify `getFadeInMs` reads correctly when the alarm fires after the app
  is killed (direct `SharedPreferences` read, not the `StateFlow`).
- **Fade lifecycle:** ensure `fadeJob` is cancelled in `stop()` and never calls `setVolume` on a
  released `MediaPlayer`.
- **Stereo correctness:** verify buffer length / sample-count math (interleaved = 2× frames) so
  there's no truncation, clipping, or speed change; confirm tones sound centered + wider, not
  off-balance.
- **Build:** `./gradlew :app:compileDebugKotlin` (or `assembleDebug`); smoke-test an alarm ring
  with fade ON (ramps over ~20 s) and OFF (instant), on a synth tone (stereo) and a custom file.

## Open question
- Fade duration is fixed at **20 s** when enabled. If you'd prefer a selectable duration
  (e.g. Off / 10s / 20s / 30s) instead of a plain on/off, say so and I'll make the Alarm tab a
  segmented selector instead of a switch.
