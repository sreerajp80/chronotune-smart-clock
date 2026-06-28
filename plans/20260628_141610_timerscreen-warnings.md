# TimerScreen.kt — 11 IDE warnings

**Status:** completed

## Issue
[TimerScreen.kt](app/src/main/java/in/sreerajp/chronotune_smart_clock/TimerScreen.kt)
reports 11 problems of two kinds:

### A. "Assigned value is never read" (lines 183, 189, 481, 569, 659 — and 2 more below the fold)
These all assign to **Compose state** delegates set inside event lambdas:
- `showAddSheet = false` (183, 189) — read at line 181 (`if (showAddSheet)`).
- `screen = "tones" / "main"` (481, 569, 659) — read at lines 432/434.
- The 2 remaining (not visible in the screenshot) are the same pattern inside `AddTimerSheet`
  (e.g. `toneTab` / `currentTone` / `customToneUri` / `playingKey` assignments in lambdas).

**These are false positives.** The assignments happen in lambdas that run on a *later*
recomposition, while the reads appear earlier in source order, so the inspection's
intra-function dataflow can't see the value is ever read. Each assignment is functional —
removing any of them would break tab-switching, sheet dismissal, and tone selection.

### B. "Redundant curly braces in string template" (lines 680–683)
In `formatDurationLabel`, `${h}`, `${m}`, `${s}` are simple identifier references that don't
need braces. Genuine cleanup.

## Files to change
- [TimerScreen.kt](app/src/main/java/in/sreerajp/chronotune_smart_clock/TimerScreen.kt)

## Plan for the fix
1. **Suppress the false-positive dead-assignment warnings** by adding
   `@Suppress("AssignedValueIsNeverRead")` at the **function level** on the two composables
   that own this state:
   - `TimerScreen` (line 47) — covers 183, 189.
   - `AddTimerSheet` (line 356) — covers 481, 569, 659 and the 2 hidden ones in the same function.

   Function-level (rather than per-line) is used because the assignments are statements inside
   lambdas, which can't carry their own annotation, and because the inspection is
   systematically wrong for Compose state mutated in event lambdas.

   *(If the IDE's actual suppression key differs, it is the one offered by Alt+Enter →
   "Suppress for function" on that warning; I'll match it.)*

2. **Fix the redundant curly braces** in `formatDurationLabel` (lines 680–683):
   - `append("${h} h ")` → `append("$h h ")`
   - `append("${m} min")` → `append("$m min")`
   - `append("${s} s")` → `append("$s s")`
   - `append(" ${s} s")` → `append(" $s s")`

No behavior change.
