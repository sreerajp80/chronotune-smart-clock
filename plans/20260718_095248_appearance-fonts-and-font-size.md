# Appearance: font family picker + font-size slider

**Status:** completed

## What the user wants

Under **Settings → Appearance**, add:
1. A **font picker** offering several beautiful open-source fonts (plus the current system default).
2. A **font-size slider** to make all app text larger or smaller.

Both should apply app-wide (Clock, Alarms, Stopwatch, Timer, Schedules, Settings, and the alarm-ringing screen).

## Decisions already confirmed with the user

- **Font delivery:** bundled `.ttf` files in `res/font/` (works offline, no Play Services). OFL-licensed, fetched from the official Google Fonts repo.
- **Font set (curated mix):** `System Default` (first), **Inter**, **Poppins**, **Nunito**, **Lato**, **Roboto Slab** (serif), **Merriweather** (serif).
- **Size range:** `0.85x – 1.30x`, 5 named steps (Small, Default, Large, Larger, Largest).

## Key facts about the current code

- No fonts are bundled today; [Type.kt](app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/theme/Type.kt) uses `FontFamily.Default` and defines only `bodyLarge`.
- Most screens use **hardcoded `fontSize = X.sp`**, not `MaterialTheme.typography` tokens. So a real global size control must scale `LocalDensity.fontScale` (which scales every `sp`), not just the `Typography`.
- Similarly, a global font-family override must set both the `Typography` (for Material components) **and** `LocalTextStyle` (for the many plain `Text(...)` calls that don't set a family).
- `MyApplicationTheme` in [Theme.kt](app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/theme/Theme.kt) is the single wrapper used by both `MainActivity` and `AlarmActivity`, so applying the family/scale there covers the whole app.
- Preferences follow a clear pattern in [AppPrefs.kt](app/src/main/java/in/sreerajp/chronotune_smart_clock/AppPrefs.kt): a `KEY_`, a `MutableStateFlow`, a public `StateFlow`, load in `init()`, and a `setX()` writer. Settings UI collects the flow with `collectAsStateWithLifecycle()`.
- `minSdk = 24`. Variable-font weight variation settings only apply on API 26+, so we bundle **static** weight files (Regular + Bold) per family; Medium/SemiBold synthesize acceptably. This keeps it correct on all supported devices.

## Files to change / add

### New files
1. `app/src/main/res/font/` — bundled OFL `.ttf` files. Per family, static **Regular** and **Bold** (2 files each). Snake_case names required by Android resources, e.g.:
   - `inter_regular.ttf`, `inter_bold.ttf`
   - `poppins_regular.ttf`, `poppins_bold.ttf`
   - `nunito_regular.ttf`, `nunito_bold.ttf`
   - `lato_regular.ttf`, `lato_bold.ttf`
   - `roboto_slab_regular.ttf`, `roboto_slab_bold.ttf`
   - `merriweather_regular.ttf`, `merriweather_bold.ttf`
2. `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/theme/AppFonts.kt` — the font catalog:
   - An `enum class AppFont(val key: String, val displayName: String)` with `SYSTEM, INTER, POPPINS, NUNITO, LATO, ROBOTO_SLAB, MERRIWEATHER`.
   - A `@Composable`/`val` mapping each `AppFont` to a `FontFamily` (built from the bundled `Font(R.font.*, weight)` pairs; `SYSTEM` → `FontFamily.Default`).
   - Helper `fontFamilyFor(AppFont): FontFamily`.
   - A `FONT_SCALE_STEPS = listOf(0.85f, 1.0f, 1.15f, 1.22f, 1.30f)` with labels `Small, Default, Large, Larger, Largest` (default index 1 = 1.0x).
3. `app/src/main/res/font/OFL.txt` (or a short `LICENSES.md` note under the font folder) — record the SIL Open Font License and font sources for attribution.

### Edited files
4. **`AppPrefs.kt`** — add two prefs following the existing pattern:
   - `KEY_FONT = "app_font_family"` (String key = `AppFont.key`), flow `appFont: StateFlow<AppFont>`, default `SYSTEM`, `setAppFont(context, AppFont)`.
   - `KEY_FONT_SCALE = "app_font_scale"` (Float), flow `fontScale: StateFlow<Float>`, default `1.0f`, `setFontScale(context, Float)` clamped to `0.85f..1.30f`. Load both in `init()`.
5. **`ui/theme/Type.kt`** — replace the single hardcoded `Typography` with a `fun appTypography(fontFamily: FontFamily): Typography` that returns a `Typography` whose styles use the given family (keeping current sizes). Keep a default `Typography` val for any existing callers.
6. **`ui/theme/Theme.kt`** — in `MyApplicationTheme`:
   - Collect `AppPrefs.appFont` and `AppPrefs.fontScale`.
   - Build `typography = appTypography(fontFamilyFor(appFont))`.
   - Wrap the `MaterialTheme(...)` content so that:
     - `LocalDensity` is provided with `fontScale = currentDensity.fontScale * userScale` (this scales every `sp` app-wide, including hardcoded ones, while still respecting the device's own font-size setting).
     - `LocalTextStyle` is provided as `LocalTextStyle.current.copy(fontFamily = chosenFamily)` so plain `Text(...)` calls pick up the family.
   - Pass the built `typography` to `MaterialTheme`.
7. **`SettingsScreen.kt`** — in the Appearance page (`1 -> { ... }`, around line 287), add two new cards after `AccentColorCard`:
   - `FontFamilyCard(context)` — a chunky card (matching `AccentColorCard` styling) listing the fonts. Each row shows the font's **name rendered in that font** as a live preview, with a selected check/highlight; tapping calls `AppPrefs.setAppFont`.
   - `FontSizeCard(context)` — a chunky card with the current step label (e.g. "Large") and a slider. Reuse the existing `ChunkyValueSlider` visual: map the 5 discrete steps to fractions, snap on change, and call `AppPrefs.setFontScale`. Show a short sample line of text that visibly reflects the current scale.
   - Add needed imports (`AppFont`, `fontFamilyFor`, font catalog).

## How the fix works (summary)

- Font files live in `res/font/`; `AppFonts.kt` turns them into `FontFamily`s and lists the size steps.
- `AppPrefs` persists the chosen font key and scale factor and exposes them as flows.
- `MyApplicationTheme` reads both and applies them globally via `Typography` + `LocalTextStyle` (family) and `LocalDensity.fontScale` (size). Because it wraps every activity's content, all screens update live when the user changes the setting.
- Two new Settings cards let the user pick a font and drag the size slider, matching the existing chunky card / slider design.

## Notes / risks

- Bundling 12 static `.ttf` files adds roughly ~1–3 MB to the APK. Acceptable for the benefit; can trim weights later if needed.
- Very large scale (1.30x) is within layout tolerance; existing tight rows were checked conceptually but I'll sanity-check the busiest screens (Alarms list, Settings) after building.
- Multiplying onto the device `fontScale` means users who already enlarge system text get a combined effect — this is the correct, accessibility-friendly behavior.
- No database or migration changes; prefs are additive with safe defaults, so existing users see no change until they opt in.

## Verification

- Build the app (`assembleDebug`).
- Launch, go to Settings → Appearance, switch fonts (confirm live change across Clock/Alarms/Settings) and drag the size slider (confirm text grows/shrinks app-wide and persists across restart).
