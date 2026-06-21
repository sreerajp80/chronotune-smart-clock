# Change Log — Configure Alarm design polish

**Date:** 2026-06-17
**Implements plan:** `plans/20260617_090007_alarm-default-weekdays.md`

Follow-up to the Configure Alarm redesign, bringing the sheet to an exact match of the
design file (sole intentional deviation: time is entered via keyboard).

## Files changed
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/MainActivity.kt`

## Changes
1. **Removed bottom blank space — sheet now hugs content.**
   - `BoxWithConstraints`: dropped `.fillMaxHeight(0.9f)` (now `.fillMaxWidth()` only), so the
     sheet wraps the main editor pane's natural height.
   - Main editor pane `Column`: `.fillMaxSize()` → `.fillMaxWidth()` (wraps content; still
     scrolls if it ever exceeds the sheet's max height).
   - Tone-picker pane `Column`: `.fillMaxSize()` → `.matchParentSize()`, so it matches the
     main pane's height without contributing to it. Switching panes no longer resizes the
     sheet, and there is no trailing empty space below "Save Alarm".

2. **Repeat-on-Days defaults to Mon–Fri for new alarms.**
   - `selectedDays` is now seeded with `[1,2,3,4,5]` when `existing == null`; editing an
     existing alarm still loads that alarm's saved days. Chips render filled accent out of
     the box, matching the design.

3. **Thin volume slider (matches design file).**
   - Replaced the default thick Material3 `Slider` track/thumb with custom slots: a 4dp
     rounded track (accent active / muted inactive) and a 16dp circular thumb filled with the
     sheet color and a 2.5dp accent ring. Value range/behavior unchanged.

## Not changed
- Save logic, scheduler, data model, tone picker behavior, and the 12h/24h handling are
  unchanged. Volume default remains 0.8 (slider styling only was requested).

## Validation
- `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL.
