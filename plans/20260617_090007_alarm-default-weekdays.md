# Configure Alarm — weekday default + remove bottom blank space

**Date:** 2026-06-17

## Issues
1. **Bottom blank space.** The bottom sheet is forced to 90% of screen height
   (`BoxWithConstraints(... .fillMaxHeight(0.9f))` + main pane `.fillMaxSize()`), so a large
   empty gap appears below the "Save Alarm" button. The new design sizes the sheet to its
   content (no gap).
2. **Repeat-on-Days default.** A new alarm starts with no days selected, so the chips render
   empty. The design pre-selects **Mon–Fri** (filled) for a new alarm.

## Files to change
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/MainActivity.kt`

## Fix 1 — content-height sheet (kills blank space, keeps stable two-pane slide)
Make the sheet wrap the **main editor pane's** natural height, and have the tone-picker pane
match that same height (so switching panes doesn't resize/jump):

- `BoxWithConstraints`: drop `.fillMaxHeight(0.9f)` → keep only `.fillMaxWidth()` (wraps content height).
- Main pane `Column`: change `.fillMaxSize()` → `.fillMaxWidth()` so it wraps its content height
  (the `verticalScroll` still lets it scroll if it ever exceeds the sheet's max height).
- Tone-picker pane `Column`: change `.fillMaxSize()` → `.matchParentSize()` (BoxScope) so it
  takes the main pane's measured height without contributing to it. Its inner `LazyColumn`
  keeps `weight(1f)` and scrolls within that height — matching the design, where the tone
  picker fills the same area as the form.

Net effect: sheet height = form height (no blank space); both panes share one stable height.

## Fix 3 — thin volume slider (match design)
Replace the default thick Material3 `Slider` track/thumb with the design's thin style:
a 4dp rounded track (accent active / muted inactive) and a small (~16dp) circular thumb
filled with the sheet color and a 2.5dp accent ring. Implemented via the `Slider`
`track`/`thumb` slot composables; value/behavior unchanged.

## Fix 2 — default new alarms to Mon–Fri
In `AlarmEditDialog`, seed `selectedDays` with Mon–Fri when creating a new alarm; editing an
existing alarm is unchanged:

```kotlin
val selectedDays = remember {
    mutableStateListOf<Int>().apply {
        if (existing != null) existing.getRepeatDaysList().let { addAll(it) }
        else addAll(listOf(1, 2, 3, 4, 5)) // Mon–Fri default for new alarms
    }
}
```

## Scope / impact
- No changes to save logic, scheduler, data model, or the tone picker behavior.
- Fix 1 is layout-only. Fix 2 changes only the default day set for newly created alarms.

## Validation
- `./gradlew :app:compileDebugKotlin`.
- New alarm: sheet hugs content (no bottom gap); Mon–Fri filled, Sat/Sun outlined.
- Open the tone picker: it fills the same area and its list scrolls; returning to the form
  keeps the same sheet height (no jump).

## Change log
- After implementation, write a change log referencing this plan.
