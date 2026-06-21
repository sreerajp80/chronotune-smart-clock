# Configure Alarm — Redesign Plan

**Date:** 2026-06-17
**Design source:** `Chronotune Clock.zip` → `Configure Alarm.dc.html` + reference PNGs
**Target screen:** `AlarmEditDialog` (Configure / Edit Alarm) in `MainActivity.kt`

## Approved scope decisions
1. **Tone picker tabs:** All three — **Built-in / Ringtones / From File** (Ringtones backed by Android `RingtoneManager`).
2. **Container:** Convert editor to a **bottom sheet** (drag handle, rounded top corners, scrim) — matching the design.
3. **Time input:** **Keep keyboard text entry** (restyle boxes only; no tap-to-increment).

## What the design changes

The new design keeps the same logical fields (time, AM/PM, label, repeat days, tone, volume, vibration) but restructures the UI:

- Editor is a **slide-up bottom sheet** instead of a centered `Dialog`.
- Header gains a circular **close (✕)** button next to the title.
- **Time boxes** are wider/flatter (rounded 14dp, 36sp digits) with `:` separator and a vertical AM/PM stack on the right.
- **Repeat day chips** become **solid-filled accent circles** (white text when selected) instead of soft-tinted outlined chips.
- **Alarm Audio Tone** is no longer inline chips + a file button. It becomes a **single tappable row** ("Alarm Audio Tone" + selected tone name + chevron) that opens a **second screen** (slides in over the sheet).
- **Tone picker sub-screen** contains:
  - Back button + "Alarm Tone" title
  - Search field ("Search tones") filtering the current tab's list by name
  - Three tabs: **Built-in / Ringtones / From File**
  - A scrollable list; each row = circular **play/preview** button (shows animated EQ bars while previewing), tone name + sub-line, and a **radio check** circle on the right
  - **From File** tab: empty-state ("No files added yet" + "Browse device files") or a list of picked files + an "Add another file" dashed button
  - Bottom bar: "Selected · `<tone>`" + **Done** button
- **Volume** row adds a **percentage label** on the right; slider styling matches accent track.
- **Vibration alerts** stays a toggle.
- Footer: **Cancel** text + gradient **Save Alarm / Update Alarm** button.

## Files to change

1. **`app/src/main/java/in/sreerajp/chronotune_smart_clock/MainActivity.kt`** — the only file requiring substantive change.
   - Rewrite `AlarmEditDialog` (lines ~1270–1665) to:
     - Use a bottom-sheet container. Implementation approach: keep the `Dialog` host but render the content as a bottom-anchored sheet (Surface aligned to bottom, rounded top corners, drag handle, scrim via `Dialog` dim) with a slide-in animation — OR use `ModalBottomSheet` from Material3 if available in the project's Compose BOM (will verify before coding; fall back to the Dialog-anchored sheet if not). The sheet hosts a two-pane animated content (`AnimatedContent`/offset) for **main** ↔ **tone picker** screens, matching the design's horizontal slide.
     - Main pane: restyled time boxes (reusing/adjusting `TimeDigitBox`), AM/PM stack (`AmPmPill`), label field, filled day chips, the new "Alarm Audio Tone" summary row, volume row with `%` label, vibration toggle, Cancel/Save footer, and a header ✕ button.
     - Tone picker pane: search field, 3 tabs, preview rows with play/EQ + radio check, From-File empty/list states, Done bar. Reuses `AudioEngine` preview (`playAudio`) already wired in the current dialog; only one row previews at a time.
   - Add new private composables (kept in this file alongside the existing alarm helpers):
     - `FilledDayChip` (solid accent circle day selector) — or extend `OutlinedPillChip` with a `filled` variant.
     - `ToneSummaryRow` (label + selected tone + chevron).
     - `TonePickerScreen` (the sub-screen) + `TonePickerRow` + `EqBars` (animated equalizer) + `ToneTab`.
   - Day chip styling: selected = filled `primary`, white text; unselected = transparent with outline + muted text (per design).
   - Ringtones: enumerate system ringtones via `RingtoneManager` (TYPE_ALARM + TYPE_RINGTONE) inside a `remember`/`LaunchedEffect` (cursor read off the main thread). Each entry → `{ title, uri }`. Selecting one sets `currentTone = title`, `customToneUri = uri`. Preview uses existing `playAudio(toneName, uriString=uri, ...)`.
   - Built-in tab: existing `tonesList` (the 6 synth tones); selecting sets `currentTone = name`, `customToneUri = ""`.
   - From File tab: reuse existing `OpenDocument` launcher + `takePersistableUriPermission`; selecting/adding a file sets `currentTone = fileName`, `customToneUri = uri`.
   - `onSave` contract is unchanged: `(hour, minute, label, repeatDays, tone, uri, volume, isVibrate)`. The selected tone's name+uri flow through the existing path, so **no changes** are needed in `ClockViewModel`, `AlarmScheduler`, `Receivers`, or `Models`.

2. **No changes** to: `Models.kt`, `ClockViewModel.kt`, `AlarmScheduler.kt`, `Receivers.kt`, `AudioEngine.kt` (URIs — including system ringtone URIs — already play through `playAudio`'s `uriString` branch; `customToneName`/`customToneUri` already persist tone name + URI).

## Behavior parity / correctness notes
- 12h/24h handling, hour/minute sanitizers, and the 12h→24h save conversion are preserved exactly from the current implementation.
- Preview engine lifecycle (`DisposableEffect { previewEngine.stop() }`, stop on dismiss) is preserved; tone picker preview stops when leaving the picker or switching rows.
- Editing an existing alarm: if its `customToneUri` is non-blank, the picker opens with the matching row checked under the appropriate tab (From File if it was a document; Ringtones if it matches a system ringtone uri; else Built-in by name).
- Selected-day order/serialization (`days.sorted().joinToString(",")`) unchanged.

## Risks / things to verify during implementation
- Confirm Material3 `ModalBottomSheet` availability in the project's Compose version; otherwise use the Dialog-anchored sheet fallback (no new dependency either way).
- `RingtoneManager` cursor enumeration must run off the UI thread and handle empty/permission-less results gracefully (show an empty list, never crash).
- Keep the sheet height bounded and scrollable (design caps at `calc(100% - 40px)`); ensure the keyboard (label/search fields) doesn't clip the sheet.

## Validation
- Build: `./gradlew :app:assembleDebug`.
- Manual: create + edit an alarm; switch tabs; preview a built-in tone, a ringtone, and a picked file; confirm the saved alarm rings with the chosen tone (existing scheduler path).

## Change log
- After implementation, write `change_log/<ts>_configure-alarm-redesign.md` referencing this plan.
