# Audio quality: richer synthesis + configurable playlist crossfade (Music Scheduler settings)

**Status:** completed

## Context / what the request asked for

Original request listed three audio-quality items. After reading the code, two are **already done**
(the request note was stale, pre-dating the last commit):

- **Stereo is already done** — both synth paths render interleaved `CHANNEL_OUT_STEREO` with a right-channel
  detune (`stereoDetune = 1.004`).
  [AudioEngine.kt:193-263](../app/src/main/java/in/sreerajp/chronotune_smart_clock/audio/AudioEngine.kt#L193-L263).
- **Tone preview while picking is already done** — the picker has a dedicated `previewEngine` with a
  per-row play/stop toggle.
  [AlarmsScreen.kt:1139-1151](../app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmsScreen.kt#L1139-L1151).

Remaining + newly-requested work:

1. **Richer synthesis** (harmonics + ADSR) for the built-in tones.
2. **Crossfade between playlist items** in `playPlaylist`.
3. **Make the crossfade configurable in Settings.** `playPlaylist` powers **Music schedules** only
   (`type == "MUSIC"`, [Receivers.kt:46-62](../app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/Receivers.kt#L46-L62));
   alarms never use it. So add a new **"Music"** tab to Settings (Music Scheduler) holding the crossfade
   controls, as global prefs (mirroring the existing fade-in pattern in `AppPrefs`).

Crossfade settings to expose (all four confirmed):
- **Enable crossfade** (toggle) — off = hard cut (today's behavior).
- **Crossfade duration** (slider, 0–12 s, default 3 s) — overlap window.
- **Crossfade curve** (Equal-power vs Linear) — equal-power keeps perceived loudness flat through the blend.
- **Loudness normalization** (toggle) — even out level differences between items so transitions don't jump.

## The issue

1. **Synthesis is thin** — every note is a bare sine with a linear decay; no overtones, abrupt onset.
2. **No crossfade** — `playPlaylist` plays items strictly sequentially (each voice blocks until done),
   giving a hard cut + gap between items and on every loop-around.
3. **Nothing is configurable** — the crossfade behavior has no UI and no persisted settings.

## Files to be changed

- `app/src/main/java/in/sreerajp/chronotune_smart_clock/audio/AudioEngine.kt` — synth + crossfade + curve + normalization.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/AppPrefs.kt` — new music-crossfade prefs.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/SettingsScreen.kt` — new "Music" tab + controls.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/Receivers.kt` — read prefs, pass to `playPlaylist`.

No new dependencies. No UI changes to the tone picker (preview already exists).

## Plan

### Part 1 — Richer synthesis (harmonics + ADSR) — `AudioEngine.kt`

Add two private helpers used by both synth render paths:

- `harmonicSample(i, freq, sampleRate): Double` — additive synthesis: fundamental + overtones with
  weights e.g. `[1.0, 0.45, 0.22, 0.12, 0.06]`, normalized by the weight sum so output stays in ~[-1, 1].
  Warmer, organ/bell-like timbre vs. a bare sine.
- `adsrEnvelope(i, noteSamples): Double` — gain in [0, 1]: soft attack (~4%), gentle decay to a sustain
  plateau (~0.65), smooth release tail (~30%). Replaces the abrupt linear decay.

Replace the per-sample core in both loops (looping `playSynthArpeggio` and the playlist synth render) so:

```kotlin
val env  = adsrEnvelope(i, noteDurationSamples)
val amp  = Short.MAX_VALUE * volume * 0.5 * env * fade
val left  = (harmonicSample(i, freq, sampleRate) * amp).toInt()
val right = (harmonicSample(i, freq * stereoDetune, sampleRate) * amp).toInt()
```

Existing `0.5` headroom + `coerceIn` clamp prevent clipping; fade-in and detune behavior are preserved.

### Part 2 — Configurable crossfade in `playPlaylist` — `AudioEngine.kt`

Introduce a `CrossfadeCurve` enum (`LINEAR`, `EQUAL_POWER`) in `AudioEngine`'s companion and extend the
signature (defaults keep existing callers working):

```kotlin
fun playPlaylist(
    items: List<PlaylistItem>,
    volume: Float,
    durationMs: Long?,
    crossfadeMs: Long = 0L,                 // 0 = hard cut (today's behavior)
    curve: CrossfadeCurve = CrossfadeCurve.EQUAL_POWER,
    normalize: Boolean = false,
)
```

Rewrite the playback loop to overlap consecutive items, ramping loudness via `setVolume()` (no need to
re-bake gain into samples). A small private `Voice` abstraction unifies files and synth:

```kotlin
private sealed class Voice {
    abstract val durationMs: Long          // <= 0 = unknown
    abstract fun setGain(g: Float)         // 0..1, applied on top of playlist volume * normGain
    abstract fun elapsedMs(): Long
    abstract fun finished(): Boolean
    abstract fun release()
}
```

- **FileVoice** — wraps a `MediaPlayer`, blocking `prepare()` on `Dispatchers.IO`; `durationMs` from
  `getDuration()` (unknown if ≤0); `elapsedMs` from `currentPosition`; `finished` via completion flag;
  `setGain` → `mp.setVolume(volume*normGain*g, …)`.
- **SynthVoice** — renders one arpeggio cycle (using the new harmonic+ADSR code) into a `MODE_STATIC`
  `AudioTrack`, so `getPlaybackHeadPosition()` gives accurate `elapsedMs`/`finished`; `durationMs` =
  `totalFrames*1000/sampleRate`; `setGain` → `track.setVolume(volume*normGain*g)`.

Orchestration:

```
loop:
  incoming = createVoice(item)                 // starts at gain 0 when a previous voice plays
  xf = if (crossfadeMs<=0) 0
       else min(crossfadeMs, incoming.dur/2, current?.dur/2)   // shrink/skip if durations unknown
  if current == null || xf == 0: incoming.setGain(1f); current?.release()
  else: crossfade(out=current, in=incoming, xf, curve); current.release()
  current = incoming
  wait until current.elapsedMs() >= current.durationMs - xf  (or current.finished()), poll ~40 ms
```

`crossfade(out, in, ms, curve)` steps both gains over ~40 ms increments with fraction `f = s/steps`:
- **LINEAR:** `out = 1-f`, `in = f`.
- **EQUAL_POWER:** `out = cos(f·π/2)`, `in = sin(f·π/2)` (constant total power, no mid-blend dip).

When `crossfadeMs <= 0` or a duration is unknown → fall back to the current hard-cut-on-finish behavior.

**Lifecycle:** `current`/`incoming` are hoisted locals; `finally { withContext(NonCancellable){ current?.release(); incoming?.release() } }`
guarantees teardown on `stop()` (which already cancels `playlistJob` + restores the alarm stream).
Single-item playlists crossfade on each loop-around (outgoing tail vs. a fresh incoming instance).
The now-unused `playFileSuspending` / `playSynthArpeggioOnce` are replaced by the `Voice` classes.

### Part 3 — Loudness normalization — `AudioEngine.kt`

When `normalize == true`, each `Voice` gets a `normGain` so items play at a comparable perceived level:

- **SynthVoice** — known, consistent amplitude → reference gain `1.0` (defines the target level).
- **FileVoice** — estimate loudness by decoding a short prefix (cap ~4 s / a few hundred-K frames) via
  `MediaExtractor` + `MediaCodec` to PCM, compute RMS, then `normGain = (targetRms / measuredRms)` clamped
  to a sane window (≈ ±6 dB, i.e. `0.5..2.0`). Result cached per-URI; decode failure/timeout → unity gain
  (safe no-op). Analysis runs off the audio thread inside `FileVoice` creation (already on `Dispatchers.IO`).

When `normalize == false`, `normGain = 1.0` everywhere (no decode, no overhead).

*Note on scope:* this is prefix-RMS normalization (lightweight, bounded), not full EBU-R128/ReplayGain.
It evens out gross level differences between tracks; it does not do per-track integrated-loudness analysis
of the whole file. Called out so expectations are clear. If the MediaCodec pre-scan proves problematic on
some devices, the fallback is Android's `LoudnessEnhancer` effect (boost-only) — but the plan implements
the RMS pre-scan as primary.

### Part 4 — Persisted settings — `AppPrefs.kt`

Add keys + in-memory `StateFlow`s + setters + cold-read getters (used by `Receivers` in a cold process),
mirroring the existing `fadeIn` pattern:

| Pref | Type | Default | Range |
|---|---|---|---|
| `music_crossfade_enabled` | Bool | `true` | — |
| `music_crossfade_ms` | Int (ms) | `3000` | `0..12000` |
| `music_crossfade_curve` | String (`equal_power`/`linear`) | `equal_power` | — |
| `music_loudness_normalize` | Bool | `false` | — |

Exposed as: `crossfadeEnabled`, `crossfadeMs`, `crossfadeCurve`, `loudnessNormalize` flows; setters
`setCrossfadeEnabled/…`; cold getters `isCrossfadeEnabled(ctx)`, `getCrossfadeMs(ctx)`
(returns 0 when disabled), `getCrossfadeCurve(ctx)` (→ `AudioEngine.CrossfadeCurve`),
`isLoudnessNormalize(ctx)`. Init values read in `AppPrefs.init`.

### Part 5 — Wire prefs into playback — `Receivers.kt`

In the `type == "MUSIC"` branch, before calling `playPlaylist`:

```kotlin
val xfMs   = AppPrefs.getCrossfadeMs(context)          // 0 when disabled
val curve  = AppPrefs.getCrossfadeCurve(context)
val norm   = AppPrefs.isLoudnessNormalize(context)
audioEngine?.playPlaylist(playlist, alarm.volume, durationMs, xfMs, curve, norm)
```

### Part 6 — Settings "Music" tab — `SettingsScreen.kt`

Add a 5th `Tab` ("Music", e.g. `Icons.Default.QueueMusic`) to the `PrimaryTabRow`, and a `when` branch
(`selectedTab == 4`) with a scrollable column. Reuse existing components/patterns:

- **Enable crossfade** — `ChunkyAppearanceToggle` bound to `AppPrefs.crossfadeEnabled`.
- **Crossfade duration** — a chunky value-slider row (built on the existing `ChunkyOpacitySlider` canvas,
  generalized to display seconds: value 0..1 ↔ 0..12 s, label e.g. `3.0 s`), persisting on drag-end via
  `setCrossfadeMs`. Disabled/dimmed when crossfade is off.
- **Crossfade curve** — a small two-chip segmented selector (Equal-power | Linear) writing
  `setCrossfadeCurve`. Disabled/dimmed when crossfade is off.
- **Loudness normalization** — `ChunkyAppearanceToggle` bound to `AppPrefs.loudnessNormalize`.
- Helper caption text explaining these apply to Music schedules only (not alarms).

## Out of scope

- Stereo, and tone preview (both already implemented).
- Shuffle / gapless / queue reordering (separate features, not crossfade knobs).
- Full integrated-loudness (R128/ReplayGain) analysis — see the note in Part 3.

## Verification

- Build (`./gradlew assembleDebug`).
- Preview built-in tones in the picker — noticeably warmer/rounder.
- Settings → Music: toggle crossfade, change duration, switch curve, toggle normalization; confirm values
  persist across app restart.
- Fire a multi-item Music schedule (synth + file mix): with crossfade on, transitions overlap smoothly
  (no gap, no clipping) over the configured duration and loop-around; equal-power vs linear audibly differ;
  with crossfade off, behavior is the old hard cut. With normalization on, a loud file and a quiet file
  play at closer perceived levels.
- Confirm Stop/dismiss tears down audio promptly with no lingering voices.
