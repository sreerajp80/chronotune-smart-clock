# Remove unused code

**Status:** completed

## What I checked

I scanned the whole project for dead code in four ways:

1. Compiled the app with `./gradlew compileDebugKotlin --rerun-tasks` and read every
   compiler warning. Result: **only one warning** in the whole build (a deprecated
   `Icons.Filled.ArrowBack` in [HolidaysScreen.kt:100](app/src/main/java/in/sreerajp/chronotune_smart_clock/HolidaysScreen.kt#L100)).
   So there are no unused local variables and no unused private members.
2. Cross-referenced every top-level and class-level declaration (492 functions,
   912 vals/vars, all classes/objects/interfaces) against the whole source tree.
3. Checked every import in every Kotlin file against the file body.
4. Checked every resource (drawable, layout, mipmap, string, color) and every
   Gradle dependency against actual usage.

The Kotlin code is in good shape. What follows is what is genuinely never used.

## Issue 1 — 14 unused colours in the theme palette

[ui/theme/Color.kt](app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/theme/Color.kt)
lines 32-50 define three groups of colours that nothing reads:

- Clock-face accents: `ClockFaceSurfaceLight`, `ClockFaceEdgeLight`,
  `ClockFaceSurfaceDark`, `ClockFaceEdgeDark`, `SecondHandLight`, `SecondHandDark`
- 3D button shadows: `ButtonShadowLight`, `ButtonShadowDark`
- Material 3 template fallbacks: `Purple80`, `PurpleGrey80`, `Pink80`, `Purple40`,
  `PurpleGrey40`, `Pink40`

The widget clock face ([AnalogClockFaceRenderer.kt](app/src/main/java/in/sreerajp/chronotune_smart_clock/widget/AnalogClockFaceRenderer.kt))
computes its own colours, so the clock-face group is left over from an older version.

**Fix:** delete lines 32-50. Keep everything above line 32 (all of it is used).

## Issue 2 — all 7 colours in `res/values/colors.xml` are unused

`purple_200`, `purple_500`, `purple_700`, `teal_200`, `teal_700`, `black`, `white`
are the Android Studio template colours. Nothing in the Kotlin code, the layouts,
the drawables, or the manifest refers to them.

**Fix:** delete `app/src/main/res/values/colors.xml`.

## Issue 3 — three dead functions

| Function | File | Note |
|---|---|---|
| `clearImportState()` | [ui/ClockViewModel.kt:332](app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ClockViewModel.kt#L332) | The Holidays screen never resets the import state. |
| `intentFor()` | [ui/VoiceIntentActivity.kt:200](app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/VoiceIntentActivity.kt#L200) | Its own comment says "convenience for other parts of the app" — no part of the app uses it. It is the only member of the `companion object`, so the companion object goes too. |
| `isSkippedToday()` | [data/Models.kt:131](app/src/main/java/in/sreerajp/chronotune_smart_clock/data/Models.kt#L131) | Sibling of `isPausedNow()`. The app uses `isSkippedOnEpochDay()` and `isSkippingActive()` instead. |

**Fix:** delete all three (plus the companion object wrapper and any import that
becomes unused as a result).

## Issue 4 — 20 unused imports

| File | Unused imports |
|---|---|
| [AlarmsScreen.kt](app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmsScreen.kt) | `BackHandler`, `FontFamily`, `graphicsLayer`, `kotlin.math.roundToInt`, `automirrored.filled.ArrowBack`, `automirrored.filled.VolumeUp` |
| [MusicSchedulerScreen.kt](app/src/main/java/in/sreerajp/chronotune_smart_clock/MusicSchedulerScreen.kt) | `BasicTextField`, `Brush`, `detectHorizontalDragGestures`, `detectTapGestures`, `LocalDensity`, `FontFamily`, `Dialog`, `shadow`, `luminance`, `pointerInput`, `automirrored.filled.VolumeUp` |
| [StopwatchScreen.kt](app/src/main/java/in/sreerajp/chronotune_smart_clock/StopwatchScreen.kt) | `androidx.compose.ui.graphics.Color` |
| [TimerScreen.kt](app/src/main/java/in/sreerajp/chronotune_smart_clock/TimerScreen.kt) | `androidx.compose.ui.graphics.Color` |

**Fix:** delete those import lines.

Note: the `androidx.compose.runtime.getValue` / `setValue` imports in
`ui/AlarmActivity.kt`, `ui/theme/Theme.kt` and `VoiceCommandUi.kt` *look* unused but
are **not** — Kotlin needs them for the `val x by someState` delegation. They stay.

## Issue 5 — unused Gradle dependencies

The app has **no network code at all**: no `retrofit`, `okhttp`, `moshi`, `@Json`
or `HttpLoggingInterceptor` anywhere in the source. It also has no
`NavHost` / `rememberNavController` (navigation is a simple tab index in
`MainActivity`), no `@Preview`, and no screenshot tests.

Shipped in the APK but never used:

- `libs.retrofit`
- `libs.converter.moshi`
- `libs.moshi.kotlin`
- `"ksp"(libs.moshi.kotlin.codegen)`
- `libs.okhttp`
- `libs.logging.interceptor`
- `libs.androidx.navigation.compose`
- `libs.androidx.compose.ui.tooling.preview` (nothing uses `@Preview`; the debug-only
  `ui.tooling` dependency pulls it in anyway for the IDE)

Test-only, never used:

- `alias(libs.plugins.roborazzi)` plugin, plus `libs.roborazzi`,
  `libs.roborazzi.compose`, `libs.roborazzi.junit.rule` — no `captureRoboImage`
  or Roborazzi rule anywhere
- `libs.androidx.compose.ui.test.junit4` (both `testImplementation` and
  `androidTestImplementation`) and `libs.androidx.compose.ui.test.manifest` — no
  `createComposeRule` anywhere
- `libs.androidx.espresso.core` — no Espresso in `ExampleInstrumentedTest`

**Fix:** in [app/build.gradle.kts](app/build.gradle.kts), comment these out the same
way the file already comments out other unused dependencies (the file's own comment at
line 67 says this is the project's convention). The version catalog entries in
`gradle/libs.versions.toml` stay, so any of them can be switched back on by
uncommenting one line.

Removing the network stack drops Retrofit, OkHttp and Moshi from the APK, and dropping
the Moshi KSP processor makes builds faster.

## Deliberately NOT removed

- `MainActivity.TAB_STOPWATCH` and `TAB_SCHEDULES` — unused today, but they are part of
  a numbered set (`TAB_CLOCK`=0 … `TAB_SCHEDULES`=4) that defines the `EXTRA_OPEN_TAB`
  contract. Deleting two of five would make the remaining numbers look arbitrary.
- `AnalogClockWidgetProvider.onAppWidgetOptionsChanged()` — no caller in this app because
  Android itself calls it when the widget is resized.
- The launcher icons in `res/mipmap-*` — referenced from `AndroidManifest.xml`.
- `ExampleUnitTest`, `ExampleRobolectricTest`, `ExampleInstrumentedTest` — template
  tests, but they do run and they prove the Robolectric/instrumentation setup works.
- Everything in `res/values-ml/strings.xml` — no orphan keys; it is just missing five
  translations, which is a separate matter.

## Files to be changed

1. `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/theme/Color.kt` — delete 14 unused colours
2. `app/src/main/res/values/colors.xml` — delete the file
3. `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ClockViewModel.kt` — delete `clearImportState()`
4. `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/VoiceIntentActivity.kt` — delete `intentFor()` and its companion object
5. `app/src/main/java/in/sreerajp/chronotune_smart_clock/data/Models.kt` — delete `isSkippedToday()`
6. `app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmsScreen.kt` — delete 6 imports
7. `app/src/main/java/in/sreerajp/chronotune_smart_clock/MusicSchedulerScreen.kt` — delete 11 imports
8. `app/src/main/java/in/sreerajp/chronotune_smart_clock/StopwatchScreen.kt` — delete 1 import
9. `app/src/main/java/in/sreerajp/chronotune_smart_clock/TimerScreen.kt` — delete 1 import
10. `app/build.gradle.kts` — comment out 15 unused dependencies and the Roborazzi plugin

## How I will verify

Run `./gradlew compileDebugKotlin --rerun-tasks` and `./gradlew testDebugUnitTest`
after the changes and confirm both pass with no new warnings.

## Note on uncommitted work

`ui/ClockViewModel.kt` currently has uncommitted changes from the
`20260815_190238_dismissed-alarm-rings-again-in-app` plan. My edit to that file is a
single small deletion far from those changes, so it will not disturb them.
