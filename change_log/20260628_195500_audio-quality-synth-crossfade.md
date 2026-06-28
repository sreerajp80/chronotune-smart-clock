# Change log: richer synthesis + configurable Music-schedule crossfade

Implements plan [plans/20260628_193500_audio-quality-synth-crossfade.md](../plans/20260628_193500_audio-quality-synth-crossfade.md).

## Summary

Made the built-in synth tones noticeably richer, added a true crossfade between Music-schedule
playlist items, and exposed the crossfade behavior in a new **Settings → Music** tab (enable, duration,
curve, loudness normalization). Stereo and tone-preview were already implemented and left untouched.

## Changes by file

### `app/.../audio/AudioEngine.kt`
- **Richer synthesis.** Added companion helpers `harmonicSample()` (additive overtones, weights
  `[1.0, 0.45, 0.22, 0.12, 0.06]`, normalized) and `adsrEnvelope()` (soft attack / sustain plateau /
  release tail), replacing the bare-sine + linear-decay in the looping `playSynthArpeggio` and the new
  `SynthVoice` render. Stereo detune and the existing fade-in are preserved.
- **Configurable crossfade.** Added `enum CrossfadeCurve { LINEAR, EQUAL_POWER }`. `playPlaylist` gained
  `crossfadeMs`, `curve`, and `normalize` params (defaulted, so existing callers are unaffected). The
  loop now overlaps consecutive items via a `Voice` abstraction, ramping gains with `crossfade()`
  (linear or equal-power cos/sin). `crossfadeMs <= 0` or an unknown item duration falls back to the
  prior hard-cut behavior. `current`/`incoming` voices are torn down in a `NonCancellable finally` on
  `stop()`.
- **Voice abstraction.** New private `Voice` sealed class with `FileVoice` (MediaPlayer, gain via
  `setVolume`, completion-flagged) and `SynthVoice` (MODE_STATIC AudioTrack, accurate
  `elapsedMs`/`finished` via `playbackHeadPosition`, gain via `setVolume`). Replaces the removed
  `playFileSuspending` and `playSynthArpeggioOnce`.
- **Loudness normalization.** When enabled, `FileVoice` applies a per-URI `normGain` from
  `computeFileNormGain()` → `measurePrefixRms()`: decodes ~4 s of PCM (MediaExtractor + MediaCodec),
  computes RMS, returns `TARGET_RMS / rms` clamped to ±6 dB (0.5–2.0), cached per URI, fail-safe to
  unity. Synth voices use the reference level (unity). No decode/overhead when normalization is off.
- Added `import kotlin.math.cos`.

### `app/.../AppPrefs.kt`
- Added music-crossfade prefs mirroring the fade-in pattern: `crossfadeEnabled` (default `true`),
  `crossfadeMs` (default `3000`, clamped `0..12000`), `crossfadeCurve` (default `EQUAL_POWER`),
  `loudnessNormalize` (default `false`) — each with a `StateFlow`, setter, and read in `init()`.
- Added cold-read getters for the alarm-firing path: `getCrossfadeMs()` (returns `0` when disabled),
  `getCrossfadeCurve()`, `isLoudnessNormalize()`, plus `curveToKey`/`curveFromKey` string mapping and
  `CROSSFADE_MIN_MS/MAX_MS/DEFAULT_MS` constants.

### `app/.../ui/Receivers.kt`
- In the `type == "MUSIC"` branch, read the three crossfade prefs and pass them to `playPlaylist(...)`.
  Alarms (non-music) are unchanged.

### `app/.../SettingsScreen.kt`
- Added a 5th "Music" tab (`Icons.Default.QueueMusic`) and a `selectedTab == 4` branch rendering the new
  `MusicSchedulerSettings` composable: an enable toggle, a crossfade-length slider (0–12 s in 0.5 s
  steps, dimmed/disabled when off), an Equal-power/Linear curve chip selector, a loudness-normalization
  toggle, and an explanatory caption (music-only). Added `ChunkyValueSlider` (generalized from
  `ChunkyOpacitySlider` with an `enabled` flag) and a small `CurveChip`. Added imports for
  `androidx.compose.ui.draw.alpha` and `AudioEngine`.

## Verification
- `./gradlew :app:compileDebugKotlin` and `:app:assembleDebug` both succeed (only unrelated
  native-access warnings).
- Runtime/audio behavior to be confirmed on-device per the plan's verification steps (warmer tones,
  smooth crossfades incl. loop-around, curve difference, hard-cut when disabled, normalization level
  matching, clean stop).

## Notes / deviations
- Crossfade defaults to **ON at 3 s** (feature live out of the box); normalization defaults **OFF**.
- Normalization is bounded prefix-RMS matching, not full EBU-R128/ReplayGain (as flagged in the plan).
