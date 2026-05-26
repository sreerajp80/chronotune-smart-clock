# ChronoTune Smart Clock

A feature-rich Android clock app featuring multiple location clocks, custom tone alarms, a stopwatch, a timer, and an automated music scheduler powered by the Gemini AI API.

## Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` to your Gemini API key (see `.env.example` for the expected format)
5. Configure keystore signing credentials in `local.properties`:
   ```
   STORE_PASSWORD=<your-keystore-password>
   KEY_PASSWORD=<your-key-password>
   KEY_ALIAS=chronotune-smart-clock   # or your custom alias
   KEYSTORE_PATH=<path-to-keystore>   # optional, defaults to keystore.jks in project root
   ```
6. Run the app on an emulator or physical device
