# Receivers.kt — StaticFieldLeak warning on `audioEngine`

**Status:** completed

## Issue
[Receivers.kt:34](app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/Receivers.kt#L34)
warns: **Do not place Android context classes in static fields ... this is a memory leak**.

`ActiveAlarmState` is a Kotlin `object` (static singleton) that holds
`private var audioEngine: AudioEngine? = null`. `AudioEngine` stores a `Context` field
([AudioEngine.kt:15](app/src/main/java/in/sreerajp/chronotune_smart_clock/audio/AudioEngine.kt#L15)),
so Lint flags the static reference as a potential leak.

In practice there is **no real leak**: the only construction site already passes
`context.applicationContext` (Receivers.kt:39), and the Application context lives for the whole
process. Lint can't prove the stored context is the application context, so it warns anyway.

## Files to change
- [Receivers.kt](app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/Receivers.kt)
- [AudioEngine.kt](app/src/main/java/in/sreerajp/chronotune_smart_clock/audio/AudioEngine.kt)

## Plan for the fix
1. **AudioEngine.kt** — harden the constructor so it can never hold an Activity context:
   change `class AudioEngine(private val context: Context)` to store the application context:
   `private val context: Context = context.applicationContext` (constructor param renamed to
   avoid shadowing). This makes the "no leak" guarantee structural, not dependent on callers.
2. **Receivers.kt** — annotate the static field with
   `@SuppressLint("StaticFieldLeak")` plus a short comment explaining it only ever holds an
   AudioEngine backed by the application context (so the suppression is justified, not blind).
   Add the `import android.annotation.SuppressLint`.

No behavior change — the audio engine already received the application context; this makes that
guarantee explicit and silences the false-positive Lint warning honestly.
