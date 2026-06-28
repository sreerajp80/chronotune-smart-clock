# Unified Alarm / Music-Schedule editors + "rings in X" save toast

Implements plan `plans/20260628_201317_schedule-editor-unification-toast.md`.

## What changed

### New: `data/ScheduleTiming.kt`
- `nextTriggerTime(hour, minute, repeatDays, pauseStartMillis, pauseEndMillis)` — the next-fire
  computation, moved verbatim out of `AlarmScheduler` so it is now the single source of truth.
- `formatTimeUntil(targetMillis, nowMillis)` — formats a future delta as "1 day 2 h", "8 h 30 m",
  "45 m", or "less than a minute" for the save toast.

### `ui/AlarmScheduler.kt`
- Removed the private `nextTriggerTime` / `toModelDay`; both `scheduleAlarm` and `scheduleMusic`
  now call the shared `data.nextTriggerTime`. No behavior change (identical algorithm).

### New: `ScheduleEditSheet.kt` (shared editor kit)
- `ScheduleEditSheet(...)` — the themed `ModalBottomSheet` + sliding two-pane scaffold (main editor
  ↔ tone picker), with `mainPane`/`tonePane` composable slots, an `overlay` slot (for the pause
  date picker), and an `onBackFromTones` hook to stop tone preview.
- `TonePickerPane(...)` — generalized tone picker: single- or multi-select via
  `isSelected`/`onToggleSelect`, optional Ringtones tab (`showRingtones`), search, empty/add-file
  states, and preview.
- Shared themed primitives moved here from `AlarmsScreen` and now `internal`: `TimeDigitBox`,
  `AmPmPill`, `DayCircleChip`, plus new `SheetHeader`, `SheetTimeRow`, `SheetSectionLabel`,
  `RepeatDaysRow`, `SheetVolumeSlider`, `SheetFooter`.

### `AlarmsScreen.kt`
- `AlarmEditDialog` rebuilt on `ScheduleEditSheet` + `TonePickerPane` (single-select, ringtones on).
  Behavior unchanged; the inline `ModalBottomSheet`/two-pane/tone-picker code and the three private
  primitives were removed in favor of the shared kit.
- `AlarmsScreen` now shows a Toast on save: "Alarm will ring in <X>" for new alarms, and for edits
  when the alarm is enabled (computed via `nextTriggerTime` + `formatTimeUntil`).

### `MusicSchedulerScreen.kt`
- `MusicScheduleEditDialog` rebuilt on the shared `ScheduleEditSheet`: dropped the hardcoded
  `MusicV1Palette` and all `MusicV1*` primitives (~290 lines) in favor of the theme-aware shared
  kit, so it now matches the alarm editor and honors theme/accent.
- Added `existing: MusicSchedule?` (pre-fills the editor) and the **edit path**: `MusicScheduleCard`
  gained `onEdit` and is now clickable; `MusicSchedulerScreen` tracks `editingSchedule` and wires it
  to the existing `ClockViewModel.updateMusicSchedule`.
- Melodies + files moved into the shared multi-select `TonePickerPane` (no ringtones); duration and
  volume remain in the main pane. Multi-file picking and the `track`/`uri` save encoding are
  preserved. Tone preview is now available for schedules too.
- Save Toast: "Music will play in <X>" for new schedules, and for edits when enabled.

## Notes
- Toast is shown only when the saved item is enabled; the editor never toggles `isEnabled`.
- Music passes the default `0L` pause args to `nextTriggerTime` (schedules have no pause window),
  matching the prior `AlarmScheduler.scheduleMusic` behavior.
- No data-model or DB schema changes.

## Verification
- `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL (only a pre-existing unrelated
  `Icons.Filled.QueueMusic` deprecation warning in `SettingsScreen.kt`).
- Manual checks recommended: editing an existing alarm and an existing schedule (new capability),
  pre-fill correctness, the save countdown toast for both, and rendering in light/dark + a custom
  accent.
