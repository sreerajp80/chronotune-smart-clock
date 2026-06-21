# Change log: Make "Configure Alarm" dialog taller

Implements plan `plans/20260616_080621_alarm-dialog-taller.md`.

## What changed
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/MainActivity.kt`
  - In the `AlarmEditDialog` composable, changed the `Card` sizing modifier from
    `.heightIn(max = 580.dp)` to `.fillMaxHeight(0.95f)`.

## Effect
The "Configure Alarm" / "Edit Alarm" dialog now expands to ~95% of the screen height
instead of being capped at 580dp, removing the large empty gap below it and giving the
scrollable content more vertical room. `fillMaxWidth` and `verticalScroll` are unchanged,
so longer content still scrolls. No new imports were needed (`fillMaxHeight` comes from
the existing `androidx.compose.foundation.layout.*` wildcard import).
