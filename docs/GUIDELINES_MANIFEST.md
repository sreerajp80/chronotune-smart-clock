# Kotlin Guidelines — Manifest

This is a **single, portable pointer file**. It carries the relative paths to the shared Kotlin
guideline documents. Copy this file into your Kotlin app's `docs/` folder, and add the guidelines
repository as a Git submodule at `docs/guidelines/` to keep your app consistent.

The guidelines live at:

```
docs/guidelines/
```

## How to use this file

1. Copy `GUIDELINES_MANIFEST.md` into the `docs/` folder of your Kotlin app.
2. Add the guidelines repository as a Git submodule at `docs/guidelines/`:
   ```bash
   git submodule add https://github.com/sreerajp80/Kotlin_Guidelines docs/guidelines
   ```
3. Reference it from the app's mandatory root `CLAUDE.md` and `AGENTS.md` (e.g. "Follow the guidelines listed in
   `docs/GUIDELINES_MANIFEST.md`.").
4. Open the documents at the relative paths below to read the guidelines.

> **Master vs. local copy.** The paths below point to the **submodule** copies. If a document has
> instead been copied directly into this app's own `docs/` folder, the **local copy wins** for that app;
> use the submodule path only when there is no local copy.

## Core documents

| Core documents | Path (from `docs/`) | What it is |
|---|---|---|
| Folder-structure guideline | `guidelines/guideline.md` | My personal cross-app conventions: About-screen config, the release keystore rules, and the baseline source package layout. **Source of truth for keystore rules.** |
| Engineering standard | `guidelines/kotlin_project_engineering_standard.md` | The master, project-agnostic rulebook — rules that apply to *every* app (structure, UI, accessibility, performance, database, logging, security, CI, git, Definition of Done). |
| Architecture blueprint | `guidelines/architecture.md` | A per-project architecture blueprint template. Fill it in with one app's actual decisions. |
| Build configuration guide | `guidelines/kotlin_build_configuration_guide.md` | Technical reference for Gradle Kotlin DSL, product flavors, build types, signing, R8/ProGuard, and version catalogs. |
| Release process | `guidelines/release_process.md` | Step-by-step release runbook — versioning, hardening, signing, build commands, distribution, rollback. |
| Security blueprint | `guidelines/security.md` | A per-project security blueprint template — threat model, sensitive-data inventory, crypto design, OWASP checklist. |
| CLAUDE.md writing guideline | `guidelines/CLAUDE_MD_GUIDELINE.md` | Mandatory guideline for creating and maintaining the project-root `CLAUDE.md` for every Kotlin project (**MUST**). |
| AGENTS.md writing guideline | `guidelines/AGENTS_MD_GUIDELINE.md` | Mandatory guideline for creating and maintaining the project-root `AGENTS.md` for other LLMs and AI agents (**MUST**). |
| Docs folder guideline | `guidelines/DOCS_FOLDER_GUIDELINE.md` | How to create files in a project's `docs/` folder (local vs submodule, naming rules, file anatomy, catalog of recognized doc types). |
| Index / README | `guidelines/README.md` | The overview of the whole guideline set and where to start. |

## Plain-English explainers

Dense documents have a matching explainer that describes, in simple English, what the document
says and how to use it. Open the explainer first if a document looks hard.

| Explainer | Path (from `docs/`) |
|---|---|
| Architecture explainer | `guidelines/docs/architecture_README.md` |
| Engineering standard explainer | `guidelines/docs/kotlin_project_engineering_standard_README.md` |
| Build configuration explainer | `guidelines/docs/kotlin_build_configuration_guide_README.md` |
| Release process explainer | `guidelines/docs/release_process_README.md` |
| Security explainer | `guidelines/docs/security_README.md` |

## Which documents apply to my app (by profile)

The engineering standard defines three applicability profiles. Profiles stack — pick the ones
that fit your app, then read across the row. A small internal tool is `Core Baseline` only; a
shipped password manager is in all three.

| Profile | Applies to | Documents in force |
|---|---|---|
| `Core Baseline` | Every app | Root `CLAUDE.md` (via `CLAUDE_MD_GUIDELINE.md`, **MUST**); Root `AGENTS.md` (via `AGENTS_MD_GUIDELINE.md`, **MUST**); `guideline.md`; Core Baseline rules of `kotlin_project_engineering_standard.md`; `architecture.md`; `DOCS_FOLDER_GUIDELINE.md` |
| `Production App Extension` | Apps shipped to real users / QA / stores | The above **plus** `release_process.md`, `kotlin_build_configuration_guide.md` (if using flavors), and the Production sections of the engineering standard |
| `Sensitive Data Extension` | Apps handling secrets, PII, health, finance, or local encrypted stores | The above **plus** `security.md` and the Sensitive Data sections of the engineering standard |

## Where to start

- **Writing / maintaining project root `CLAUDE.md` & `AGENTS.md` (MUST)** — follow `CLAUDE_MD_GUIDELINE.md` and `AGENTS_MD_GUIDELINE.md`.
- **Starting a new app** — read `guideline.md` and `kotlin_project_engineering_standard.md`.
- **Structuring project `docs/` files** — follow `DOCS_FOLDER_GUIDELINE.md`.
- **Designing one app's structure** — fill in `architecture.md`.
- **Setting up build configuration** — see `kotlin_build_configuration_guide.md`.
- **Shipping a release** — follow `release_process.md`.
- **Handling sensitive data** — fill in `security.md`.
