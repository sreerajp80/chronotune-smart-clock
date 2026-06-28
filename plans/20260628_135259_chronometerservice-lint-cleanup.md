# ChronometerService.kt — Lint Cleanup

**Status:** completed

## Issue

IntelliJ/Android Lint reports 16 warnings in
[ChronometerService.kt](app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ChronometerService.kt),
5 of which are highlighted:

| Line | Warning |
|------|---------|
| 98  | Remove redundant qualifier name |
| 117 | Unnecessary; `SDK_INT` is never < 24 |
| 190 | Unnecessary; `SDK_INT` is always >= 24 |
| 232 | Remove redundant qualifier name |
| 258 | Unnecessary; `SDK_INT` is always >= 24 |

`minSdk = 24` (confirmed in [app/build.gradle.kts](app/build.gradle.kts#L17)), so every
`Build.VERSION.SDK_INT` guard against `M` (23) or `N` (24) is dead code. The redundant
qualifiers are `Context.NOTIFICATION_SERVICE`: `ChronometerService` extends `Context`, so the
inherited constant `NOTIFICATION_SERVICE` is in scope without the `Context.` prefix.

## Files to change

- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ChronometerService.kt`

## Plan for the fix

1. **Line 98** — `getSystemService(Context.NOTIFICATION_SERVICE)` → `getSystemService(NOTIFICATION_SERVICE)`.
2. **Line 117** — remove the dead early-return `if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return`
   from `cancelStale` (it can never trigger when minSdk is 24).
3. **Line 190** — `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) builder.setChronometerCountDown(true)`
   → unconditional `builder.setChronometerCountDown(true)`.
4. **Line 232** — `getSystemService(Context.NOTIFICATION_SERVICE)` → `getSystemService(NOTIFICATION_SERVICE)`.
5. **Lines 257–264** — collapse `stopForegroundCompat()` to the unconditional
   `stopForeground(STOP_FOREGROUND_REMOVE)`, dropping the unreachable pre-N `else` branch
   and its `@Suppress("DEPRECATION")`.

After edits, the `android.os.Build` import may become unused (no remaining `SDK_INT`/`Build`
references). I'll check and remove the `import android.os.Build` line if so. The
`import android.content.Context` import stays (still referenced by the `Context` parameter type
in `cancelStale`, `refresh`, etc.).

## Verification

- Re-read the file to confirm no remaining `Build.VERSION` references and no unused imports.
- Optionally compile via Gradle if requested.

## Notes

The IDE listed 16 problems but only 5 are the highlighted warnings above; this plan addresses
those 5. The remaining 11 are likely the same categories elsewhere in the file or info-level
hints — if you want a full sweep, say so and I'll expand the scope.
