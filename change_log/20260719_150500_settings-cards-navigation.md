# Settings screen: cards instead of tabs

Implements `plans/20260719_150000_settings-cards-navigation.md`.

## What changed

Only `app/src/main/java/in/sreerajp/chronotune_smart_clock/SettingsScreen.kt`.

- Removed the 5-tab `PrimaryTabRow` (About, Appearance, Permissions, Alarm, Music).
- Added a private `SettingsSection` enum holding each page's title, one-line subtitle and
  icon.
- `SettingsScreen` now keeps `openSection: SettingsSection?`. When it is `null` the new
  `SettingsHub` card list is shown; otherwise that section's page fills the screen.
- New `SettingsHub` and `SettingsHubCard` composables draw one tappable card per section,
  styled like the existing chunky cards (round icon tile, title, subtitle, chevron).
- The top bar title now shows the open section's name, and its back arrow returns to the
  hub. A `BackHandler` makes the device back button do the same, so back only leaves
  settings from the hub.

## Note on the plan

Plan step 6 said each page body would move into its own composable. The bodies were left
inline inside a `when (section)` block instead — same result on screen, much smaller diff,
and no risk of changing any individual setting. Everything else follows the plan.

## Verification

`./gradlew :app:compileDebugKotlin` passes with no warnings or errors. Not run on a device.
