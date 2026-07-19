# Fix ExampleRobolectricTest failure

**Status:** completed

## The issue

`ExampleRobolectricTest` fails on every run (also with my other changes stashed, so it is
pre-existing). The real error is:

```
java.lang.UnsupportedOperationException: Failed to create a Robolectric sandbox:
Android SDK 36 requires Java 21 (have Java 17)
```

So it is **not** a `compileSdk` problem. The test asks Robolectric to emulate Android
SDK 36 (`@Config(sdk = [36])`). Robolectric needs Java 21 to run SDK 35 and above, but
Gradle runs the unit tests on Java 17. Only JDK 17 is installed on this machine.

## Files to change

- `app/src/test/java/in/sreerajp/chronotune_smart_clock/ExampleRobolectricTest.kt`
- `app/src/test/resources/robolectric.properties` (new file)

## The plan for the fix

1. Add `app/src/test/resources/robolectric.properties` with `sdk=34`. This sets one
   emulated Android version for **all** Robolectric tests in the project, so future tests
   do not hit the same problem. SDK 34 is the newest Android version Robolectric can run
   on Java 17.
2. Remove the `@Config(sdk = [36])` annotation (and its now unused import) from
   `ExampleRobolectricTest.kt`, so the shared properties file is used.
3. Run `./gradlew :app:testDebugUnitTest` and confirm the test passes. If SDK 34 still
   fails on Java 17, step down to `sdk=33`.

## Why not the other option

The "proper" fix is to run unit tests on Java 21 (a Gradle Java toolchain). That needs a
JDK 21 on the machine or a toolchain download plugin, and it would change the build for
everyone. Pinning the Robolectric SDK is a small, local change that keeps the test doing
its job (reading a string resource through a real Android context).
