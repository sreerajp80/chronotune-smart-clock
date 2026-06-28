# Change log — Split MainActivity.kt into per-screen files

Implements plan: `plans/20260628_130510_split-mainactivity-screens.md`.

## What changed

`MainActivity.kt` (~5,069 lines) was split into per-screen files. All new files live in the
same package/directory (`in.sreerajp.chronotune_smart_clock`), so in-package references
resolve without import changes. The refactor is a **pure code move** — no logic was changed.

### `MainActivity.kt` (now 193 lines)
Reduced to package, imports, and the `MainActivity` activity class only.

### New files
| File | Contents |
|------|----------|
| `ClockAppScreen.kt` | `ClockAppScreen` (bottom-nav host + ringing-overlay fallback) |
| `WorldClockScreen.kt` | `WorldClockScreen`, `WorldClockItem`, `LocationSearchDialog`, `getZoneTimeFormatted`, `getZoneOffsetFormatted` |
| `AlarmsScreen.kt` | `AlarmsScreen`, `AlarmCard`, `TimeDigitBox`, `AmPmPill`, `DayCircleChip`, `AlarmEditDialog`, `formatPauseRange` |
| `StopwatchScreen.kt` | `StopwatchScreen` |
| `TimerScreen.kt` | `TimerScreen`, `TimerRow`, `TimerPresetChip`, `AddTimerSheet`, `TimerPickerColumn`, `formatDurationLabel`, `formatTimerClock`, `queryDisplayName` |
| `MusicSchedulerScreen.kt` | `MusicSchedulerScreen`, `MusicScheduleCard`, `MusicScheduleEditDialog`, `rememberMusicV1Palette`, `MusicV1Palette`, `MusicV1*` helpers |
| `SettingsScreen.kt` | `SettingsScreen`, `InfoCard`, `PermissionItem`, `ChunkyIconTile`, `ChunkyAppearanceToggle`, `WidgetAppearanceCard`, `ChunkyWidgetOpacityRow`, `ChunkyOpacitySlider`, `loadConfig`, `AppConfig` |
| `AlarmRingingOverlay.kt` | `AlarmRingingOverlay` |
| `ToneShared.kt` | Shared tone-picker UI + helpers used by both Alarms and Music/Timer |

### Visibility changes
Six symbols used across more than one screen were promoted `private` → `internal` and moved
to `ToneShared.kt`: `getFileNameFromUri`, `loadSystemRingtones`, `ToneItem`, `ToneTabChip`,
`EqBars`, `TonePickerRow`. All other symbols kept their original visibility.

## Notes
- Each new file received the full original import block. Unused imports surface only as
  warnings, not errors; they can be tidied later if desired.
- A few section-banner comments may now sit at the tail of the preceding file (cosmetic only).

## Verification
`./gradlew :app:compileDebugKotlin` → **BUILD SUCCESSFUL**.
