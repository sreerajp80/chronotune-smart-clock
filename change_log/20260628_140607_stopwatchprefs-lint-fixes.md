# Change Log — StopwatchPrefs.kt Lint/IDE Warning Fixes

Implements plan: [20260628_140607_stopwatchprefs-lint-fixes.md](../plans/20260628_140607_stopwatchprefs-lint-fixes.md)

## Changes
File: [StopwatchPrefs.kt](../app/src/main/java/in/sreerajp/chronotune_smart_clock/StopwatchPrefs.kt)

1. **KDoc unresolved symbol** — changed the KDoc link `[ClockViewModel]` to the
   package-qualified `[ui.ClockViewModel]` so it resolves without an unused import.
2. **Unused function** — removed `fun isActive()` (dead code; no callers in the codebase).
3. **KTX `edit`** — added `import androidx.core.content.edit` and rewrote `save()` to use
   the `prefs(context).edit { ... }` KTX block instead of the `edit()...apply()` builder chain.

No behavior change — warning-only cleanups.
