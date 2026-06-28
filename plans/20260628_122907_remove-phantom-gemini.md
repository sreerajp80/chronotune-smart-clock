# Remove phantom Gemini AI integration

**Status:** completed

## The issue

The README advertises the music scheduler as "powered by the Gemini AI API," but no
network call to Gemini (or any LLM) exists anywhere in the codebase. The Music Scheduler
is a purely local feature backed by Room (`MusicSchedule`, `MusicScheduleDao`,
`ClockViewModel.addMusicSchedule/...`). Gemini appears only as dead scaffolding:

- a README marketing claim and a `GEMINI_API_KEY` / `.env` setup step,
- a `.env.example` file whose only contents is the placeholder key,
- the `secrets` Gradle plugin, configured solely to inject that key (the signing config
  reads `local.properties` via its own `secret()` helper, **not** this plugin),
- a commented-out `firebase-ai` dependency,
- an authorship credit "Gemini 3.5 Flash and Claude Opus 4.8" in `app_config.json`.

Rather than implement a real Gemini integration, the decision is to **remove the false
advertising and the dead scaffolding** so the README matches what the app actually does.

## Plan for the fix

1. **README.md**
   - Line 3: drop the clause "powered by the Gemini AI API" (keep the rest of the
     feature list).
   - Remove the setup step that tells users to create `.env` and set `GEMINI_API_KEY`
     (step 4); renumber the remaining steps.

2. **.env.example** — delete (its only content is the Gemini placeholder key).

3. **.gitignore** — remove the now-moot `.env` ignore line (and its leftover blank line).

4. **app/build.gradle.kts**
   - Remove the `alias(libs.plugins.secrets)` plugin line.
   - Remove the `secrets { ... }` configuration block (and its comment).
   - Remove the commented-out `// implementation(libs.firebase.ai)` line.

5. **gradle/libs.versions.toml**
   - Remove the `secrets` plugin entry and the `secretsGradlePlugin` version.
   - Remove the `firebase-ai` library entry.

## Explicitly left alone (not false advertising)

- **app_config.json** `author: "Gemini 3.5 Flash and Claude Opus 4.8"` and the matching
  defaults in `MainActivity.kt` — these are authorship credits (who built the app), not a
  runtime feature claim. Leave as-is unless you'd prefer them removed too.
- **firebase-bom** platform in `build.gradle.kts` — currently no Firebase library is
  actually used, so it is also dead, but it is not Gemini-specific. Out of scope here;
  can be cleaned up separately if you want.

## Files to be changed

- `README.md`
- `.env.example` (delete)
- `.gitignore`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`

## Verification

- Confirm no remaining references: `grep -ri gemini` and `grep -ri 'GEMINI_API_KEY\|\.env'`
  across source/config (build artifacts excluded).
- Sanity-build with Gradle to confirm removing the `secrets` plugin doesn't break the
  build (signing still resolves via the `secret()` helper).
