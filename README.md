# ChronoTune Smart Clock

A feature-rich Android clock app featuring multiple location clocks, custom tone alarms, a stopwatch, a timer, and an automated music scheduler.

## Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project
4. Configure keystore signing credentials in `local.properties`:
   ```
   STORE_PASSWORD=<your-keystore-password>
   KEY_PASSWORD=<your-key-password>
   KEY_ALIAS=chronotune-smart-clock   # or your custom alias
   KEYSTORE_PATH=<path-to-keystore>   # optional, defaults to keystore.jks in project root
   ```
5. Run the app on an emulator or physical device
