# Change Log — AlarmsScreen.kt Lint/Inspection Cleanup

Implements plan: `plans/20260628_135259_alarmsscreen-lint-cleanup.md`

## Context

The IDE reported 16 inspection warnings. A clean `:app:compileDebugKotlin` (zero compiler
diagnostics) confirmed all 16 are IDE-only inspections, not compiler issues. User approved both
recommended options: file-level suppress for the false-positive group, and leaving #603 untouched.

## Changes to `AlarmsScreen.kt`

1. **Assigned value is never read (9 spots: 166, 183, 753, 1007, 1213, 1234, 1245, 1254, 1256)** —
   added a single file-level `@file:Suppress("ASSIGNED_VALUE_IS_NEVER_READ")` (with an explanatory
   comment) above the package declaration. These are all Compose state setters whose values are read
   on recomposition. Removed the now-redundant inline `@Suppress("ASSIGNED_VALUE_IS_NEVER_READ")`
   that previously guarded `showAddDialog = false`.

2. **Redundant `let` (486)** — `existing.getRepeatDaysList().let { addAll(it) }`
   → `addAll(existing.getRepeatDaysList())`.

3. **Prefer `mutableLongStateOf` (496, 497)** — `mutableStateOf(existing?.pauseStartMillis ?: 0L)`
   and the `pauseEndMillis` equivalent → `mutableLongStateOf(...)`. No import change
   (`androidx.compose.runtime.*` is wildcard-imported).

4. **Unnecessary safe call (531)** — `existing?.customToneName?.ifBlank { "Custom file" } ?: "Custom file"`
   → `existing.customToneName.ifBlank { "Custom file" }`. K2 smart-casts `existing` to non-null inside
   the `if (!existingUri.isNullOrBlank() …)` block, and `Alarm.customToneName` is a non-null `String`,
   so the safe calls and elvis fallback were dead. Behaviour unchanged.

## Left unchanged (deliberate)

- **#603 `BoxWithConstraints` "UiComposable" warning** — IDE-only false positive (compiler emits
  nothing; message is self-contradictory). The `BoxWithConstraints` is load-bearing for sheet sizing,
  so no refactor. Expected to clear on IDE cache invalidate / Gradle sync.

## Verification

`:app:compileDebugKotlin` → BUILD SUCCESSFUL, no warnings or errors. The L531 smart-cast change
compiled, confirming `existing` is non-null at that point.
