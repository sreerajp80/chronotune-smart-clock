# Change Log — Configure Alarm redesign

**Date:** 2026-06-17
**Implements plan:** `plans/20260617_075931_configure-alarm-redesign.md`

## Summary
Reworked the Configure / Edit Alarm editor to match the new design (`Chronotune Clock.zip` → `Configure Alarm.dc.html`). The editor is now a bottom sheet with a dedicated, sliding tone-picker sub-screen (Built-in / Ringtones / From File).

## Files changed
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/MainActivity.kt`

### Imports added
- `androidx.activity.compose.BackHandler`
- `androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight`
- `androidx.compose.ui.graphics.graphicsLayer`
- `kotlin.math.roundToInt`
- `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.withContext`

### Composable changes
- **`TimeDigitBox`** — now width-flexible (accepts a `Modifier`, used with `weight(1f)`); restyled to 60dp height, 14dp radius, 34sp bold digits.
- **Removed `OutlinedPillChip`** (no longer used) and replaced it with new helpers:
  - `DayCircleChip` — solid-filled accent circular day selector.
  - `RowScope.ToneTabChip` — segmented tab chip for the tone picker.
  - `EqBars` — animated equalizer bars shown on a previewing tone row.
  - `TonePickerRow` — list row with play/preview lead, name + sub, radio check.
  - `ToneItem` data class and `loadSystemRingtones(context)` helper (enumerates `RingtoneManager` TYPE_ALARM + TYPE_RINGTONE off the main thread).
- **`AlarmEditDialog`** — rewritten:
  - Container converted from `Dialog`/`Card` to `ModalBottomSheet` (drag handle, rounded top, scrim).
  - Two horizontally-sliding panes (`graphicsLayer` translation driven by `animateFloatAsState`): main editor ↔ tone picker.
  - Main pane: header with circular close (✕) button, restyled time boxes + AM/PM stack, label field, solid-filled day chips, a tappable "Alarm Audio Tone" summary row (name + chevron), volume row with live percentage, vibration toggle, Cancel + Save/Update footer.
  - Tone picker pane: back button + "Alarm Tone" title, search field, Built-in/Ringtones/From File tabs, scrollable list with per-row preview (one at a time, 10s) and radio selection, From-File empty state ("Browse device files") and "Add another file" action, bottom "Selected · <tone>" + Done bar.
  - `BackHandler` returns from the tone picker to the main editor instead of dismissing.

## Behavior preserved
- 12h/24h handling, hour/minute input sanitizers, and the 12h→24h save conversion are unchanged.
- `onSave` contract unchanged — selected tone name/uri flow through the existing path, so **no changes** were needed in `Models.kt`, `ClockViewModel.kt`, `AlarmScheduler.kt`, `Receivers.kt`, or `AudioEngine.kt`.
- System ringtone selections persist as `customToneName` (title) + `customToneUri` (content URI) and play through the existing `AudioEngine.playAudio` URI branch.
- Preview engine lifecycle preserved (stops on dispose, on dismiss, on leaving the picker, and when switching rows/tabs).
- When editing an existing alarm, the picker opens on the tab matching the current selection and shows it checked.

## Validation
- `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL.
- Manual device verification of preview playback, ringtone enumeration, and ring-on-schedule recommended.
