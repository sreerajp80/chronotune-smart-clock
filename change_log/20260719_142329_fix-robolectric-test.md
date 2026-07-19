# Fixed the failing ExampleRobolectricTest

Implements [plans/20260719_142329_fix-robolectric-test.md](../plans/20260719_142329_fix-robolectric-test.md).

## What was wrong

The test failed on every run with:

```
Failed to create a Robolectric sandbox: Android SDK 36 requires Java 21 (have Java 17)
```

The test asked Robolectric to emulate Android SDK 36, but Robolectric needs Java 21 for
SDK 35 and above, and the build runs unit tests on Java 17.

## What changed

- Added `app/src/test/resources/robolectric.properties` with `sdk=34`. This sets the
  emulated Android version once for every Robolectric test in the project. 34 is the
  newest version Robolectric can run on Java 17.
- `ExampleRobolectricTest.kt`: removed the `@Config(sdk = [36])` annotation and its
  import, so the test uses the shared properties file. Added a short comment pointing at
  that file.

## Result

`./gradlew :app:testDebugUnitTest` passes: 23 tests, 0 failures
(ExampleRobolectricTest 1, ExampleUnitTest 1, VoiceCommandParserTest 21).
