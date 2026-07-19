# Voice intent integration (English + Malayalam)

Implements [plans/20260719_134910_voice-intent-integration.md](../plans/20260719_134910_voice-intent-integration.md).

The app can now be driven by speech in two ways, and it tells the user plainly when the
phone cannot understand Malayalam.

## New files

**`ui/VoiceCommandParser.kt`**
Turns a spoken sentence into a `VoiceCommand` (`SetAlarm`, `SetTimer`, `ShowAlarms`,
`ShowTimers`, `DismissAlarm`, `SnoozeAlarm`, `Unknown`). Plain Kotlin, no Android classes,
fully offline.

- English: "set an alarm for 7", "wake me up at 6:30 am", "7 o'clock", "quarter past six",
  "half past five", "quarter to seven", "19:45", "set a timer for 1 hour 30 minutes",
  "half an hour", "wake me in 20 minutes", "stop the alarm", "snooze for 10 minutes",
  "show my alarms".
- Malayalam: Malayalam numerals (൭) and number words (ഏഴ്, ഒമ്പത്…), half-past forms
  (ഏഴര), time-of-day words (രാവിലെ, ഉച്ചയ്ക്ക്, വൈകിട്ട്, രാത്രി, പുലർച്ചെ),
  മണി/മിനിറ്റ്/മണിക്കൂർ, അലാറം, ടൈമർ, നിർത്തൂ, weekday names and ദിവസവും.
- Repeats: every day, weekdays, weekends, named days, in both languages.
- When the speaker gives no AM/PM, the app picks whichever reading comes soonest. Asking
  for "7" at 10 pm gets 7 am; asking at 9 am gets 7 pm.

**`ui/VoiceIntentActivity.kt`**
A no-UI activity registered for the standard Android clock actions, so Google Assistant and
other voice apps can drive this app instead of the stock clock: `SET_ALARM`, `SET_TIMER`,
`SHOW_ALARMS`, `SHOW_TIMERS`, `DISMISS_ALARM`, `SNOOZE_ALARM`. It writes the alarm/timer,
schedules it, toasts a confirmation and finishes. `EXTRA_DAYS` (Calendar day numbers) is
converted to the app's 1=Mon..7=Sun. When no time is supplied it falls back to the parser,
and failing that it opens the app on the right tab.

**`ui/VoiceLanguageSupport.kt`**
Asks the phone's speech app which languages it has, through the
`GET_LANGUAGE_DETAILS` ordered broadcast. Answers `SUPPORTED`, `NOT_SUPPORTED` or
`UNKNOWN` — the third state matters, because some speech apps never reply and the app must
not claim a language is missing when it simply could not find out. Also opens the phone's
voice input settings, trying three system screens before falling back to the speech app's
own details page.

**`VoiceCommandUi.kt`**
The microphone button. Listening is done by the system speech app through `RecognizerIntent`,
so the app needs **no RECORD_AUDIO permission**. All the recogniser's ranked guesses are
tried until one parses. What was understood is shown in a confirm dialog before anything is
created. If nothing parsed, a dialog shows the heard text plus example phrases.

**`res/values-ml/strings.xml`** — Malayalam translations of all new user-facing text.

**`app/src/test/.../VoiceCommandParserTest.kt`** — 21 tests over English and Malayalam
phrases, including the soonest-time rule. All pass.

## Telling the user about the Malayalam voice model

Malayalam speech-to-text needs a voice model many phones ship without. Three notices:

1. **Settings** — a helper line under the language chips. When the check returns
   `NOT_SUPPORTED` it becomes a red card: "Malayalam speech is not installed… what you say
   will be heard in English", tappable to open voice input settings. Re-checked every time
   Settings opens, so it disappears once the model is installed.
2. **First time Malayalam is picked** — a one-off dialog with an "Open voice settings"
   button, shown only when support is not confirmed, remembered in prefs so it never nags.
3. **When a Malayalam command fails to parse** — the "did not get that" dialog adds the
   likely cause and the same settings button.

Wording changes with certainty: "does not have" when the recogniser confirmed it, "may not
have" when the answer was unknown.

## Changed files

- `AndroidManifest.xml` — registered `VoiceIntentActivity` with the six clock actions;
  added `<queries>` for the speech recogniser (needed for package visibility on Android 11+).
- `AppPrefs.kt` — new `VoiceLanguage` enum (Auto/English/Malayalam) with the BCP-47 tag
  mapping, the stored preference, and the one-off-notice flag.
- `SettingsScreen.kt` — new `VoiceLanguageCard` with the language chips and the notices.
- `AlarmsScreen.kt`, `TimerScreen.kt` — microphone button in the top bar, plus an
  `onNavigateTab` callback.
- `ClockAppScreen.kt` — accepts an `initialTab` and lets a spoken command switch tabs, so a
  timer spoken on the Alarms screen lands where the user can see it.
- `MainActivity.kt` — accepts `EXTRA_OPEN_TAB` in both `onCreate` and `onNewIntent`, plus
  named tab constants.
- `ClockViewModel.kt` — `applyVoiceCommand()`, routing to the existing add/dismiss/snooze
  functions so voice-made alarms use the user's normal defaults.
- `res/values/strings.xml` — the new user-facing strings.

## Verification

- `./gradlew testDebugUnitTest --tests '*VoiceCommandParserTest*'` — 21 tests pass.
- `./gradlew assembleDebug` — builds clean.
- `ExampleRobolectricTest` fails, but it fails the same way with these changes stashed. It
  is a pre-existing problem: Robolectric does not support the project's compileSdk. Not
  touched by this work.
- Not yet done on a real device: speaking to Assistant, the in-app microphone, and the
  Malayalam-missing notices. These need hardware.
