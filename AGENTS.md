# AGENTS.md — ChronoTune Smart Clock

This file is read by AI agents and LLM coding assistants (Gemini, Antigravity, Cursor, Windsurf, Codex, OpenDevin, etc.) at the start of every session in this repository.
Read it before making any change. See the docs table below for full detail.

---

## Project identity

| Field | Value |
|-------|-------|
| App name | ChronoTune Smart Clock |
| Type | Advanced time management app with smart alarms, world clock, stopwatch, multi-timers, procedural ambient scheduler, and offline voice commands |
| Platform | Android (minSdk 24, targetSdk 36, compileSdk 36.1) |
| Package / namespace | `in.sreerajp.chronotune_smart_clock` |
| Kotlin | 2.2.10 |
| Compose BOM | 2026.05.01 |
| AGP | 8.13.2 |
| JDK | 11 |
| State management | ViewModel + StateFlow (UDF architecture) |
| Navigation | Single-Activity bottom navigation tab switching in `MainActivity` |
| Database | Room Database (`AppDatabase`) |
| Orientation | Both (Portrait and Landscape supported) |
| Connectivity | Fully offline — zero INTERNET permission, zero telemetry |

---

## Read these docs before working

| Document | Read when |
|----------|-----------|
| [docs/architecture.md](docs/architecture.md) | Changing structure, screens, state, services, audio synthesis, models, repositories |
| [docs/security.md](docs/security.md) | Touching permissions, logging, storage, export/import, manifest |
| [docs/release_process.md](docs/release_process.md) | Building a release, versioning, signing, release checklist |
| [docs/features.md](docs/features.md) | Understanding detailed feature specifications and module requirements |
| [docs/guidelines/kotlin_project_engineering_standard.md](docs/guidelines/kotlin_project_engineering_standard.md) | Any code change — layers, naming, testing standards |
| [docs/GUIDELINES_MANIFEST.md](docs/GUIDELINES_MANIFEST.md) | The shared Kotlin guidelines index |

> If a doc is copied into this project's own `docs/`, the local copy wins over the submodule template.

---

## Package naming

One identifier everywhere: the source package, the build `namespace`, and the `applicationId` are all `in.sreerajp.chronotune_smart_clock`.

Note that `in` is a Kotlin keyword, so it must be backticked in source — in every `package` line and in every import of our own code:

```kotlin
package `in`.sreerajp.chronotune_smart_clock
import `in`.sreerajp.chronotune_smart_clock.data.repository.ClockRepository
```

---

## Hard rules (must follow — these override convenience)

1. **100% Offline & Private:** Never request `android.permission.INTERNET` or add network SDKs. All data stays local on device.
2. **Open Source Only:** No proprietary or source-available SDKs. All dependencies must be permissive open-source (Apache 2.0, MIT, OFL).
3. **Never Crash on Bad Input:** Every audio parser, JSON backup importer, and voice command parser must have safe fallback paths that never throw unhandled exceptions.
4. **Alarm & Timer Reliability:** Foreground services (`AlarmService`, `ChronometerService`) and `AlarmManager.setAlarmClock` must be used for exact time wakeups so OEM battery killers do not silence alarms.

---

## Architecture rules

- **Layout:** Single-module MVVM under `app/src/main/java/in/sreerajp/chronotune_smart_clock/`:
  - `config/` — `AppConfig` + `ConfigService` (About screen single source of truth).
  - `data/` — Room database (`AppDatabase`), DAOs, entities/models (`Alarm`, `TimerItem`, etc.), and `ClockRepository`.
  - `ui/` — Composable screens, reusable components, and theme.
  - `audio/` — Real-time PCM audio synthesis engine (`AudioEngine`).
  - `widget/` — Home screen clock widgets and renderers.
- **Layer boundaries:** Composables must not directly access Room DAOs, raw SQLite, or SharedPreferences. Always route through `ClockViewModel` / `ClockRepository`.
- **Dependency direction:** Composables → ViewModel → Repository → Room Database / AudioEngine → Models.
- **Models are immutable:** Use `data class` with `copy()`. Never mutate entities in place.

---

## Build & run commands

```bash
./gradlew assembleDebug              # build debug APK
./gradlew assembleRelease            # build release APK (signed via keystore.properties)
./gradlew testDebugUnitTest          # run JVM / Robolectric unit tests
./gradlew lint                       # Android lint (must remain at 0 errors)
```

---

## Build types / flavors

| Build type | App ID | Display name | Signing |
|------------|--------|--------------|---------|
| `debug` | `in.sreerajp.chronotune_smart_clock` | ChronoTune Smart Clock | Debug / local keystore |
| `release` | `in.sreerajp.chronotune_smart_clock` | ChronoTune Smart Clock | Release keystore via `keystore.properties` |

---

## Signing / keystore

- Keystore file: `keystore.jks` at project root. Alias: `chronotune-smart-clock`.
- Configure via `keystore.properties` (git-ignored — never commit):
  ```properties
  storePassword=<password>
  keyPassword=<password>
  keyAlias=chronotune-smart-clock
  storeFile=keystore.jks
  ```
- `.gitignore` includes `keystore.properties`, `*.jks`, `*.keystore`.

---

## Security rules

- Never log sensitive user data, alarm labels, or file paths — even in debug builds.
- Minimal permissions: only request permissions with explicit user justification (`POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `READ_CALENDAR`).
- `android:allowBackup="true"` with custom backup rules for secure user-initiated JSON export/import.

---

## String resources

- All user-visible UI text comes from `res/values/strings.xml` via `stringResource()` or `context.getString()`.
- Multilingual translations are maintained in `res/values-ml/strings.xml` for Malayalam.
- Literals are permitted only for internal logs, non-UI exception messages, asset paths, and JSON keys.

---

## Code style / naming

- Files: `PascalCase.kt` for classes/Composables, `camelCase.kt` for extensions.
- Classes: `PascalCase`; functions & properties: `camelCase`; constants: `SCREAMING_SNAKE_CASE`.
- Package names: `lowercase` (with backticked `` `in` ``).
- Prefer `val` over `var`, `data class` for domain models, `sealed interface` for UI states.

---

## Testing rules

- Unit tests live under `app/src/test/java/in/sreerajp/chronotune_smart_clock/`.
- Test data transformations, time calculation algorithms, voice parsing, and backup serialization.
- Run `./gradlew testDebugUnitTest` to verify all test suites before committing.

---

## Dependency constraints

- Blocked (never add): Any networking libraries (Retrofit, OkHttp, Ktor-client), analytics trackers, proprietary ad networks.
- Allowed: Jetpack libraries (Compose, Room, Lifecycle), Kotlin Coroutines, Robolectric, JUnit 4.

---

## Where things live

```
AGENTS.md            # AI agents / LLM instructions
CLAUDE.md            # Claude Code native project rules
docs/                # living design and process documentation
plans/               # one plan per change (relative paths only)
change_log/          # one log per implemented change
app/src/main/        # app source code, assets, and resources
app/src/test/        # unit tests (JVM / Robolectric)
app/src/androidTest/ # instrumented tests
```

---

## Workflow rules (mandatory — from global rules)

Every change follows plan-before-changing and log-after-changing:

1. **Plan before changing.** Write a full plan to `plans/` named `yyyymmdd_hhMMss_<short-slug>.md` with a `**Status:**` line, the files to change, the issue, and the fix. Then **STOP and get explicit approval** before editing/creating/deleting any project file (other than the plan). A question or ambiguous reply is not approval.
2. **Log after changing.** After implementing, write a change log to `change_log/` named `yyyymmdd_hhMMss_<short-slug>.md` describing what changed and referencing its plan.
3. **Relative paths & privacy only.** `plans/` and `change_log/` files are committed and may become public on the internet. They MUST use relative repository paths only (never absolute system paths like `C:\...`, `l:\...`, or `file:///...`). They MUST NOT contain any **local system details** — OS user name, computer/host name, home or drive-letter paths, network share names, LAN/internal IP addresses, local server URLs with ports, device serial numbers, personal email addresses — or any secret (API keys, tokens, passwords, keystore passphrases, credentials, PII). Write them as if a stranger will read them; nothing should reveal the machine they came from.

---

## Communication rules

- **Always use simple English.** Write all responses, plans, change logs, and explanations in plain, simple English. Short sentences, common words. Explain any jargon you must use.

---

## What AI agents must always / never do

**Always:**
- Read this file and referenced docs first.
- Keep About screen values sourced from `app/src/main/assets/config/app_config.json` via `ConfigService`.
- Run unit tests (`./gradlew testDebugUnitTest`) after making changes.
- Keep `MainActivity` thin by delegating business logic to `ClockViewModel` and service components.

**Never:**
- Add network permissions or internet-dependent libraries.
- Hardcode user-facing strings directly inside Composables.
- Bypass the Repository layer to perform direct Room database operations in UI components.
- Put local machine absolute paths or sensitive data into `plans/` or `change_log/`.
