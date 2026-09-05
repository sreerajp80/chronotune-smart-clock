# Architecture — ChronoTune Smart Clock

This document describes the system architecture, component design, data persistence, and audio engine pipeline for ChronoTune Smart Clock.

**Read first:** [../AGENTS.md](../AGENTS.md) · [../CLAUDE.md](../CLAUDE.md) · [features.md](features.md) · [guidelines/architecture.md](guidelines/architecture.md)

---

## 1. Scope

- Product: ChronoTune Smart Clock
- Repository type: Android application (single-module `app`)
- Engineering standard profiles in force:
  - `Core Baseline`
  - `Production App Extension`
- Platform: Android (minSdk 24, targetSdk 36, compileSdk 36.1)

---

## 2. System Overview

ChronoTune Smart Clock is engineered using **100% Kotlin**, Jetpack Compose, Material Design 3, Room database, real-time procedural audio synthesis, and persistent Android background services. It operates entirely offline without network requirements.

The application is structured into five core functional modules:
1. **World Clock (`Clock`)**: Global timezone tracking with IANA catalog search and Canvas analog previews.
2. **Smart Alarms (`Alarms`)**: Intelligent recurring and dated alarms with holiday awareness, pause windows, cognitive wake-up challenges, and volume ramp.
3. **Precision Stopwatch (`Stopwatch`)**: Centisecond stopwatch with split/lap tracking and persistent state.
4. **Multi-Timer Engine (`Timer`)**: Concurrent countdown timers driven by dual timebases (monotonic elapsedRealtime + RTC currentTimeMillis).
5. **Automated Music Scheduler (`Schedules`)**: Timed audio sessions with composite playlists, equal-power crossfading, and RMS loudness normalization.

---

## 3. Layered Architecture

The app follows a unidirectional data flow (UDF) MVVM pattern:

```
[ UI Layer (Compose Screens & Components) ]
                   │
                   ▼ (User Events / Intent Calls)
          [ ClockViewModel ]
                   │
                   ▼ (StateFlow & Coroutine Dispatchers)
          [ ClockRepository ]
           │              │
           ▼              ▼
  [ Room Database ]  [ AudioEngine / Services ]
```

### Layer Responsibilities

| Layer | Directory | Responsibilities |
|---|---|---|
| **Config** | `config/` | `AppConfig` data model and `ConfigService` loader (About screen single source of truth). |
| **Data** | `data/` | Room database (`AppDatabase`), entity definitions (`Alarm`, `TimerItem`, `SpecialDay`, etc.), DAOs, and `ClockRepository`. |
| **UI** | `ui/` & root package | Composable screens (`AlarmsScreen`, `TimerScreen`, `StopwatchScreen`, `WorldClockScreen`, `MusicSchedulerScreen`, `SettingsScreen`), components, and theme. |
| **Audio** | `audio/` | Procedural additive arpeggiated synthesis, ADSR envelopes, crossfading, and MediaCodec RMS analysis (`AudioEngine`). |
| **Services & Receivers** | `ui/` (Services/Receivers) | Foreground services (`AlarmService`, `ChronometerService`), wakeup broadcast receivers (`AlarmReceiver`, `BootReceiver`). |
| **Widgets** | `widget/` | Canvas-rendered analog watch face and digital home-screen widget providers. |

---

## 4. Audio Engine Architecture

The procedural synthesis engine (`AudioEngine`) creates real-time 16-bit PCM stereo melodies directly via Android `AudioTrack`:
- **Additive Synthesis**: Fundamental frequency + 4 harmonic overtones (1.0, 0.45, 0.22, 0.12, 0.06).
- **ADSR Envelope**: Smooth attack, decay, sustain, and release curves to avoid speaker pops and clipping clicks.
- **Stereo Chorus**: 1.004 right-channel detuning creates rich acoustic depth.
- **Equal-Power Crossfading**: Trigonometric `cos`/`sin` blend curves for smooth playlist track transitions without volume drops.

---

## 5. Persistence & Data Flow

1. **Room Entities**: Managed in `AppDatabase` with automatic schema migrations.
2. **Special Day Registry**: `SpecialDayRegistry` provides in-memory caching of calendar holidays and workdays for zero-latency alarm trigger evaluation.
3. **Backup & Restore**: `BackupManager` provides versioned JSON import/export (`FORMAT_VERSION = 1`) with primary key reassignment and collision avoidance.
