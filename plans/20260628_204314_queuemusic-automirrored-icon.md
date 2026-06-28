# Fix `QueueMusic` AutoMirrored Icon Deprecation

**Status:** completed

## Issue

The Music tab icon in [SettingsScreen.kt:172](../app/src/main/java/in/sreerajp/chronotune_smart_clock/SettingsScreen.kt#L172)
uses `Icons.Default.QueueMusic`, which is deprecated:

> 'val Icons.Filled.QueueMusic: ImageVector' is deprecated. Use the AutoMirrored version at Icons.AutoMirrored.Filled.QueueMusic.

`QueueMusic` is a directional icon, so Material recommends the auto-mirrored variant (which flips correctly
in RTL layouts). A project-wide scan shows this is the **only** remaining deprecated directional-icon usage —
all other directional icons (`ArrowBack`, `KeyboardArrowRight`, `VolumeUp`, etc.) already use
`Icons.AutoMirrored.Filled.*`.

## Files to change

1. `app/src/main/java/in/sreerajp/chronotune_smart_clock/SettingsScreen.kt`

## Plan for the fix

1. Add the import `androidx.compose.material.icons.automirrored.filled.QueueMusic`
   (placed alongside the existing `automirrored.filled.ArrowBack` import at line 50).
2. Change line 172 from `Icons.Default.QueueMusic` to `Icons.AutoMirrored.Filled.QueueMusic`.

No other behavior changes; the icon renders identically in LTR and now mirrors correctly in RTL.

## Verification

- Confirm the deprecation warning is gone.
- Confirm the project compiles (`Icons.AutoMirrored.Filled.QueueMusic` resolves with the new import).
