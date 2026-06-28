# Fix `QueueMusic` AutoMirrored Icon Deprecation

Implements [plans/20260628_204314_queuemusic-automirrored-icon.md](../plans/20260628_204314_queuemusic-automirrored-icon.md).

## What changed

In `app/src/main/java/in/sreerajp/chronotune_smart_clock/SettingsScreen.kt`:

1. Added import `androidx.compose.material.icons.automirrored.filled.QueueMusic` (next to the
   existing `automirrored.filled.ArrowBack` import).
2. Changed the Music tab icon from the deprecated `Icons.Default.QueueMusic` to
   `Icons.AutoMirrored.Filled.QueueMusic` (line 173).

## Result

- The deprecation warning for `Icons.Filled.QueueMusic` is resolved.
- The Music tab icon now mirrors correctly in RTL layouts; LTR rendering is unchanged.
- This was the only remaining deprecated directional-icon usage in the project.
