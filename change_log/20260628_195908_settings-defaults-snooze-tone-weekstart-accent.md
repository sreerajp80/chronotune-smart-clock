# Change Log — Settings additions: default snooze, default tone, week-start day, accent color

**Implements:** [plans/20260628_194023_settings-defaults-snooze-tone-weekstart-accent.md](../plans/20260628_194023_settings-defaults-snooze-tone-weekstart-accent.md)
**Date:** 2026-06-28

## Summary

Added four user settings:

- **Alarm tab** — Default snooze length, Default alarm tone (built-in melodies only).
- **Appearance tab** — Week-start day, Theme accent color (full custom HSV + hex picker with auto-contrast).

Build verified: `compileDebugKotlin` → BUILD SUCCESSFUL.

## Changes by file

### `AppPrefs.kt`
- New prefs + `StateFlow`s + setters: `defaultSnoozeMinutes` (default 5), `defaultAlarmTone` (default "Morning Breeze"), `weekStartDay` (default 1=Mon), `accentColor` (ARGB Int; `0` = no override).
- Constants `SNOOZE_CHOICES = [5,10,15,20,30]`, `DEFAULT_TONE_NAME`, `WEEK_START_DEFAULT`, `ACCENT_DEFAULT`.
- Cold-read getter `getDefaultSnoozeMinutes(context)` and helper `orderedWeekDays(start)` (rotates day-numbers 1..7 to begin at the chosen start — display order only).
- All four loaded in `init()`.

### `ui/theme/AccentColors.kt` (new)
- `AccentSwatches` quick-pick list.
- Pure helpers: `onColorFor(bg)` (luminance → black/white), `deriveContainer(accent, isDark)`, `deriveOnContainer(accent, isDark)`.

### `ui/theme/ColorPickerDialog.kt` (new)
- Self-contained HSV color picker: saturation/value box + hue bar (Compose `Canvas`/`pointerInput`) + hex field with parse/format. No new dependency.

### `ui/theme/Theme.kt`
- `MyApplicationTheme` collects `AppPrefs.accentColor`. When set (non-zero), copies the active `ColorScheme` with `primary` = chosen color, `onPrimary` = contrast-derived, and derived `primaryContainer`/`onPrimaryContainer`. When `0`, the built-in palette is used unchanged.

### `SettingsScreen.kt`
- Alarm tab (index 3): `DefaultSnoozeCard` (chip row) + `DefaultToneCard` (selectable list of 6 built-ins) + explanatory caption noting defaults apply to new alarms only.
- Appearance tab (index 1): `AccentColorCard` (swatch row + "Custom…" picker + "Reset") and `WeekStartCard` (Mon/Sun/Sat chips).
- Added `SelectableToneRow` and `SwatchDot` helpers; imports for the theme helpers, `toArgb`, `clip`.

### `AlarmsScreen.kt`
- New alarms preselect the tone from `AppPrefs.defaultAlarmTone`.
- Day chips in `AlarmCard` and the editor reordered via `AppPrefs.orderedWeekDays(weekStart)`; added `dayLetter`/`dayShort` helpers.
- Swapped white-on-accent content to `onPrimary` (FAB, Save/Update button, file-picker button, tone-picker save button, `DayCircleChip` selected label).

### `MusicSchedulerScreen.kt`
- Day chips in the schedule card and editor reordered by week-start.
- FAB content color swapped to `onPrimary`. The music editor's bespoke fixed palette (gradient buttons, `onPrimarySoft`) left unchanged — it is not the theme accent.

### `ui/ClockViewModel.kt`
- `addAlarm` sets `snoozeMinutes = AppPrefs.getDefaultSnoozeMinutes(context)` for newly created alarms.

### White → onPrimary swaps (accent legibility)
- `TimerScreen.kt` (4 buttons on primary), `StopwatchScreen.kt` (toggle: `onError`/`onPrimary` to match its error/primary color), `ToneShared.kt` (tab chip, Eq bars, radio check), `AlarmRingingOverlay.kt` (icon + DISMISS button — only the MUSIC branch that sits on `primary`; the red ALARM branch and white-on-black overlay text left as-is).

## Scope notes / known limitations

- **Existing alarms are not changed** — snooze/tone defaults only seed alarms created afterward.
- **Day numbering in the DB is unchanged** (1=Mon…7=Sun); week-start only affects display order, so no migration and no scheduler impact.
- The alarm ringing overlay's **"SNOOZE (5 MIN)"** label is still hardcoded — it does not yet reflect a per-alarm snooze length (the `ActiveAlarm` state does not carry it). Left out of scope.
