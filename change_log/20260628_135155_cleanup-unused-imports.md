# Change log: Remove unused imports across Kotlin sources

Implements plan: `plans/20260628_133641_cleanup-unused-imports.md`.

## What changed

Removed **506 unused `import` directives** across **10 Kotlin files**. Only import lines
were touched — no logic, formatting, or other lines were changed. Wildcard imports (`...*`)
were left intact.

### Net imports removed per file

| File (`app/src/main/java/in/sreerajp/chronotune_smart_clock/`) | Removed |
|------|-------:|
| `ClockAppScreen.kt` | 65 |
| `AlarmRingingOverlay.kt` | 62 |
| `ToneShared.kt` | 60 |
| `StopwatchScreen.kt` | 57 |
| `MainActivity.kt` | 54 |
| `WorldClockScreen.kt` | 47 |
| `AlarmsScreen.kt` | 41 |
| `MusicSchedulerScreen.kt` | 41 |
| `TimerScreen.kt` | 39 |
| `SettingsScreen.kt` | 38 |
| `ui/ClockViewModel.kt` | 2 |
| **Total** | **506** |

(`AppPrefs.kt`, `StopwatchPrefs.kt`, `ui/AlarmActivity.kt`, `ui/ChronometerService.kt`,
`ui/Receivers.kt`, `ui/theme/Button3D.kt`, `ui/theme/Type.kt` had candidates that all
turned out to be genuinely in use — net 0 removed.)

## Process

1. **Detection** — a simple-name reference heuristic flagged 584 candidate imports across 18
   files (reproduced the IDE's exact count of 65 for `AlarmRingingOverlay.kt`).
2. **Initial removal** — all 584 candidates removed.
3. **Compile verification** — `./gradlew compileDebugKotlin` surfaced over-flagged imports
   that are referenced only via dot-notation / implicit operators and so had no literal
   token in the body. These were restored:
   - Extension/member/icon imports used as `.dp`, `.sp`, `.clickable`, `.toUri`,
     `Icons.AutoMirrored.Filled.ArrowBack`, etc. — restored via a conservative reconcile
     pass (restore any removed import whose name appears as a token anywhere in the body).
   - `androidx.compose.runtime.getValue` in `AlarmActivity.kt` — used implicitly by a `by`
     property delegate (`State.cannot serve as a delegate` error); restored manually.
   - `collectAsStateWithLifecycle`, `pointerInput`, `detectTapGestures`,
     `detectHorizontalDragGestures`, `background`, `clip`, `shadow`, `launch`,
     `asStateFlow`, etc. — restored where actually used.
   - In total **78** of the 584 candidates were restored.
4. **Final verification** — `./gradlew compileDebugKotlin` → **BUILD SUCCESSFUL**.

## Verification

`./gradlew compileDebugKotlin` completes with `BUILD SUCCESSFUL` and no compile errors.
No behavior change — only unused import directives were removed.
