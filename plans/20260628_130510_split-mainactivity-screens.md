# Split MainActivity.kt into per-screen files

**Status:** completed

## Issue

`MainActivity.kt` is ~5,069 lines (the prompt says 4,556; it has since grown). It holds
the `Activity`, the navigation host, every screen composable, every screen-local helper,
and a few cross-screen helpers — all in one file. This is hard to navigate and maintain.
The goal is to split each screen into its own file.

## Approach

Keep **everything in the same package** `in.sreerajp.chronotune_smart_clock` (new files in
the same directory as `MainActivity.kt`). Same-package means public/`internal` members are
visible across files with **no import changes** for in-package references. Only `private`
top-level helpers that are used by more than one screen need to be promoted to `internal`
and moved to a shared file.

Each new file gets its own import block (a subset of the current imports plus anything that
was implicit from being in the same file).

### Cross-file (shared) helpers — promote `private` → `internal`, move to a shared file

Verified by grep of call sites:
- `getFileNameFromUri` — used by Alarms (1640) and Music (3784)
- `loadSystemRingtones` — used by Alarms (1617) and Timer (2900)
- `ToneItem` (data class), `ToneTabChip`, `EqBars`, `TonePickerRow` — used by Alarms and Music

These go into a new **`ToneShared.kt`** as `internal`.

### Screen-local helpers stay `private` and move with their screen

- `formatPauseRange`, `TimeDigitBox`, `AmPmPill`, `DayCircleChip` → Alarms (only callers there)
- `getZoneTimeFormatted`, `getZoneOffsetFormatted` → World Clock
- `formatDurationLabel`, `formatTimerClock`, `queryDisplayName`, `TimerPickerColumn` → Timer
- `loadConfig`, `AppConfig`, `InfoCard`, `PermissionItem`, `Chunky*`, `WidgetAppearanceCard` → Settings
- `rememberMusicV1Palette`, `MusicV1Palette`, `MusicV1*` → Music

## Files

### Modified
- `MainActivity.kt` — reduced to: package, imports needed by the `Activity`, and the
  `MainActivity` class only.

### New (all in `app/src/main/java/in/sreerajp/chronotune_smart_clock/`)
1. `ClockAppScreen.kt` — `ClockAppScreen` (bottom-nav host + ringing-overlay fallback).
2. `WorldClockScreen.kt` — `WorldClockScreen`, `WorldClockItem`, `LocationSearchDialog`,
   `getZoneTimeFormatted`, `getZoneOffsetFormatted`.
3. `AlarmsScreen.kt` — `AlarmsScreen`, `AlarmCard`, `TimeDigitBox`, `AmPmPill`,
   `DayCircleChip`, `AlarmEditDialog`, `formatPauseRange`.
4. `StopwatchScreen.kt` — `StopwatchScreen`.
5. `TimerScreen.kt` — `TimerScreen`, `TimerRow`, `TimerPresetChip`, `AddTimerSheet`,
   `TimerPickerColumn`, `formatDurationLabel`, `formatTimerClock`, `queryDisplayName`.
6. `MusicSchedulerScreen.kt` — `MusicSchedulerScreen`, `MusicScheduleCard`,
   `MusicScheduleEditDialog`, `rememberMusicV1Palette`, `MusicV1Palette`, `MusicV1*` helpers.
7. `SettingsScreen.kt` — `SettingsScreen`, `InfoCard`, `PermissionItem`, `ChunkyIconTile`,
   `ChunkyAppearanceToggle`, `WidgetAppearanceCard`, `ChunkyWidgetOpacityRow`,
   `ChunkyOpacitySlider`, `loadConfig`, `AppConfig`.
8. `AlarmRingingOverlay.kt` — `AlarmRingingOverlay`.
9. `ToneShared.kt` — `ToneItem`, `ToneTabChip`, `EqBars`, `TonePickerRow`,
   `getFileNameFromUri`, `loadSystemRingtones` (all `internal`).

## Risks / mitigation

- **Imports**: each file needs the right import subset. Mitigation: build with Gradle after
  the split and fix any unresolved references / unused-import warnings.
- **Visibility**: only the 6 shared symbols change visibility; everything else keeps its
  current visibility. No behavior change — pure code-move refactor.
- **No logic changes**: code is moved verbatim, not rewritten.

## Verification

Run `./gradlew :app:compileDebugKotlin` (or assembleDebug) and confirm it builds clean.
Then write the change log.
