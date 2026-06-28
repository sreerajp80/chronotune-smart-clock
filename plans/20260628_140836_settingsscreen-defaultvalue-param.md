# SettingsScreen.kt — Remove redundant `defaultValue` parameter

**Status:** completed

## Issue
[SettingsScreen.kt:643](app/src/main/java/in/sreerajp/chronotune_smart_clock/SettingsScreen.kt#L643)
warns: **Value of parameter 'defaultValue' is always 'WidgetPrefs.DEFAULT_ALPHA'**.

The private `@Composable ChunkyWidgetOpacityRow` takes a `defaultValue: Float` parameter, but
both call sites (Digital widget @ line 604, Analog widget @ line 622) pass the same constant
`WidgetPrefs.DEFAULT_ALPHA`. The parameter adds no value.

## Files to change
- [SettingsScreen.kt](app/src/main/java/in/sreerajp/chronotune_smart_clock/SettingsScreen.kt)

## Plan for the fix
1. Remove the `defaultValue: Float` parameter from `ChunkyWidgetOpacityRow`.
2. Inside the function, replace the reference to `defaultValue` (in the `isAtDefault`
   computation) with `WidgetPrefs.DEFAULT_ALPHA`.
3. Remove the `defaultValue = WidgetPrefs.DEFAULT_ALPHA` argument from both call sites
   (Digital and Analog widget rows).

No behavior change — warning-only cleanup.
