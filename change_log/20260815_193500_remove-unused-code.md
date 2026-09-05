# Removed unused code

Implements [plans/20260815_192700_remove-unused-code.md](../plans/20260815_192700_remove-unused-code.md).

## Summary

Removed dead code found by a full scan of the project: unused colours, three unused
functions, 20 unused imports, and 15 unused Gradle dependencies.

Net effect: **147 lines deleted, 27 added** across 10 files, and the Retrofit / OkHttp /
Moshi network stack is no longer packed into the APK.

## What was removed

### 1. Unused colours

- `ui/theme/Color.kt` — deleted 14 colours nothing read: the clock-face accents
  (`ClockFaceSurfaceLight/Dark`, `ClockFaceEdgeLight/Dark`, `SecondHandLight/Dark`),
  the 3D button shadow tints (`ButtonShadowLight/Dark`), and the six Material 3
  template fallbacks (`Purple80`, `PurpleGrey80`, `Pink80`, `Purple40`,
  `PurpleGrey40`, `Pink40`).
- `res/values/colors.xml` — deleted the whole file. All seven colours in it
  (`purple_200`, `purple_500`, `purple_700`, `teal_200`, `teal_700`, `black`,
  `white`) were Android Studio template leftovers with no reference anywhere.

### 2. Unused functions

- `ui/ClockViewModel.kt` — `clearImportState()`.
- `ui/VoiceIntentActivity.kt` — `intentFor()` and the `companion object` that only
  held it. The now-orphaned `import android.content.Context` went with it.
- `data/Models.kt` — `isSkippedToday()`. The app uses `isSkippedOnEpochDay()` and
  `isSkippingActive()` instead.

### 3. Unused imports (20)

- `AlarmsScreen.kt` — 6 (`BackHandler`, `FontFamily`, `graphicsLayer`,
  `roundToInt`, automirrored `ArrowBack`, automirrored `VolumeUp`)
- `MusicSchedulerScreen.kt` — 11 (`BasicTextField`, `Brush`,
  `detectHorizontalDragGestures`, `detectTapGestures`, `LocalDensity`,
  `FontFamily`, `Dialog`, `shadow`, `luminance`, `pointerInput`, automirrored
  `VolumeUp`)
- `StopwatchScreen.kt` — 1 (`Color`)
- `TimerScreen.kt` — 1 (`Color`)
- `ui/VoiceIntentActivity.kt` — 1 (`Context`, see above)

### 4. Unused Gradle dependencies

Commented out in `app/build.gradle.kts`, following the convention already used in that
file so any of them can be switched back on by uncommenting one line.

Removed from the shipped APK:

- `retrofit`, `converter-moshi`, `moshi-kotlin`, `okhttp`, `logging-interceptor`,
  and the `moshi-kotlin-codegen` KSP processor — the app makes **no network calls at
  all**. The one JSON file it reads (`assets/app_config.json`) is parsed with the
  platform's own `org.json.JSONObject` in `SettingsScreen.kt`.
- `androidx-navigation-compose` — navigation is a plain tab index in `MainActivity`,
  there is no `NavHost`.
- `androidx-compose-ui-tooling-preview` — there is no `@Preview` in the project. The
  debug-only `ui-tooling` dependency still pulls it in for the IDE.

Removed from the test builds:

- the `roborazzi` Gradle plugin and `roborazzi`, `roborazzi-compose`,
  `roborazzi-junit-rule` — no screenshot tests exist.
- `androidx-compose-ui-test-junit4` (both test and androidTest) and
  `ui-test-manifest` — no `createComposeRule` anywhere.
- `androidx-espresso-core` — no Espresso in `ExampleInstrumentedTest`.

Dropping the Moshi KSP processor also makes builds a little faster.

## What was deliberately kept

- `MainActivity.TAB_STOPWATCH` and `TAB_SCHEDULES` — unused today, but part of a
  numbered set (`TAB_CLOCK`=0 … `TAB_SCHEDULES`=4) defining the `EXTRA_OPEN_TAB`
  contract. Deleting two of five would leave the rest looking arbitrary.
- `AnalogClockWidgetProvider.onAppWidgetOptionsChanged()` — Android calls it on resize.
- `androidx.compose.runtime.getValue` / `setValue` imports in `ui/AlarmActivity.kt`,
  `ui/theme/Theme.kt` and `VoiceCommandUi.kt`. A text scan flags them as unused, but
  Kotlin needs them for `val x by someState` delegation.
- The `res/mipmap-*` launcher icons — referenced from `AndroidManifest.xml`.
- `ExampleUnitTest`, `ExampleRobolectricTest`, `ExampleInstrumentedTest` — template
  tests, but they run and prove the Robolectric / instrumentation setup works.
- `res/values-ml/strings.xml` — no orphan keys, only five missing translations
  (`app_name` and the four widget labels/descriptions). That is a separate matter.

## Verification

`./gradlew compileDebugKotlin testDebugUnitTest --rerun-tasks` — **BUILD SUCCESSFUL**.

- 52 unit tests across 5 classes: 0 failures, 0 errors, 0 skipped.
- The only compiler warning is the pre-existing deprecated
  `Icons.Filled.ArrowBack` at `HolidaysScreen.kt:100`, which was there before this
  change and is out of scope here.

## Note

`ui/ClockViewModel.kt` also carries uncommitted changes from the
`20260815_190238_dismissed-alarm-rings-again-in-app` plan. Those were left untouched;
the only edit made here was deleting `clearImportState()`.

## Files changed

| File | Change |
|---|---|
| `app/build.gradle.kts` | commented out 15 dependencies and the Roborazzi plugin |
| `app/src/main/java/.../ui/theme/Color.kt` | -20 lines |
| `app/src/main/res/values/colors.xml` | deleted |
| `app/src/main/java/.../ui/ClockViewModel.kt` | removed `clearImportState()` |
| `app/src/main/java/.../ui/VoiceIntentActivity.kt` | -8 lines |
| `app/src/main/java/.../data/Models.kt` | -3 lines |
| `app/src/main/java/.../AlarmsScreen.kt` | -6 imports |
| `app/src/main/java/.../MusicSchedulerScreen.kt` | -11 imports |
| `app/src/main/java/.../StopwatchScreen.kt` | -1 import |
| `app/src/main/java/.../TimerScreen.kt` | -1 import |
