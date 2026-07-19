# Settings screen: cards instead of tabs

**Status:** completed

## Files to change

- `app/src/main/java/in/sreerajp/chronotune_smart_clock/SettingsScreen.kt` (only file)

## What the issue is

The settings screen uses a `PrimaryTabRow` with 5 tabs (About, Appearance, Permissions,
Alarm, Music). Five tabs make each tab label tiny and cramped, and it does not scale when
more settings are added. The user wants a hub screen made of cards. Tapping a card opens
that section as its own page.

## Plan for the fix

1. Add a small private enum `SettingsSection` with the five sections (ABOUT, APPEARANCE,
   PERMISSIONS, ALARM, MUSIC), each carrying its title, subtitle, and icon.
2. In `SettingsScreen`, replace `selectedTab: Int` with
   `var openSection by remember { mutableStateOf<SettingsSection?>(null) }`.
   - `null` means the hub (card list) is shown.
   - A non-null value means that section's page is shown.
3. Top bar becomes dynamic:
   - Hub: title "Settings & Info", back arrow calls `onBack()` (unchanged behaviour).
   - Section page: title is the section name, back arrow returns to the hub
     (`openSection = null`).
4. Add `BackHandler` so the device back button also returns to the hub when a section is
   open, instead of leaving settings.
5. New private composable `SettingsHub(...)`: a scrolling column of tappable cards, one per
   section, each with a round icon tile, title, one-line subtitle, and a chevron. Styling
   matches the existing "chunky card" look already used in this file
   (`ChunkyAppearanceToggle`, `InfoCard`).
6. Move the existing five `when (selectedTab)` bodies into five private composables —
   `AboutSection`, `AppearanceSection`, `PermissionsSection`, `AlarmSection` — with the
   music one already existing as `MusicSchedulerSettings`. The content of each is kept
   exactly as it is today; only the enclosing wrapper changes. Permission state and the
   permission launcher stay in `SettingsScreen` and are passed into `PermissionsSection`.
7. Remove the now-unused tab imports/usages.

No behaviour of any individual setting changes. No other screen is touched;
`ClockAppScreen.kt` calls `SettingsScreen(...)` with the same parameters.

## Risk

Low. Single file, mostly a move of existing code into new composables.
