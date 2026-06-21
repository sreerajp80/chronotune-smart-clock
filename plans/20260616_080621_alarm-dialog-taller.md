# Make "Configure Alarm" dialog taller (touch the bottom)

## Files to be changed
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/MainActivity.kt`
  - `AlarmEditDialog` composable, the `Card` modifier at ~line 1314-1318.

## The issue
The "Configure Alarm" (and "Edit Alarm") dialog is a Compose `Dialog` whose `Card`
is constrained with `.heightIn(max = 580.dp)`. On taller screens this leaves a large
empty gap below the dialog. The card floats in the middle instead of using the
available vertical space, so the scrollable content area is smaller than it needs to be.

## The plan for the fix
In `AlarmEditDialog`, change the `Card` sizing so the dialog grows to nearly the full
screen height, giving the content more room:

- Replace `.heightIn(max = 580.dp)` with `.fillMaxHeight(0.95f)` so the card expands
  to ~95% of the screen height (small top/bottom margin keeps the rounded-card look
  while effectively touching near the bottom).
- Keep `.fillMaxWidth()` and `.verticalScroll(rememberScrollState())` as-is, so content
  longer than the card still scrolls.

This is a single, minimal modifier change. No logic, no new imports
(`fillMaxHeight` is from the same `androidx.compose.foundation.layout` package already
used by `fillMaxWidth`).

### Note / decision point
`fillMaxHeight(0.95f)` keeps the centered floating-card style but makes it tall.
If instead you want the dialog to truly span edge-to-edge (no side/top margin, like a
full-screen sheet), that requires `DialogProperties(usePlatformDefaultWidth = false)`
plus `.fillMaxSize()` — a bigger visual change. The plan above assumes you want the
taller-but-still-a-card look. Let me know if you prefer the full-screen variant.
