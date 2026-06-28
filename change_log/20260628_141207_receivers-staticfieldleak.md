# Change Log — Receivers.kt StaticFieldLeak warning

Implements plan: [20260628_141207_receivers-staticfieldleak.md](../plans/20260628_141207_receivers-staticfieldleak.md)

## Changes

### [AudioEngine.kt](../app/src/main/java/in/sreerajp/chronotune_smart_clock/audio/AudioEngine.kt)
- Constructor changed from `class AudioEngine(private val context: Context)` to
  `class AudioEngine(context: Context)` with `private val context: Context = context.applicationContext`.
  The engine now structurally holds only the application context, so it can never leak an
  Activity regardless of caller.

### [Receivers.kt](../app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/Receivers.kt)
- Added `import android.annotation.SuppressLint`.
- Annotated the static `audioEngine` field in `ActiveAlarmState` with
  `@SuppressLint("StaticFieldLeak")` plus a justifying comment (the engine only ever holds the
  application context).

No behavior change — the engine already received the application context at its single call
site; this makes the guarantee explicit and silences the false-positive Lint warning honestly.
