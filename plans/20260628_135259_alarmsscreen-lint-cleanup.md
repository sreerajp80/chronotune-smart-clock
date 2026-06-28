# AlarmsScreen.kt — Lint/Inspection Cleanup (16 problems)

**Status:** completed

## Issue

The IDE reports 16 inspection warnings in
[AlarmsScreen.kt](app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmsScreen.kt).

**Important finding:** I ran `:app:compileDebugKotlin --rerun-tasks` — **BUILD SUCCESSFUL with zero
Kotlin compiler warnings**. So all 16 are *IDE-only inspections*, not compiler diagnostics. This
matters for two of them (see #603 and the suppression strategy).

| Line(s) | Warning | Nature |
|---------|---------|--------|
| 166, 183, 753, 1007, 1213, 1234, 1245, 1254, 1256 | Assigned value is never read | False positive — Compose state setters (`editingAlarm`, `screen`, `showPausePicker`); value *is* read on recomposition. File already suppresses this at line 156. |
| 486 | Redundant 'let' call could be removed | Real cleanup |
| 496, 497 | Prefer `mutableLongStateOf` instead of `mutableStateOf` | Real cleanup |
| 531 | Unnecessary safe call on a non-null receiver of type 'Alarm' | Real cleanup — K2 smart-casts `existing` to non-null |
| 603 | Calling a UI Composable where a `UiComposable` was expected | **IDE-only false positive** — not emitted by the compiler; message is self-contradictory |

## Files to change

- `app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmsScreen.kt`

## Plan for the fix

### Real cleanups (unambiguous)

1. **Line 486** — `if (existing != null) existing.getRepeatDaysList().let { addAll(it) }`
   → `if (existing != null) addAll(existing.getRepeatDaysList())`.
2. **Lines 496–497** — `mutableStateOf(existing?.pauseStartMillis ?: 0L)` → `mutableLongStateOf(...)`,
   same for `pauseEndMillis`. `androidx.compose.runtime.*` is wildcard-imported, so no new import.
   The `by` delegate and all Long usages remain valid.
3. **Line 531** — `existing?.customToneName?.ifBlank { "Custom file" } ?: "Custom file"`
   → `existing.customToneName.ifBlank { "Custom file" }`. Inside the `if (!existingUri.isNullOrBlank() …)`
   block, K2 smart-casts `existing` to non-null, and `Alarm.customToneName` is a non-null `String`
   (default `"Morning Breeze"`), so both safe calls and the elvis fallback are dead. Behaviour is
   identical.

### "Assigned value is never read" (9 spots) — RECOMMENDED: file-level suppress

These are all Compose state mutations the inspection can't see being read across recomposition.
The file already suppresses this exact ID inline at line 156.

- **Recommended:** add `@file:Suppress("ASSIGNED_VALUE_IS_NEVER_READ")` at the top of the file and
  remove the now-redundant inline `@Suppress` at line 156. One line instead of nine; clears all spots.
  - *Trade-off:* a genuinely-dead local assignment elsewhere in this file would also be silenced.
- **Alternative:** add an inline `@Suppress("ASSIGNED_VALUE_IS_NEVER_READ")` at each of the 9 spots
  (matches current per-line convention; more verbose; keeps suppression tightly scoped).

### #603 BoxWithConstraints — RECOMMENDED: leave as-is

The compiler accepts this code with no diagnostic; the inspection message is self-contradictory
("UI Composable where UiComposable was expected"), a known class of stale Compose-plugin false
positive. The `BoxWithConstraints` is load-bearing (drives sheet height; tone pane uses
`matchParentSize`), so I will **not** refactor working layout for a non-issue.

- **Recommended:** leave the code unchanged; it typically clears after an IDE Gradle sync /
  "Invalidate Caches & Restart".
- **Alternative:** best-effort source suppress `@Suppress("COMPOSE_APPLIER_CALL_MISMATCH")` on the
  call — but since the compiler never emits this, the suppress ID may not silence the IDE inspection.

## Verification

- Re-run `:app:compileDebugKotlin` to confirm it still builds clean.
- Re-read the edited regions.

## Decisions needed from you

1. Suppression strategy for the 9 "assigned never read": **file-level** (recommended) or per-line?
2. #603: **leave as-is** (recommended) or attempt a source suppress?
