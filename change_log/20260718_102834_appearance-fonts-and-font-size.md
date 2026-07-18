# Change log: Appearance fonts + font-size slider

Implements plan [plans/20260718_095248_appearance-fonts-and-font-size.md](../plans/20260718_095248_appearance-fonts-and-font-size.md).

## What was added

Under **Settings → Appearance**, two new controls:

1. **Font** — pick the app-wide typeface. Options: `System Default` plus six bundled
   open-source fonts — **Inter**, **Poppins**, **Nunito**, **Lato**, **Roboto Slab** (serif),
   **Merriweather** (serif). Each option previews its own name in that font.
2. **Font size** — a 5-step slider (Small · Default · Large · Larger · Largest) that scales
   every text size in the app from 0.85x to 1.30x. Includes a live sample line.

Both apply instantly across the whole app and persist across restarts.

## Files changed

### New
- `app/src/main/res/font/` — 12 bundled static `.ttf` files (Regular + Bold per family).
  Variable-font families (Inter, Nunito, Roboto Slab, Merriweather) were instanced to static
  weights with `fonttools`; Poppins and Lato shipped their static weights directly. Static
  weights were used because `minSdk = 24` predates variable-font support.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/theme/AppFonts.kt` — the `AppFont`
  enum, `fontFamilyFor()`, the `FONT_SCALE_STEPS` / `FONT_SCALE_LABELS` tables, and
  `fontScaleStepIndex()` helper.
- `licenses/fonts/` — SIL OFL 1.1 / Apache-2.0 license texts and a `README.md` attribution table.

### Edited
- `AppPrefs.kt` — added `appFont` (`StateFlow<AppFont>`) + `setAppFont()`, and `fontScale`
  (`StateFlow<Float>`, clamped 0.85..1.30) + `setFontScale()`; both loaded in `init()`.
- `ui/theme/Type.kt` — replaced the single hardcoded `Typography` with `appTypography(fontFamily)`
  that swaps the family across all Material text styles (keeps a `DefaultTypography` for System).
- `ui/theme/Theme.kt` — `MyApplicationTheme` now reads `appFont` + `fontScale` and applies them
  globally: `Typography` and `LocalTextStyle` carry the chosen family (covering both Material
  components and the app's many plain `Text(fontSize = X.sp)` calls), and `LocalDensity.fontScale`
  is multiplied by the user's scale so every `sp` grows/shrinks while still respecting the
  device's own accessibility font setting.
- `SettingsScreen.kt` — added `FontFamilyCard` + `FontRow` and `FontSizeCard` (reusing the
  existing `ChunkyIconTile` / `ChunkyValueSlider` styling) to the Appearance page, plus imports.

## How it works

Because `MyApplicationTheme` wraps the content of both `MainActivity` and `AlarmActivity`,
setting the family via `Typography`/`LocalTextStyle` and the size via `LocalDensity.fontScale`
in one place makes every screen — Clock, Alarms, Stopwatch, Timer, Schedules, Settings, and the
alarm-ringing screen — update live. Prefs are additive with safe defaults (`System`, 1.0x), so
existing users see no change until they opt in. No database or migration changes.

## Verification

- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- Installed on a physical device (adb) and drove the flow:
  - Font list showed all 7 options, each in its own typeface.
  - Selecting **Merriweather** turned the entire UI serif instantly.
  - Dragging the size slider to **Largest** enlarged all text app-wide.
  - Both settings **persisted across an app relaunch**.
  - Restored to System Default / Default afterward to leave the device unchanged.

## Notes

- APK grows by ~4.8 MB from the bundled fonts (Merriweather is the heaviest family).
- At the maximum size step, a couple of the settings tab labels wrap to two lines — cosmetic and
  expected at 1.30x; layouts remain usable.
