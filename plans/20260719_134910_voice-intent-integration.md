# Voice intent integration (English + Malayalam)

**Status:** completed

## What the user asked for

Let the user create alarms and timers by speaking, e.g. "set an alarm for 7".
Both English and Malayalam speech must work.

## What is missing today

- The app has no `AlarmClock` intent filters, so Google Assistant / Gemini / any other
  voice app cannot hand "set an alarm for 7" to ChronoTune. It always goes to the
  stock clock app.
- There is no microphone button inside the app either.
- Nothing in the app understands Malayalam phrases such as
  "രാവിലെ 7 മണിക്ക് അലാറം വെക്കൂ".

## Plan

Two ways in, one shared brain.

### 1. Shared parser (`VoiceCommandParser.kt`) — new file

A plain Kotlin object (no Android classes) so it can be unit tested.

`parse(text: String, now: Calendar): VoiceCommand`

Sealed result type:
- `SetAlarm(hour, minute, label, days: List<Int>)`
- `SetTimer(durationMs, label)`
- `ShowAlarms`
- `DismissAlarm`
- `SnoozeAlarm(minutes?)`
- `Unknown(text)`

English handled:
- "set an alarm for 7", "set alarm 7:30", "wake me up at 6 am",
  "alarm for quarter past six", "7 o'clock", "half past 5"
- repeats: "every day", "weekdays", "weekends", "on monday and friday", "tomorrow"
- "set a timer for 10 minutes", "timer 1 hour 30 minutes", "5 minute timer"
- "show my alarms", "stop the alarm", "snooze"

Malayalam handled:
- digits in both ASCII (7) and Malayalam numerals (൭)
- number words ഒന്ന്…പന്ത്രണ്ട് and പതിനഞ്ച്/മുപ്പത് for minutes
- time-of-day words: രാവിലെ (AM), ഉച്ചയ്ക്ക് (noon), വൈകിട്ട്/വൈകുന്നേരം (PM),
  രാത്രി (night), പുലർച്ചെ (early AM)
- മണി / മണിക്ക് (o'clock), മിനിറ്റ് (minute), മണിക്കൂർ (hour)
- അലാറം + വെക്ക്/സെറ്റ്/ഇടൂ → set alarm; ടൈമർ → timer
- നാളെ (tomorrow), ദിവസവും (every day), weekday names
  തിങ്കൾ ചൊവ്വ ബുധൻ വ്യാഴം വെള്ളി ശനി ഞായർ

AM/PM rule when the speaker gives no marker (the "set an alarm for 7" case):
pick whichever of 7:00 and 19:00 comes **soonest in the future**. This matches
what people expect and is easy to explain.

### 2. System voice intents (`VoiceIntentActivity.kt`) — new file

A tiny transparent, no-UI activity registered in the manifest for the standard
Android clock actions, so Assistant and other voice apps can drive the app:

- `android.intent.action.SET_ALARM` (EXTRA_HOUR, EXTRA_MINUTES, EXTRA_MESSAGE,
  EXTRA_DAYS, EXTRA_VIBRATE, EXTRA_SKIP_UI)
- `android.intent.action.SET_TIMER` (EXTRA_LENGTH)
- `android.intent.action.SHOW_ALARMS`, `SHOW_TIMERS`
- `android.intent.action.DISMISS_ALARM`, `SNOOZE_ALARM`

It writes straight to the repository (alarm/timer), schedules it through
`AlarmScheduler` / `TimerEngine`, shows a short toast, then finishes. When the
voice app sends no time, or `EXTRA_SKIP_UI` is false, it opens `MainActivity` on
the Alarms (or Timer) tab instead. If the spoken text arrives only as
`EXTRA_MESSAGE`-style free text, it is fed through `VoiceCommandParser`.

### 3. In-app microphone button

- A mic icon in the Alarms screen top bar and the Timer screen top bar.
- Tapping it launches `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` through an
  activity-result launcher. Because the recognition runs in the system speech
  app, the app does **not** need the RECORD_AUDIO permission.
- Language sent with the intent comes from a new setting (below). All returned
  candidate strings are tried until one parses.
- Result is shown in a small confirm dialog: what was heard, what will be
  created, and Save / Cancel. Unrecognised speech shows the heard text plus a
  few example phrases in the chosen language.
- If the device has no speech recogniser, the mic button shows a toast saying so.

### 4. Setting

`AppPrefs`: `voiceLanguage` = `AUTO` | `EN` | `ML` (default AUTO → device locale,
falling back to `en-IN`). Exposed in Settings as a three-way choice
"Voice command language".

### 4b. Telling the user about the Malayalam voice model

Malayalam speech-to-text only works if the device's speech app has a Malayalam
voice model installed. The app must say this clearly instead of just failing.

**Checking what the device supports** — a new `VoiceLanguageSupport.kt` helper:
- Sends the `RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS` ordered broadcast and
  reads `EXTRA_SUPPORTED_LANGUAGES`.
- Result is one of `SUPPORTED`, `NOT_SUPPORTED`, `UNKNOWN` (some speech apps do
  not answer the broadcast — we must not claim "not supported" in that case).
- Cached in memory for the session; re-checked when Settings is opened, because
  the user may install the language pack and come back.

**Where the user is told** — three places, in plain language:

1. **In Settings, under the language choice.** A permanent helper line:
   > "Malayalam voice commands need the Malayalam voice model on your phone."

   When the check says `NOT_SUPPORTED`, this becomes a warning-coloured card:
   > "Malayalam speech is not installed on this phone. Voice commands will be
   > heard in English until you add it. Tap to open voice input settings."

   Tapping opens `Settings.ACTION_VOICE_INPUT_SETTINGS` (falling back to the
   app-details screen of the speech app if that action is missing), so the user
   can download the language. This card is checked/refreshed each time Settings
   opens, so it disappears once the model is installed.

2. **The first time the user picks Malayalam.** A one-off dialog explaining the
   requirement, with "Open voice settings" and "OK" buttons. Shown only when the
   check is `NOT_SUPPORTED` or `UNKNOWN`, and only once (an `AppPrefs` flag).

3. **When a Malayalam attempt fails.** The "could not understand" dialog adds a
   line when the language is Malayalam and support is not confirmed:
   > "Your phone may not have the Malayalam voice model. You can still type the
   > alarm, or add Malayalam in voice input settings."

   with the same "Open voice settings" button.

All three strings go in `res/values/strings.xml` (with a Malayalam
`res/values-ml/strings.xml` copy) rather than being hard-coded.

### 5. Tests

`app/src/test/.../VoiceCommandParserTest.kt` — table-driven tests over ~40
English and Malayalam phrases, including the next-upcoming AM/PM rule.

## Files

New:
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/VoiceCommandParser.kt`
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/VoiceIntentActivity.kt`
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/VoiceLanguageSupport.kt`
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/VoiceCommandUi.kt` (mic button + confirm dialog + language-missing notice)
- `app/src/main/res/values-ml/strings.xml`
- `app/src/test/java/in/sreerajp/chronotune_smart_clock/VoiceCommandParserTest.kt`

Changed:
- `app/src/main/AndroidManifest.xml` — register `VoiceIntentActivity` with the
  clock intent filters; add a `<queries>` entry for the speech recogniser.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmsScreen.kt` — mic button.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/TimerScreen.kt` — mic button.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ClockAppScreen.kt` — let a
  voice command switch tabs (alarm command → Alarms, timer command → Timer).
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/MainActivity.kt` — accept an
  "open this tab" extra from `VoiceIntentActivity`.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ClockViewModel.kt` —
  `applyVoiceCommand(cmd)` that routes to the existing add/show functions.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/AppPrefs.kt` — voice language pref.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/SettingsScreen.kt` — the setting UI
  plus the "Malayalam voice model missing" notice.
- `app/src/main/res/values/strings.xml` — the new user-facing strings.

## Notes / limits

- Malayalam speech-to-text quality depends on the device's recogniser. Where the
  model is missing the mic still works and hears English, and the app now says so
  (see 4b) instead of silently mis-hearing.
- The support check can answer `UNKNOWN` on speech apps that ignore the language
  broadcast. In that case the app words the notice as "may not have" rather than
  stating it as fact.
- No network or cloud service is used; parsing is fully offline string matching.

## Verification

- `./gradlew testDebugUnitTest` for the parser tests.
- `./gradlew assembleDebug` for a clean build.
- Manual: say "set an alarm for 7" to Assistant with ChronoTune as the clock app;
  tap the mic in the Alarms tab and speak both an English and a Malayalam phrase.
- Manual: on a phone without the Malayalam voice model, pick Malayalam in Settings
  and confirm the warning card, the one-off dialog, and the "Open voice settings"
  button all appear and work.
