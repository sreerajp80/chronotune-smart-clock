# Alarm / Wake Experience — Crescendo, Dismiss Challenges, Smart Snooze, Next-Alarm Indicator & Widget

**Status:** approval_pending

## What the issue is

The current alarm experience is functional but blunt:

1. **Flat volume.** `AudioEngine` plays every alarm at a fixed loudness from the first millisecond (`mp.setVolume(volume, volume)` for files; baked-in gain for the synth). There is no gentle wake.
2. **Trivial to dismiss / endless snooze.** The ringing overlay's **DISMISS** button stops the alarm instantly, and **SNOOZE** can be tapped forever. There is nothing to guarantee the user is actually awake, and no cap on snoozing. (Also, the per-alarm `snoozeMinutes` value is never honored at ring time — the overlay hardcodes "5 MIN" and `scheduleSnooze` defaults to 5.)
3. **No "next alarm" visibility.** Neither the in-app clock screen nor the home screen shows when the next alarm will fire (only the OS status-bar chip, via `setAlarmClock`).

This plan adds the four requested wake-experience features.

---

## Feature-by-feature plan

### Feature 1 — Gradual volume fade-in (crescendo alarm)

**Goal:** ramp from near-silent to the alarm's target volume over a configurable 0–30 s window (off by default-compatible; default 20 s for new alarms).

**Approach**
- **`AudioEngine.kt`** — add a `fadeInMs: Long = 0L` parameter to `playAudio(...)` and `playPlaylist(...)`.
  - *Synth path* (`playSynthArpeggio` / `playSynthArpeggioOnce`): the synth already generates PCM in a loop, so the cleanest ramp is self-contained — track a running `totalSamplesWritten` counter and compute `gain = (totalSamples / fadeInSamples).coerceAtMost(1f)` per sample (where `fadeInSamples = fadeInMs/1000 * sampleRate`). Multiply the existing `volume * 0.5 * envelope` by `gain`. When `fadeInMs == 0` the gain is immediately 1f, preserving current behavior.
  - *MediaPlayer path* (built-in URI, default-ringtone fallback, and playlist files): launch a small coroutine (`fadeJob`) that steps the player volume from a low floor (`0.05f`) up to the target `volume` over `fadeInMs` (≈50 ms steps), calling `mp.setVolume(v, v)`. Store `fadeJob` and cancel it in `stop()`. Guard against the player being released mid-fade.
  - This composes with the existing `overrideAlarmStream()` logic — the stream stays pinned to max and the per-track scalar (now ramped) remains the sole loudness determinant.

**Plumbing the per-alarm setting** (`fadeInSeconds`)
- **`Models.kt`** — add `val fadeInSeconds: Int = 0` to `Alarm`.
- **`AppDatabase.kt`** — bump version 3 → 4, add `MIGRATION_3_4` (see "Database migration" below).
- **`ui/Receivers.kt`** —
  - `ActiveAlarmState.ActiveAlarm`: add `fadeInSeconds: Int = 0` (and the snooze fields from Feature 3).
  - `triggerAlarm(...)`: pass `fadeInMs = fadeInSeconds * 1000L` to `playAudio` (alarms only; music keeps flat volume).
  - `AlarmReceiver.onReceive`: read a `FADE_IN` extra and put it on the `ActiveAlarm`; `rescheduleNextOccurrence` carries it forward.
- **`ui/AlarmScheduler.kt`** — `scheduleAlarm` puts `FADE_IN` = `alarm.fadeInSeconds` on the broadcast intent.
- **`ui/AlarmService.kt`** — add `EXTRA_FADE_IN`; read it in `onStartCommand`, store on `ActiveAlarm`, serialize it in `startIntent`.
- **`ui/ClockViewModel.kt`** — `checkInAppTriggers` builds an `ActiveAlarm` with `fadeInSeconds = alarm.fadeInSeconds`; `addAlarm`/`updateAlarm` carry the new field.
- **`AlarmsScreen.kt`** (`AlarmEditDialog`) — add a "Gradual volume (crescendo)" row: a small segmented/slider control (Off / 10s / 20s / 30s). Extend the `onSave` lambda signature and the call sites in `AlarmsScreen`.

---

### Feature 2 — Dismiss challenges (math, shake; QR/NFC optional — see note)

**Goal:** when configured, the user must complete a challenge before the alarm will dismiss, so they can't dismiss half-asleep.

**Data**
- **`Models.kt`** — add `val dismissChallenge: String = "NONE"` to `Alarm` (values `NONE` | `MATH` | `SHAKE`). Constants on the `Alarm` companion.
- **`AppDatabase.kt`** — column added in `MIGRATION_3_4`.

**Ringing UI gating** — **`AlarmRingingOverlay.kt`**
- Add a `dismissChallenge` value to `ActiveAlarm` (plumbed exactly like `fadeInSeconds`).
- The overlay's **DISMISS** button:
  - `NONE` → calls `onDismiss()` directly (current behavior).
  - `MATH`/`SHAKE` → opens an in-overlay challenge panel; `onDismiss()` fires **only on success**. A "Cancel" returns to the ringing screen (alarm keeps sounding).
- **MATH challenge** — generate e.g. `a × b` / `a + b` (medium difficulty: two 2-digit-ish operands), numeric input + Submit; wrong answer shakes the field and regenerates. Pure Compose, no sensors.
- **SHAKE challenge** — register a `SensorManager` accelerometer listener inside a `DisposableEffect`; count shake peaks above a threshold; show "Shake! (n/15)" progress; success at the target count. Unregister on dispose. Runs fine inside `AlarmActivity`.

**Notification consistency** — **`ui/AlarmService.kt`**
- When `dismissChallenge != NONE`, the heads-up notification must **not** offer a no-challenge Dismiss. `buildNotification` will, in that case, replace the "Dismiss" action with an "Open" action that launches `AlarmActivity` (so the user faces the challenge). The Snooze action remains (subject to Feature 3 limits). The silent/demoted notification behaves the same.

**Render parity** — the overlay is shown in **`ui/AlarmActivity.kt`** and as the fallback in **`ClockAppScreen.kt`**; both already delegate to `AlarmRingingOverlay`, so gating lives in one place and both paths inherit it.

> **QR / NFC (optional, recommended as a follow-up):** scanning a saved QR/barcode requires **CameraX** (present in the version catalog but commented out in `app/build.gradle.kts`) **plus a barcode decoder** (ML Kit → pulls in Play Services, or `com.google.zxing:core` → new dependency). NFC needs no new dependency but requires NFC hardware and a tag-registration flow. Because both add real dependency/scope weight, this plan implements **MATH + SHAKE** fully and treats **QR/NFC as a separate phase**. I'll confirm with you at approval whether to fold QR in now (the model would store `dismissChallenge = "QR"` + a saved tag value on the `Alarm`, add a scanner screen, and uncomment the CameraX deps).

---

### Feature 3 — Snooze limits & smarter snooze

**Goal:** cap how many times an alarm can be snoozed, and optionally auto-shorten each successive snooze.

**Data**
- **`Models.kt`** — add to `Alarm`: `val maxSnoozes: Int = 0` (0 = unlimited) and `val snoozeAutoShorten: Boolean = false`. (`snoozeMinutes` already exists.)
- **`AppDatabase.kt`** — columns added in `MIGRATION_3_4`.

**Carry snooze state at runtime** (not persisted on the entity — it's per-ring)
- **`ui/Receivers.kt`** — `ActiveAlarm` gains `snoozeMinutes`, `maxSnoozes`, `snoozeAutoShorten`, and `snoozeCount` (how many times *this* ring has already been snoozed).
- `ActiveAlarmState.scheduleSnooze(...)` is reworked to accept `snoozeCount`, `maxSnoozes`, `snoozeAutoShorten`, `baseSnoozeMinutes`, and to **build its own broadcast intent** (instead of reusing `scheduleAlarm`) so it can attach `SNOOZE_COUNT`, `MAX_SNOOZES`, `AUTO_SHORTEN`, `SNOOZE_MIN` extras and still use the privileged `setAlarmClock` path (extract a small shared helper in `AlarmScheduler`).
- **Auto-shorten formula:** snooze #1 = base minutes; each subsequent snooze = half the previous, floored at 1 min (e.g. 10 → 5 → 2 → 1 → 1). When `snoozeAutoShorten` is false, every snooze uses `base`.
- **Cap enforcement:** the Snooze affordance is hidden / disabled once `maxSnoozes > 0 && snoozeCount >= maxSnoozes`:
  - **`AlarmRingingOverlay.kt`** — hide the SNOOZE button at the cap; show the real next-snooze duration in its label (fixing the hardcoded "5 MIN").
  - **`ui/AlarmService.kt`** — `buildNotification`/`buildSilentNotification` omit the Snooze action at the cap; the snooze `PendingIntent` carries the count + metadata.
  - **`ui/Receivers.kt`** `AlarmSnoozeReceiver` — read the carried extras, compute next duration, and re-arm with `snoozeCount + 1`.

**Plumbing** — `AlarmReceiver.onReceive` reads `SNOOZE_COUNT` (default 0), `MAX_SNOOZES`, `AUTO_SHORTEN`, `SNOOZE_MIN` and seeds `ActiveAlarm`; `AlarmScheduler.scheduleAlarm` adds `MAX_SNOOZES`/`AUTO_SHORTEN` extras (count starts at 0 for a fresh alarm); `AlarmService` serializes all four via `startIntent`; `ClockViewModel.checkInAppTriggers` seeds them from the `Alarm`.

**UI** — **`AlarmsScreen.kt`** `AlarmEditDialog`: a "Snooze" section with snooze-length, a "Limit snoozes" stepper (Off/1/2/3/5), and an "Auto-shorten each snooze" switch. Extend `onSave` + call sites + `ClockViewModel.addAlarm`/`updateAlarm`.

---

### Feature 4 — "Next alarm" indicator + dedicated home widget

**Shared computation (single source of truth)**
- Extract the next-trigger logic currently private in `AlarmScheduler.nextTriggerTime` into a reusable util — **new file `ui/NextAlarm.kt`**: `object NextAlarm { fun nextTriggerMillis(alarm, nowMillis): Long? ; fun nextAcross(alarms): Pair<Alarm, Long>? }` (respects repeat days + pause window + disabled). Refactor `AlarmScheduler` to delegate to it so the widget, the in-app chip, and the scheduler never diverge.

**(a) In-app indicator** — **`WorldClockScreen.kt`** + **`ui/ClockViewModel.kt`**
- ViewModel exposes `nextAlarm: StateFlow<NextAlarmInfo?>` derived from `alarms` + the per-minute tick.
- Under the digital clock, render a chip: `⏰ Next · Tomorrow 7:00 AM · Work` (relative day label Today/Tomorrow/weekday + formatted time honoring `is24Hour`), hidden when no alarm is enabled.

**(b) Home-screen widget** — new files
- **`widget/NextAlarmWidgetProvider.kt`** — `AppWidgetProvider` modeled on `DigitalClockWidgetProvider`. `renderAll(context)` reads alarms once off the main thread (new `AlarmDao.getAllAlarmsOnce()`), computes the next via `NextAlarm.nextAcross(...)`, and fills the layout (icon + time + label, or "No upcoming alarm"). Tapping launches `MainActivity`. No per-second ticking needed.
- **`res/layout/widget_next_alarm.xml`** — alarm glyph + time + label, reusing the existing `widget_background_a*` drawables.
- **`res/xml/next_alarm_widget_info.xml`** — provider info (`updatePeriodMillis="0"`, preview, resize), plus a `widget_next_alarm_label`/`_description` in `strings.xml`.
- **`AndroidManifest.xml`** — register the new `<receiver>`.
- **Refresh triggers** (it's event-driven, not clock-driven):
  - **`ui/ClockViewModel.kt`** alarm mutations (`addAlarm`/`updateAlarm`/`toggleAlarm`/`deleteAlarm`) call `NextAlarmWidgetProvider.renderAll(context)`.
  - **`ui/Receivers.kt`** — `BootReceiver.rescheduleAll` and `AlarmReceiver` (after fire/re-arm) call `renderAll` so the widget advances to the next occurrence.

---

## Database migration

**`AppDatabase.kt`** — version 3 → 4, add `MIGRATION_3_4` and register it:
```sql
ALTER TABLE alarms ADD COLUMN fadeInSeconds INTEGER NOT NULL DEFAULT 0;
ALTER TABLE alarms ADD COLUMN dismissChallenge TEXT NOT NULL DEFAULT 'NONE';
ALTER TABLE alarms ADD COLUMN maxSnoozes INTEGER NOT NULL DEFAULT 0;
ALTER TABLE alarms ADD COLUMN snoozeAutoShorten INTEGER NOT NULL DEFAULT 0;
```
(Defaults keep every existing alarm behaving exactly as today: no fade, no challenge, unlimited fixed-length snooze.)

---

## Full list of files to be changed

**Data**
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/data/Models.kt` — 4 new `Alarm` fields.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/data/AppDatabase.kt` — v4 + `MIGRATION_3_4`.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/data/Daos.kt` — `getAllAlarmsOnce()`.

**Audio**
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/audio/AudioEngine.kt` — fade-in ramp.

**Alarm runtime / plumbing**
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/Receivers.kt` — `ActiveAlarm` fields, fade, snooze-count logic, widget refresh.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmScheduler.kt` — new extras, snooze-intent helper, delegate to `NextAlarm`.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmService.kt` — new extras + challenge-aware notification actions.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ClockViewModel.kt` — seed new fields, `nextAlarm` flow, widget refresh.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/NextAlarm.kt` — **new** shared next-trigger util.

**UI**
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmRingingOverlay.kt` — challenge gating + dynamic snooze label/cap.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmsScreen.kt` — editor controls (crescendo, challenge, snooze limit/shorten) + `onSave` signature.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/WorldClockScreen.kt` — next-alarm chip.

**Widget**
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/widget/NextAlarmWidgetProvider.kt` — **new**.
- `app/src/main/res/layout/widget_next_alarm.xml` — **new**.
- `app/src/main/res/xml/next_alarm_widget_info.xml` — **new**.
- `app/src/main/res/values/strings.xml` — widget label/description strings.
- `app/src/main/AndroidManifest.xml` — register the widget receiver.

*(QR/NFC, if approved for this phase, would additionally touch `app/build.gradle.kts`, `gradle/libs.versions.toml`, a new scanner screen, and `Alarm`/`AlarmEditDialog` for the saved tag value — otherwise deferred.)*

---

## Risks & verification
- **Migration:** `fallbackToDestructiveMigration` is the backstop, but `MIGRATION_3_4` preserves data; will verify an upgrade keeps existing alarms.
- **Sensor lifecycle:** the shake listener must unregister on dispose to avoid leaks; will verify via the `AlarmActivity` path.
- **Snooze-count correctness:** verify count increments across repeated snoozes and the cap hides the button on both the overlay and the notification; verify auto-shorten produces the expected sequence.
- **Build:** `./gradlew assembleDebug` (or `:app:compileDebugKotlin`) after implementation; manual smoke test of an alarm ring → fade → challenge → snooze cap → dismiss, plus widget add/refresh.

---

## Open question for approval
1. **Include QR/NFC dismiss now, or defer?** I recommend shipping **MATH + SHAKE** in this plan and doing **QR/NFC as a follow-up** (it needs new camera + barcode dependencies / NFC hardware). Let me know if you'd rather fold QR in now.
