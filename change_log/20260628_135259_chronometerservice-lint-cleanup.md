# Change Log — ChronometerService.kt Lint Cleanup

Implements plan: `plans/20260628_135259_chronometerservice-lint-cleanup.md`

## Summary

Resolved the 5 highlighted lint warnings in
[ChronometerService.kt](../app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ChronometerService.kt).
With `minSdk = 24`, the `SDK_INT` guards against `M`(23)/`N`(24) were dead code, and the
`Context.` prefix on the inherited `NOTIFICATION_SERVICE` constant was redundant.

## Changes

1. **`rebuild()`** — `getSystemService(Context.NOTIFICATION_SERVICE)` → `getSystemService(NOTIFICATION_SERVICE)`.
2. **`cancelStale()`** — removed dead `if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return`.
3. **`buildTimerNotification()`** — `setChronometerCountDown(true)` is now called unconditionally
   (was guarded by an always-true `SDK_INT >= N` check).
4. **`ensureChannel()`** — `getSystemService(Context.NOTIFICATION_SERVICE)` → `getSystemService(NOTIFICATION_SERVICE)`.
5. **`stopForegroundCompat()`** — collapsed to the single unconditional
   `stopForeground(STOP_FOREGROUND_REMOVE)`, dropping the unreachable pre-N `else` branch and
   its `@Suppress("DEPRECATION")`.

## Notes

- `import android.os.Build` retained — still used by the `O`(26) check in `ensureChannel()` and
  the `UPSIDE_DOWN_CAKE`(34) check in `startForegroundCompat()`.
- `import android.content.Context` retained — still referenced by the `Context` parameter type
  in `refresh()`.
- Scope was the 5 highlighted warnings only, as agreed in the plan.
