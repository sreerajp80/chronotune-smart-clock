# StopwatchPrefs.kt — Lint/IDE Warning Fixes

**Status:** completed

## Issue
IntelliJ/Android Studio reports 4 problems in
[StopwatchPrefs.kt](app/src/main/java/in/sreerajp/chronotune_smart_clock/StopwatchPrefs.kt):

1. `:12` — **Cannot resolve symbol 'ClockViewModel'** — the KDoc link `[ClockViewModel]`
   can't resolve because the class lives in the `...ui` package and isn't in scope.
2. `:61` — **Function "isActive" is never used** — `fun isActive()` has no callers anywhere
   in the codebase (verified via grep).
3. `:109` — **Use the KTX extension function `SharedPreferences.edit` instead** — `save()`
   uses the verbose `edit()...apply()` builder chain instead of the `androidx.core.content.edit`
   KTX block.

(The 4th "problem" badge counts the file-level total; these are the 3 distinct warnings.)

## Files to change
- [StopwatchPrefs.kt](app/src/main/java/in/sreerajp/chronotune_smart_clock/StopwatchPrefs.kt)

## Plan for the fix
1. **KDoc link (line 12):** change `[ClockViewModel]` to the package-qualified KDoc link
   `[ui.ClockViewModel]` so it resolves without adding an otherwise-unused import.
2. **Unused function (line 61):** remove `fun isActive()`. It is dead code (no callers).
3. **KTX `edit` (line 109):** add `import androidx.core.content.edit` and rewrite `save()` to
   use the `prefs(context).edit { ... }` block with `putString`/`putLong` calls inside.

No behavior change — these are warning-only cleanups.
