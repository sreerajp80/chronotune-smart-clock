# Change Log — TimerEngine.kt Unresolved KDoc Link Fix

Implements plan: `plans/20260628_135259_timerengine-kdoc-link-fix.md`

## Summary

Fixed the "Cannot resolve symbol 'totalDurationMs'" warning on
[TimerEngine.kt:83](../app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/TimerEngine.kt#L83).

## Change

In the `addMinute` KDoc, qualified the link to the property's owning type:
`[totalDurationMs]` → `[TimerItem.totalDurationMs]`. `TimerItem` is already imported and used
throughout the file, so no import change was needed.
