# Cleanup: Remove unused imports across Kotlin sources

**Status:** completed

## Issue

Android Studio / the Kotlin IDE inspection flags a large number of **"Unused import
directive"** warnings across the app's Kotlin sources (e.g. `AlarmRingingOverlay.kt` alone
shows 65). These are warnings only (the code compiles), but they add noise and clutter the
problems list.

A read-only detection pass (simple-name reference heuristic, which reproduced the IDE's exact
count of 65 for `AlarmRingingOverlay.kt`) found **584 unused imports across 18 files**.

## Scope (files to change)

Only `import` lines are removed — no logic, no other lines touched. Per-file counts:

| File | Unused |
|------|-------:|
| `app/.../ClockAppScreen.kt` | 68 |
| `app/.../AlarmRingingOverlay.kt` | 65 |
| `app/.../ToneShared.kt` | 64 |
| `app/.../StopwatchScreen.kt` | 62 |
| `app/.../MainActivity.kt` | 55 |
| `app/.../AlarmsScreen.kt` | 52 |
| `app/.../WorldClockScreen.kt` | 52 |
| `app/.../MusicSchedulerScreen.kt` | 50 |
| `app/.../TimerScreen.kt` | 48 |
| `app/.../SettingsScreen.kt` | 45 |
| `app/.../ui/theme/Button3D.kt` | 7 |
| `app/.../ui/ClockViewModel.kt` | 6 |
| `app/.../ui/AlarmActivity.kt` | 3 |
| `app/.../ui/ChronometerService.kt` | 2 |
| `app/.../ui/Receivers.kt` | 2 |
| `app/.../AppPrefs.kt` | 1 |
| `app/.../StopwatchPrefs.kt` | 1 |
| `app/.../ui/theme/Type.kt` | 1 |

(Paths under `app/src/main/java/in/sreerajp/chronotune_smart_clock/`.)

## Plan for the fix

1. **Detection** (read-only, already prototyped in scratchpad):
   - For each `.kt` file, parse `import` lines.
   - **Wildcard imports** (`...*`) are always kept (can't be flagged individually).
   - For aliased imports (`import a.b.C as D`) the referenced name is the alias `D`;
     otherwise it's the final path segment.
   - An import is "unused" if its referenced simple name does not appear as a word token
     anywhere in the file body (outside the import block).

2. **Removal:** delete the flagged `import` lines in a single pass per file. No blank lines
   added; remaining imports keep their order.

3. **Verification (safety net):** run `gradlew compileDebugKotlin` (or `assembleDebug` if
   needed). The heuristic can theoretically over-flag an extension/operator import that's only
   used via `.call()`; if the compile reports any `unresolved reference`, the specific import(s)
   are **restored** and the compile re-run until clean. This guarantees no behavior change.

4. **Change log:** write `change_log/<ts>_cleanup-unused-imports.md` referencing this plan.

## Risk / non-goals

- **No logic changes.** Only import lines removed.
- Wildcard imports left as-is (not expanded, not removed).
- Import *ordering/grouping* is not reformatted — only removals.
- If `gradlew` cannot run in this environment, I will report that and fall back to the
  conservative heuristic (which counts dotted/qualified references as usage, eliminating
  practical false-positive removals) before committing the change.
