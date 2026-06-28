# Unify Alarm / Music-Schedule editors + "rings in X" save toast

**Status:** completed

## The issue

Two problems with the per-item edit experience:

### 1. The two editors are inconsistent — and schedules can't be edited at all
- **Alarms** have a full edit path: tapping an [`AlarmCard`](../app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmsScreen.kt) reopens [`AlarmEditDialog`](../app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmsScreen.kt) pre-filled via its `existing: Alarm?` parameter.
- **Music schedules cannot be edited.** [`MusicScheduleCard`](../app/src/main/java/in/sreerajp/chronotune_smart_clock/MusicSchedulerScreen.kt) exposes only `onToggle`/`onDelete`, and [`MusicScheduleEditDialog`](../app/src/main/java/in/sreerajp/chronotune_smart_clock/MusicSchedulerScreen.kt) has no `existing` parameter — it is add-only. To change a schedule you must delete and recreate it. (`ClockViewModel.updateMusicSchedule` already exists and is unused by the UI.)
- The two dialogs diverge heavily in design:
  | | Alarm editor | Schedule editor |
  |---|---|---|
  | Container | `ModalBottomSheet`, sliding two-pane | `Dialog` card |
  | Theming | `MaterialTheme` color scheme (theme/accent-aware) | hardcoded `MusicV1Palette` |
  | Tone picker | dedicated searchable pane (Built-in / Ringtones / Files) + preview | inline chips + file list, no preview |
  | Title | "Configure / Edit Alarm" | always "Configure Music Schedule" |
  | Save button | `Button3D` | gradient `Box` |

### 2. No save feedback
Saving in either editor silently dismisses. There is no confirmation telling the user when the item will next fire.

The user has chosen **full unification** (one shared design) and a **"will ring / play in X" toast on save for both** editors.

## Design approach

The two editors genuinely differ in fields (alarms: vibrate + pause window, single tone; schedules: duration + multiple ambient melodies/files), so "unification" means **one shared design system and container**, not one identical form. We adopt the alarm editor's polished, theme-aware `ModalBottomSheet` + sliding tone-pane as the base and retrofit the music editor onto it, dropping `MusicV1Palette` entirely so both honor the app theme/accent.

Shared, reusable, theme-aware building blocks are extracted once; each editor composes them plus its own type-specific sections.

## Files to change

1. **`app/src/main/java/in/sreerajp/chronotune_smart_clock/data/ScheduleTiming.kt`** *(new)*
   - `fun nextTriggerTime(hour, minute, repeatDays, pauseStartMillis = 0L, pauseEndMillis = 0L): Calendar` — moved verbatim from `AlarmScheduler.nextTriggerTime` (plus its `isPaused`/`toModelDay` helpers) so it is the single source of truth.
   - `fun formatTimeUntil(targetMillis: Long, nowMillis: Long): String` — formats the delta as `"1 day 2 h"`, `"8 h 30 m"`, `"45 m"`, or `"less than a minute"`.

2. **`app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmScheduler.kt`**
   - Delete the private `nextTriggerTime` / `toModelDay`; call the shared `nextTriggerTime` from `data`. No behavior change (same algorithm).

3. **`app/src/main/java/in/sreerajp/chronotune_smart_clock/ScheduleEditSheet.kt`** *(new — shared editor kit)*
   Theme-aware primitives currently private to `AlarmsScreen`, generalized and shared:
   - `ScheduleEditSheet(...)` — the `ModalBottomSheet` + `BoxWithConstraints` two-pane scaffold (main pane ↔ tone pane slide), hosting common sections and caller-supplied type-specific sections via composable slots.
   - Common sections: header (title + close), time row (`TimeDigitBox` + AM/PM, 12/24h aware), label field, "Repeat on Days" row (week-start aware), volume slider, Cancel/Save footer.
   - `TonePickerPane(...)` — generalized from the alarm tone pane: `multiSelect: Boolean`, `showRingtones: Boolean`, optional preview. Single-select drives alarms; multi-select drives music (ambient melodies + multiple files, no ringtones).
   - The reusable `TimeDigitBox` / `AmPmPill` / `DayCircleChip` move here from `AlarmsScreen.kt`.

4. **`app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmsScreen.kt`**
   - Rebuild `AlarmEditDialog` on `ScheduleEditSheet` + shared `TonePickerPane` (single-select, ringtones on). Behavior unchanged; just sourced from the shared kit. Remove the now-moved private primitives.
   - In `onSave` (both add and edit paths): after persisting, if the alarm is enabled, compute `nextTriggerTime(...)` and `Toast` `"Alarm will ring in <X>"`.

5. **`app/src/main/java/in/sreerajp/chronotune_smart_clock/MusicSchedulerScreen.kt`**
   - Rebuild `MusicScheduleEditDialog` on `ScheduleEditSheet`: `ModalBottomSheet` + theme colors (remove `MusicV1Palette` and the `MusicV1*` primitives), add `existing: MusicSchedule? = null`, title "Configure / Edit Music Schedule", keep the duration slider and multi-track selection (via shared `TonePickerPane` multi-select + duration section).
   - Add `editingSchedule` state in `MusicSchedulerScreen`; wire the edit path to `viewModel.updateMusicSchedule(existing.copy(...))`.
   - `MusicScheduleCard`: add `onEdit` and make the card `clickable` (mirrors `AlarmCard`).
   - In `onSave` (add and edit): if enabled, compute next trigger and `Toast` `"Music will play in <X>"`.

## Notes / decisions
- Toast is shown only when the saved item is enabled (editor never toggles `isEnabled`; new items are enabled, edits preserve the existing flag).
- Music has no pause window; the shared `nextTriggerTime` is called with the default `0L` pause args for schedules — identical to today's `AlarmScheduler.scheduleMusic`.
- Multi-file picking for music (`OpenMultipleDocuments`) is preserved; the shared tone pane's "From File" tab gains multi-add when `multiSelect = true`.
- No data-model or DB schema changes; `updateMusicSchedule` already exists.

## Verification
- `./gradlew :app:compileDebugKotlin` succeeds.
- Manual: edit an existing alarm and an existing schedule (new capability); confirm pre-fill, save, and the countdown toast for both; confirm both editors render correctly in light/dark + a custom accent.
