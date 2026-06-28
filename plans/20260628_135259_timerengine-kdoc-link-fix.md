# TimerEngine.kt — Unresolved KDoc Link Fix

**Status:** completed

## Issue

[TimerEngine.kt:83](app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/TimerEngine.kt#L83)
reports "Cannot resolve symbol 'totalDurationMs'". The line is the KDoc for `addMinute`:

```
/** Adds one minute. Stretches [totalDurationMs] too so the progress ring stays sensible. */
```

The `[totalDurationMs]` KDoc link resolves against the symbols in scope at the declaration, where
`totalDurationMs` is not visible — it is a property of `TimerItem`
([Models.kt:94](app/src/main/java/in/sreerajp/chronotune_smart_clock/data/Models.kt#L94)), not of
`TimerEngine`. The actual code (`timer.totalDurationMs`) is fine; only the doc link is broken.

## Files to change

- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/TimerEngine.kt`

## Plan for the fix

Qualify the KDoc link: `[totalDurationMs]` → `[TimerItem.totalDurationMs]`.

## Verification

Re-read the line to confirm the link is qualified.
