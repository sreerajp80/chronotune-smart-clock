# ChronoTune Smart Clock - Comprehensive Feature Specification & Documentation

## App Description
**ChronoTune Smart Clock** is an advanced, feature-rich Android time-management ecosystem and clock application engineered with 100% Kotlin, Jetpack Compose, Material Design 3, Room database, real-time procedural audio synthesis, and persistent Android system services. Designed from the ground up to embody **universal accessibility, neurodiverse inclusivity, low-vision comfort, regional linguistic equity, heavy-sleeper anti-oversleeping protection, and privacy-first offline independence**, ChronoTune merges high-precision timekeeping with modern aesthetic design and deep OS integration.

ChronoTune eliminates the limitations of conventional system clocks by offering an inclusive, all-in-one suite anchored by **five primary user navigation modules**:

1. **World Clock (`Clock`)**: Global timezone tracking across world cities with full IANA catalog search, instant GMT relative offsets, date badges, and hero analog watch face previews.
2. **Smart Alarms (`Alarms`)**: Intelligent recurring and dated alarms featuring holiday/compensatory workday awareness (`ALL_DAYS`, `SKIP_HOLIDAYS`, `WORKDAYS_ONLY`), date-range pause windows (vacation mode), start dates, skip next occurrence, per-alarm volume/vibration/tones, and time-until-ring toasts.
3. **Precision Stopwatch (`Stopwatch`)**: High-precision centisecond stopwatch (`MM:SS.cc`) with concurrent split and lap tracking, reboot/process persistence (`StopwatchPrefs`), and plain-text summary export.
4. **Multi-Timer Engine (`Timer`)**: Concurrent countdown timers driven by dual timebases (monotonic `elapsedRealtime` UI countdowns + RTC `System.currentTimeMillis` background triggers), reusable presets, and status bar +1 minute extensions.
5. **Automated Music Scheduler (`Schedules`)**: Automated audio playback sessions with composite playlists blending procedural ambient melodies and local files, equal-power crossfading (0–12s), and MediaCodec RMS loudness normalization.

Supporting these five modules are **six core cross-cutting foundation pillars**:
* **Inclusive Accessibility & Neurodiverse Design**: 5-tier dynamic text scale multiplier (0.85x to 1.30x), 7 bundled font families (Inter, Poppins, Nunito, Lato, Roboto Slab, Merriweather, Default), ARGB color customizer with automatic background legibility math (`onColorFor`, `deriveContainer`), 3D tactile controls (`Button3D`), and cognitive wake-up challenges (Math, Sequence Memory, Phrase Typing).
* **Multilingual & Regional Equity**: 100% offline, zero-latency natural language voice parsing for English (`en-IN`) and Malayalam (`ml-IN`), handling localized numeral expressions (e.g., "ഏഴര", "ഏഴു മണിക്ക്"), relative delay calculations ("in 20 minutes"), smart AM/PM inference, and regional holiday/workday integration.
* **Anti-Oversleeping & Safety Snooze Suite**: Cognitive challenges paired with a vertical swipe-up drag gesture (`SwipeUpToSnooze` with snap-back physics), fixed or progressive snooze duration decay, max snooze limit caps, and remaining snooze counters.
* **Procedural Audio Synthesis & Acoustic Customization**: 6 procedurally generated 16-bit PCM stereo melodies (additive synthesis with 4 overtones, ADSR envelope, 1.004 stereo chorus detune), multi-tab tone picker (Built-in, Ringtones, Device Files), equal-power playlist crossfading (0–12s), MediaCodec RMS normalization, and 20s gradual volume ramp.
* **Deep System Integration, Widgets & OEM Resilience**: Self-describing versioned JSON backup/restore (Merge/Replace), Android Assistant voice intents (`SET_ALARM`, `SET_TIMER`, etc.), device calendar holiday importing (`READ_CALENDAR`), Canvas-rendered analog and digital home screen widgets, Android 13/14+ notification & overlay permission handling, dynamic permission diagnostic dashboard, and in-app fallback ringing overlay (`AlarmRingingOverlay`).
* **100% Privacy-First Offline Independence**: Operates entirely offline with zero cloud dependencies, analytics trackers, or network permission requirements, safeguarding complete user privacy.

---

## Complete Feature Matrix

### 1. Smart Alarms & Advanced Scheduling
- **Flexible Recurrence**: Configure alarms for specific days of the week (Monday through Sunday) or schedule single-shot occurrences.
- **Custom Alarm Labels**: Assign descriptive titles to organize alarms by purpose (e.g., "Morning Workout", "Medication", "Standup Meeting").
- **Dated One-Shot Alarms & Future Start Dates**: Schedule single alarms for specific future dates or configure repeating alarms to begin on a designated future date.
- **Date-Range Pause Windows**: Temporarily pause alarms between specified start and end dates (e.g., vacation, leave, or travel mode) without altering or deleting established alarm schedules.
- **Skip Next Occurrence**: One-tap action to skip only the immediate upcoming ring of a repeating alarm while leaving future schedules intact.
- **Holiday & Compensatory Workday Awareness**:
  - `ALL_DAYS`: Ring according to standard day-of-week schedule, ignoring holiday flags.
  - `SKIP_HOLIDAYS`: Automatically suppress alarm ringing on designated calendar holidays.
  - `WORKDAYS_ONLY`: Automatically suppress on holidays and force-ring on compensatory workdays, even if that day of the week is normally off-schedule.
- **Auto-Silence Timer**: Automatically silences ringing alarms after a user-configured duration (1, 5, 10, 15 minutes, or ring indefinitely).
- **Per-Alarm Audio, Volume & Vibration**: Configure unique volume levels (0%–100%), vibration patterns, and select between procedural synth melodies, system ringtones, or custom local device audio files for each individual alarm.
- **Exact Time-to-Ring Toast Confirmation**: Instant visual confirmation announcing exact remaining time until the next ring, dynamically calculated considering holidays, pause windows, start dates, and skip states.
- **System Alarm Clock Privilege & Status Bar Integration**: Uses `AlarmManager.setAlarmClock` with `AlarmClockInfo.showIntent` to grant user-facing alarm clock privileges, displaying the upcoming alarm in the Android system status bar and enabling direct jump back to MainActivity.
- **Full-Screen Ringing Overlay & Lockscreen Activity**: Dedicated full-screen ringing UI (`AlarmActivity`) utilizing `FLAG_TURN_SCREEN_ON`, `setShowWhenLocked`, keep-screen-on flags, and notification heads-up demotion upon activity launch.
- **OEM Background Launch Fallback Overlay**: In-app fallback overlay (`AlarmRingingOverlay`) rendered within `ClockAppScreen` when background activity launch or lockscreen display is restricted by OEM power managers (e.g., Xiaomi, Samsung, Huawei), ensuring active alarms can always be safely dismissed or snoozed.
- **Foreground Service Architecture**: Ringing audio playback and notification lifecycle are managed by `AlarmService` (`mediaPlayback` foreground service type), securing Background Activity Launch (BAL) exemptions and preventing process termination during playback.
- **Android 13/14+ Permission Resilience**: Proactively requests runtime notification permissions (`POST_NOTIFICATIONS` on Android 13+), checks full-screen notification authorization (`canUseFullScreenIntent()` via `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` on Android 14+), and verifies overlay display permissions (`canDrawOverlays()` via `ACTION_MANAGE_OVERLAY_PERMISSION`) to guarantee lockscreen elevation and full-screen alarm takeovers.
- **Reboot & Time-Change Survival**: `BootReceiver` automatically reschedules all active alarms, music schedules, and running timers upon device boot (`BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`), application update (`MY_PACKAGE_REPLACED`), and manual system time or timezone changes (`TIME_SET`, `TIMEZONE_CHANGED`).

### 2. Anti-Oversleeping Wake-Up Challenges
Interactive cognitive challenges designed to verify mental alertness before an alarm can be dismissed, preventing accidental shutoffs for heavy sleepers:
- **Math Problems**: Solve arithmetic equations with 3 difficulty tiers (`EASY`, `MEDIUM`, `HARD`) via an on-screen numeric keypad with instant error feedback.
- **Number Sequence Memory Test**: Tap shuffled number tiles in ascending order (1..N) to test cognitive focus (`EASY` = 4 tiles, `MEDIUM` = 6 tiles, `HARD` = 9 tiles).
- **Phrase Typing**: Type-to-dismiss challenge requiring accurate retyping of random words or inspiring morning phrases with auto-focused text entry.
- **Configurable Challenge Rounds**: Require completing between 1 and 10 consecutive challenge rounds before dismissal.
- **Snooze Protection**: Active challenge settings carry over to snoozed re-rings, preventing users from bypassing wake-up tasks via snooze.
- **Standard Tap-to-Dismiss**: `NONE` mode provides a classic single-tap dismissal when challenges are turned off.

### 3. Snooze Controls & Limits
- **Swipe-Up Snooze Safety Action**: Snoozing requires a deliberate upward vertical drag gesture (`SwipeUpToSnooze` with snap-back physics and threshold logic) to prevent accidental snoozing when reaching for Dismiss.
- **Configurable Snooze Duration**: Customizable snooze intervals (5, 10, 15, 20, or 30 minutes presets, or 1–60 minutes custom stepper).
- **Max Snooze Limit Cap**: Restrict maximum allowed snoozes per alarm (1 to 10 or unlimited) to halt perpetual snoozing.
- **Remaining Snooze Counter**: Real-time display on the ringing overlay showing exact snoozes remaining (e.g., `SNOOZE (5 MIN) · 2 LEFT`). Automatically transitions to a single Dismiss action once the cap is reached.
- **Snooze Modes**:
  - `FIXED`: Maintains a constant snooze interval for every snooze cycle.
  - `PROGRESSIVE`: Dynamically shortens snooze durations on subsequent snoozes to urge waking up (e.g., 10m base → 10m, 5m, 3m, 3m, 2m, 2m, 1m).

### 4. Custom Audio & Procedural Synthesized Tone Engine
- **Procedurally Synthesized Melodies**: 6 built-in procedural arpeggiated audio tracks synthesized in real time via PCM 16-bit stereo synthesis (`AudioEngine`):
  - *Morning Breeze* (C Major arpeggio)
  - *Cosmic Shimmer* (A Major arpeggio)
  - *Ocean Zen* (F Major arpeggio)
  - *Digital Alarm* (Alternating classic buzzer)
  - *Retro Chiptune* (Upbeat 8-bit scale)
  - *Deep Lofi Lounge* (Soothing G Major arpeggio)
- **Multi-Tab Tone Picker Pane**: Dedicated selection pane featuring 3 category tabs (Built-in, System Ringtones, Local Device Files), search filter, 10-second live audio preview play/stop controls, and clear selection indicators.
- **Custom Audio & System Ringtone Support**: Select system ringtones or pick local device audio files with persistable URI permission tracking.
- **Acoustic Engineering & Buffer Architecture**: Multi-harmonic additive synthesis (fundamental + 4 overtones: 1.0, 0.45, 0.22, 0.12, 0.06 normalized), ADSR envelope shaping (attack, decay, sustain, release) to eliminate audio clipping clicks, 1.004 right-channel detuning for stereo chorus depth, and dual `AudioTrack` buffer modes (`MODE_STREAM` for dynamic arpeggiated synthesis and `MODE_STATIC` for zero-latency synth playlist voices).
- **System Alarm Stream Volume Pinning**: Pins `STREAM_ALARM` to maximum during alarm ringing so the in-app volume slider serves as the master volume controller, automatically restoring system volume afterwards.
- **Gradual Volume Fade-In**: Optional 20-second linear volume ramp-up starting from an audible floor (5% target) to prevent sudden loud auditory shocks.
- **Playlist Crossfading & Loudness Normalization**: Equal-power (constant loudness) or linear blend curves (0–12s) for scheduled playlists, alongside MediaCodec prefix RMS loudness normalization for custom audio files.

### 5. Countdown Timers
- **Multiple Concurrent Timers**: Run, name, and monitor multiple independent countdown timers simultaneously with distinct label and audio tone assignments.
- **Dual Monotonic & Wall-Clock Timebases**: Utilizes `android.os.SystemClock.elapsedRealtime` (monotonic clock immune to system clock changes for smooth UI countdowns) paired with RTC `System.currentTimeMillis` (for reliable AlarmManager background execution).
- **Persistent Service Execution**: Powered by `ChronometerService` (`specialUse` foreground service type), active timers survive backgrounding, screen lock, process termination, and device reboots.
- **Non-Colliding Request Code Architecture**: Timer alarm triggers are assigned dedicated request code offsets (`RING_ID_OFFSET = 200000`) to guarantee zero ID collision with alarms or music schedules.
- **One-Tap Quick Presets**: Save and edit custom timer presets (e.g., "Tea 3 min", "Workout 15 min") with custom sorting (`sortOrder`).
- **Rich Live Notifications**: Real-time status bar notification featuring interactive Pause, Resume, Cancel, and quick **+1 Minute** addition actions (`TimerAddMinuteReceiver`).
- **Post-Finish +1 Minute Extension**: Quick action on finished timer alerts to immediately restart countdown for an extra minute.

### 6. Precision Stopwatch
- **Split & Lap Timing**: High-precision stopwatch supporting concurrent split time and lap time recording with detailed lap history lists.
- **Centisecond / Millisecond Precision**: Display formatted down to hundredths of a second (`MM:SS.cc`).
- **Plain-Text Lap Export & Sharing**: Export formatted lap summaries (total elapsed time, split times, lap durations) as plain text via system share intent (`Intent.ACTION_SEND`).
- **Live Status Bar Chronometer Notification**: Status bar controls allowing Start, Pause, Lap, and Reset directly from notifications driven by `ChronometerService`.
- **Process & Reboot Persistence**: Continuous state persistence via `StopwatchPrefs`, surviving backgrounding, app restarts, and system reboots.

### 7. World Clock & Timezone Manager
- **Global Timezone Tracking**: Track local time across global cities with digital clocks and hero analog watch face previews.
- **Full IANA Timezone Catalog Search**: Search and select any IANA timezone from a clean catalog filtered by geographic regions (e.g., `America/New_York`, `Europe/London`, `Asia/Kolkata`).
- **Instant Relative Offset Comparison**: Displays time differences relative to local device time zone alongside relative date badges ("Today", "Yesterday", "Tomorrow").

### 8. Automated Music & Ambient Scheduler
- **Scheduled Audio Sessions**: Automatically trigger audio playback at designated times and durations (e.g., 15, 30, 45, 60 minutes).
- **Composite Playlists**: Mix built-in procedural ambient melodies with local custom audio files into composite sequential playlists.
- **Crossfading & Normalization Integration**: Configurable crossfade durations (0–12s) with Equal Power (`cos`/`sin` curve) or Linear blend curves and automatic MediaCodec prefix RMS loudness level-matching across files.

### 9. Special Days & Holidays Registry
- **Central Special Days Registry**: Unified database of calendar holidays (`HOLIDAY`) and compensatory workdays (`WORKING_DAY`) affecting alarm schedules.
- **Manual Special Days**: Add, edit, or delete custom holiday dates and compensatory workdays.
- **Device Calendar Event Importer**: Import holidays and all-day events directly from device calendars (`READ_CALENDAR`). Syncs up to 18 months ahead (`IMPORT_MONTHS_AHEAD = 18`), excludes timed non-all-day events to safeguard morning alarms, and caps multi-day events at 31 days (`MAX_DAYS_PER_EVENT = 31`).
- **Visual Status & Origin Badges**: UI badges distinguish upcoming vs past days, and identify manual entries vs imported calendar events.
- **Thread-Safe In-Memory Registry**: `SpecialDayRegistry` provides asynchronous pre-loading and zero-latency synchronous lookups for background broadcast receivers.

### 10. Offline Voice Commands & Accessibility Integration
- **100% Offline Natural Language Parser**: Hands-free control using spoken commands parsed via grammarless offline regex and substring matching (`VoiceCommandParser`).
- **Multilingual Support**: Supports English (`en-IN`) and Malayalam (`ml-IN`), parsing digits, spelled numbers, regional Malayalam expressions (e.g., "7", "seven", "ഏഴര", "ഏഴു മണിക്ക്"), clock times, relative delay durations ("in 20 minutes", "20 മിനിറ്റ് കഴിഞ്ഞ്"), intelligent AM/PM meridiem inference (auto-selecting the nearest upcoming 12h vs 24h slot when unstated), and repeat day patterns ("everyday", "weekdays", "weekends", specific days of the week).
- **Floating Interactive Voice Sheet**: Dedicated floating microphone sheet (`VoiceCommandUi`) featuring real-time speech feedback, action prompts, and language selection toggle (`Automatic`, `English`, `Malayalam`).
- **6 Core Voice Actions**: Spoken commands parsed into structured data payloads:
  - `SetAlarm`: Hour, minute, label, repeat days
  - `SetTimer`: Duration in milliseconds, label
  - `ShowAlarms`: Jump to alarm list
  - `ShowTimers`: Jump to timer list
  - `DismissAlarm`: Stop active alarm ring
  - `SnoozeAlarm`: Postpone active alarm ring with optional minutes
- **Voice Model Availability & Diagnostics**: `VoiceLanguageSupport` checks device speech recognizer capabilities, detects if the Malayalam voice model is installed, and offers a direct link to system voice settings.
- **6 System Voice Intents**: Natively responds to Android Assistant voice intents via `VoiceIntentActivity` (`SET_ALARM`, `SET_TIMER`, `SHOW_ALARMS`, `SHOW_TIMERS`, `DISMISS_ALARM`, `SNOOZE_ALARM`).

### 11. Home Screen Widgets
- **Digital Clock Widget**: Home screen digital clock display featuring 5-tier backdrop transparency settings (0% glass to 100% solid, mapped to pre-baked drawables `widget_background_a00` .. `a100`).
- **Canvas-Rendered Analog Clock Widget**: Interactive analog watch face rendered via native Android `Canvas` (`AnalogClockFaceRenderer`):
  - Radial gradient dial backdrop
  - 3-ring metallic bezel
  - 60 tick marks (quarter, hour, minute styling)
  - Cardinal hour numerals (12 / 3 / 6 / 9) with dark drop-shadow halos
  - AM/PM and Date complication badges
  - Tapered hour and minute hands with ambient glow
  - Watch-style second hand with counterbalance and lollipop ring
- **Battery-Friendly Ticking**: Driven by `AlarmManager.setExact` with non-wakeup `ELAPSED_REALTIME`, providing smooth 1-second ticking while awake without battery drain during Doze.

### 12. Data Backup & Restore
- **Self-Describing Versioned JSON Backup**: Export and import all persistent application data (alarms, world clocks, music schedules, timer presets, and special days) using a self-describing, versioned JSON format (`FORMAT_VERSION = 1`) managed by `BackupManager`.
- **Primary Key Reassignment**: Primary keys are omitted on export and auto-reassigned on import to eliminate ID collisions.
- **Import Modes**: Supports `MERGE` (combine imported records with existing data) and `REPLACE` (clean table wipe before restore).

### 13. Inclusive UI, Personalization & Preferences
- **Modern Jetpack Compose Dark & Light Themes**: Modern glassmorphic design optimized for both day and night viewing.
- **3D Tactile Buttons (`Button3D`)**: Custom Material 3 button design featuring vertical gradients, top shine, bottom shading, and dynamic drop-shadows.
- **Custom ARGB Color Picker & Contrast Math**: Pick custom ARGB accent colors using an interactive color picker or select from 8 swatches (Vermillion, Blue, Teal, Violet, Gold, Sage, Pink, Slate) with dynamic contrast calculation (`onColorFor`) and M3 role derivation (`deriveContainer`, `deriveOnContainer`) to guarantee text legibility across themes.
- **7 Bundled Typography Families**: Select preferred typography from bundled OFL/Apache 2.0 fonts for personal taste or neurodiverse reading comfort (shipped as static Regular + Bold weights for minSdk 24 compatibility):
  - System Default
  - Inter
  - Poppins
  - Nunito
  - Lato
  - Roboto Slab
  - Merriweather
- **5-Tier Dynamic Text Scale Multiplier**: Fine-tune UI text scale across discrete steps (0.85x, 1.00x, 1.15x, 1.22x, 1.30x) for enhanced low-vision accessibility.
- **Structured 6-Section Settings Hub**: Navigation connecting to dedicated settings sub-pages:
  1. *About*: App metadata, system architecture specs, backup & restore operations.
  2. *Appearance*: Theme, accent color, typography family, font scale, week start day, voice language preference, widget transparency.
  3. *Permissions Dashboard*: Dynamic runtime permission status dashboard and live diagnostic logs (Notifications, Exact Alarm, Read Calendar, Ringtones).
  4. *Alarm Defaults*: Global fade-in toggle, default snooze length, default auto-silence limit, default max snooze count, default snooze style (Fixed/Progressive), default tone.
  5. *Holidays & Special Days*: Central special days management & calendar importer.
  6. *Music Scheduler*: Playlist crossfade toggle, session duration slider, blend curve selector, loudness normalization.
- **Global Alarm Creation Defaults**: Preferences to seed newly created alarms with standard default values.
- **Week Start & Time Format**: Custom week-start configuration (Monday or Sunday) and global 12h/24h time format toggles.
- **Zero-Telemetry Privacy Guarantee**: 100% local data processing without external tracking or mandatory internet connections.

---

## Technical Architecture & Engineering Specifications
- **Language & Core Architecture**: 100% Kotlin, Jetpack Compose, Material Design 3, Kotlin Coroutines, StateFlow, Unidirectional Data Flow (UDF) Architecture with `ClockViewModel` and `ClockRepository`.
- **Database Layer**: Room Database (`AppDatabase`) with 6 structured entities: `Alarm`, `TimerItem`, `TimerPreset`, `WorldClock`, `MusicSchedule`, and `SpecialDay`.
- **Services & Broadcast Receivers**:
  - `AlarmService`: Foreground service (`mediaPlayback` type) for audio playback, notification management, lockscreen activity launch, and BAL exemption.
  - `ChronometerService`: Foreground service (`specialUse` type) driving live stopwatch and running-timer notifications.
  - `AlarmReceiver`, `BootReceiver`, `AlarmDismissReceiver`, `AlarmSnoozeReceiver`, `TimerAddMinuteReceiver`.
- **Voice & Speech Recognition Pipeline**:
  - `VoiceIntentActivity`: System voice intent entry point (`SET_ALARM`, `SET_TIMER`, etc.).
  - `VoiceCommandParser`: Offline regex-based natural language parser for English & Malayalam speech.
  - `VoiceLanguageSupport`: Speech recognizer language query & system voice settings launcher.
  - `VoiceCommandUi`: Floating microphone overlay sheet.
- **Audio & Serialization Infrastructure**:
  - `AudioEngine`: Real-time PCM 16-bit stereo synthesis (`AudioTrack`), ADSR envelope shaping, stereo detuning, system alarm stream override (`STREAM_ALARM` pinning), MediaCodec prefix RMS loudness analysis, equal-power crossfading, and `MediaPlayer` integration.
  - `BackupManager`: Self-describing JSON serializer/deserializer with primary key reassignment and Merge/Replace import modes.
  - `CalendarHolidayImporter`: System calendar sync engine (`READ_CALENDAR`) for holiday import.
  - `SpecialDayRegistry`: Thread-safe in-memory cache for zero-latency background holiday resolution.
- **Widgets & Graphics Engine**: `AnalogClockWidgetProvider`, `DigitalClockWidgetProvider`, `AnalogClockFaceRenderer` (native Canvas rendering with radial gradient dial, metallic bezel, drop-shadow halos, and tapered hands), `WidgetPrefs`.
