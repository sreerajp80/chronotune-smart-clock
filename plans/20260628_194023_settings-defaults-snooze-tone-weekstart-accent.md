# Plan — Settings additions: default snooze, default tone, week-start day, accent color

**Status:** completed

## What is being added

Four new user settings, placed per your instruction:

- **Alarm tab** (Settings tab index 3):
  1. **Default snooze length** — seeds `Alarm.snoozeMinutes` for newly created alarms.
  2. **Default alarm tone** — seeds the tone preselected when configuring a new alarm.
- **Appearance tab** (Settings tab index 1):
  3. **Week-start day** — reorders the day-of-week chips/labels across Alarms and Music schedules.
  4. **Theme accent color** — overrides the app's `primary` accent color app-wide.

## The issue / motivation

These are global defaults/preferences. Today they are hardcoded:
- New alarms always get `snoozeMinutes = 5` (model default in [Models.kt:48](app/src/main/java/in/sreerajp/chronotune_smart_clock/data/Models.kt#L48)) — and snooze isn't editable per alarm, so there is no way to change it at all.
- New alarms always preselect `"Morning Breeze"` ([AlarmsScreen.kt:491](app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmsScreen.kt#L491)).
- Day chips are fixed Mon→Sun ([AlarmsScreen.kt:314](app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmsScreen.kt#L314), [AlarmsScreen.kt:721](app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmsScreen.kt#L721); [MusicSchedulerScreen.kt:229](app/src/main/java/in/sreerajp/chronotune_smart_clock/MusicSchedulerScreen.kt#L229), [MusicSchedulerScreen.kt:733](app/src/main/java/in/sreerajp/chronotune_smart_clock/MusicSchedulerScreen.kt#L733)).
- Accent color is the fixed Vermillion `primary` ([Theme.kt](app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/theme/Theme.kt), [Color.kt](app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/theme/Color.kt)).

## Design decisions (assumptions — flag if you want different)

1. **Default snooze** — selectable preset chips: **5, 10, 15, 20, 30 min** (default 5). Stored as Int. Day-of-week ordering and snooze are independent.
2. **Default alarm tone** — **built-in melodies only** (the existing 6: Morning Breeze, Cosmic Shimmer, Ocean Zen, Digital Alarm, Retro Chiptune, Deep Lofi Lounge). Stored as a single name string. Custom file/ringtone URIs are *not* offered as a global default (**confirmed by user**); the per-alarm editor still allows any tone/URI. Default `"Morning Breeze"`.
3. **Week-start day** — three options: **Monday** (default, current behavior), **Sunday**, **Saturday**. Stored as Int (1=Mon … 7=Sun semantics).
4. **Theme accent color** — **full custom color picker** (HSV: hue bar + saturation/value box + hex text field), any color allowed, plus a row of quick-pick swatches (Vermillion default, Blue, Teal, Violet, Gold, Sage) for convenience.
   - Stored as a single ARGB/hex Int (default = current Vermillion; light vs dark uses the one chosen value).
   - The chosen color becomes `primary`. **`onPrimary` is computed by luminance** (black or white, WCAG-style contrast) so content on the accent is always legible. `primaryContainer`/`onPrimaryContainer` are derived (blend accent toward surface for the container; contrast-pick the on-color).
   - **Legibility fix (decided: auto-contrast/robust):** the app currently hardcodes `Color.White` for content sitting on `primary` backgrounds in ~17 places. Each such site is replaced with `MaterialTheme.colorScheme.onPrimary` so any picked color stays readable. Sites that sit on a *fixed/non-accent* background (e.g. parts of the alarm ringing overlay) are audited and left alone if they are not on the accent.

## Files to be changed

1. **`AppPrefs.kt`** — add four prefs with keys, defaults, `StateFlow`s, setters, cold-read getters, and load them in `init()`:
   - `defaultSnoozeMinutes: StateFlow<Int>` (default 5) + `setDefaultSnoozeMinutes` + `getDefaultSnoozeMinutes(ctx)`
   - `defaultAlarmTone: StateFlow<String>` (default "Morning Breeze") + setter + getter
   - `weekStartDay: StateFlow<Int>` (default 1=Mon) + setter; plus a helper `orderedWeekDays(start): List<Int>` returning the 7 day-numbers (1..7) rotated to begin at the chosen start.
   - `accentColor: StateFlow<AccentColor>` (enum, default Vermillion) + setter. Define the `AccentColor` enum (key + light/dark `Color` triples) — likely in a new small file `ui/theme/AccentColors.kt` to keep `Color`/theme concerns out of `AppPrefs`, with `AppPrefs` storing only the enum key string.

2. **`ui/theme/AccentColors.kt`** (new) — accent helpers: the quick-pick swatch list (`Color`s), plus pure functions `onColorFor(bg: Color): Color` (luminance → black/white) and `deriveContainer(accent, isDark)` / `deriveOnContainer(...)`. No enum needed since the value is a free color; swatches are just suggested values.

3. **`ui/theme/Theme.kt`** — `MyApplicationTheme` collects `AppPrefs.accentColor` (Int) and applies `.copy(primary=accent, onPrimary=onColorFor(accent), primaryContainer=…, onPrimaryContainer=…)` to the chosen `ColorScheme` before passing to `MaterialTheme`. (Dynamic-color branch left as-is.)

4. **`SettingsScreen.kt`**:
   - **Alarm tab (index 3):** below the existing fade-in card, add a "Default snooze length" card (chip row) and a "Default alarm tone" card (selectable list of the 6 built-ins, reusing the chip/`CurveChip`-style component). Wire to the new `AppPrefs` flows.
   - **Appearance tab (index 1):** add a "Week starts on" card (Mon/Sat/Sun chips) and an "Accent color" card — quick-pick swatch row + a "Custom…" entry opening an HSV color picker (hue bar + sat/value box + hex field, built with Compose `Canvas`/`pointerInput`, no new dependency). Wire to the new flows. Update the existing explanatory caption to mention week-start/accent apply app-wide.

   Also add a small **`ColorPicker` composable** (in `SettingsScreen.kt` or a new `ui/theme/ColorPickerDialog.kt`) implementing the HSV picker + hex parsing/formatting.

5. **`AlarmsScreen.kt`**:
   - `AlarmEditDialog`: initialize `currentTone` from `AppPrefs.defaultAlarmTone` for new alarms (`existing?.customToneName ?: AppPrefs.defaultAlarmTone.value`).
   - Reorder both day UIs by `AppPrefs.weekStartDay` — the `AlarmCard` mini chips ([:314](app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmsScreen.kt#L314)) and the editor `dayChipsLabel` ([:721](app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmsScreen.kt#L721)), using a shared ordered-days helper. Day *numbers* (1..7) keep their meaning; only display order changes.

6. **`MusicSchedulerScreen.kt`** — same week-start reordering for its two day UIs ([:229](app/src/main/java/in/sreerajp/chronotune_smart_clock/MusicSchedulerScreen.kt#L229), [:733](app/src/main/java/in/sreerajp/chronotune_smart_clock/MusicSchedulerScreen.kt#L733)).

7. **`ui/ClockViewModel.kt`** — `addAlarm` sets `snoozeMinutes = AppPrefs.getDefaultSnoozeMinutes(context)` when constructing the new `Alarm` (or accept it as a parameter passed from the dialog; ViewModel-side cold read is simpler). Existing alarms are untouched.

8. **Legibility swap (accent on-color)** — across the ~17 hardcoded `Color.White`-on-`primary` sites, replace with `MaterialTheme.colorScheme.onPrimary`. Files involved: `AlarmsScreen.kt`, `MusicSchedulerScreen.kt`, `TimerScreen.kt`, `StopwatchScreen.kt`, `ToneShared.kt`, and `AlarmRingingOverlay.kt` (only where the white sits on the accent — each site audited; non-accent whites left as-is).

## Notes / scope boundaries

- **Existing alarms are not retroactively changed** — snooze/tone defaults only apply to alarms created after the setting is changed. This matches "default" semantics.
- **Day numbering is unchanged** in the DB (`daysOfWeek` stays "1=Mon…7=Sun"); week-start only affects display order, so no migration and no scheduler impact.
- **Accent override is preset-based**; no Room/DB changes anywhere in this plan.
- A change log will be written to `change_log/` after implementation.

## Resolved decisions

- **Default tone:** built-in melodies only — no custom file/ringtone as the global default. ✔
- **Accent color:** full custom HSV + hex picker (any color), with auto-contrast `onPrimary` and the ~17 hardcoded white-on-accent sites swapped to `onPrimary`. ✔
