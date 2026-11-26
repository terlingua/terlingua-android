# Terlingua Books (Android)

A free, open-source, 100% offline native Android reader and quiz companion for Terlingua Books,
distributed directly via GitHub Releases and Obtainium — no Google Play Store and no internet
connection required.

Built with **Kotlin**, **Jetpack Compose**, **Material 3 Adaptive**, **Navigation 3**, and
**Room 3 (SQLite + FTS5)** on **AGP 9.5+** and **Gradle 9.9+**. Targets **Android 8.0 (API 26)**
through **Android 17 (API 37)** across candy-bar phones, foldables, tablets, and notebook-style
Android PCs.

## Features

- **100% Offline & Private** — Zero network permissions (`INTERNET` and `ACCESS_LOCAL_NETWORK` are not declared); all books, authors, avatars, code highlighting, and quizzes are compiled into the APK at build time
- **Offline Full-Text Search** — Instant SQLite FTS5 search across all books, chapters, and sections
- **Interactive MCQs** — Single-answer (radio) and multiple-answer (checkbox) quizzes with immediate evaluation, retry, "Show answer", Markdown explanations, and sticky completion state
- **Adaptive Multi-Device UI** — Material 3 `ListDetailPaneScaffold` adapts seamlessly to phones, foldables, tablets, and desktop/Chromebook windows with full keyboard and mouse support
- **Themes** — System, Light, Dark, Sepia, Solarized Light, Solarized Dark, and High Contrast
- **Multisensory Feedback** — Material 3 visual ripples alongside optional haptic feedback and lightweight `SoundPool` audio cues (configurable in Settings and respectful of silent/DND mode)
- **Offline Progress Backup & Sharing** — Export/import portable `.terlingua-progress.json` files (`schemaVersion = 1`) via Android Share Sheet (messaging apps, email, Bluetooth, Quick Share, USB, Syncthing) or Storage Access Framework, plus air-gapped QR code transfer with conflict-free merging
- **Automated Releases** — Every push to `main` builds and signs a universal APK and publishes a full GitHub Release for Obtainium

## Repository Structure

```text
terlingua-android/
├── LICENSE                       # GNU AGPLv3
├── README.md
├── export.sh                     # LLM context exporter (file tree + source; excludes content/ bodies)
├── .github/
│   ├── dependabot.yml            # Weekly updates for Gradle dependencies and GitHub Actions
│   └── workflows/
│       ├── pr-check.yml          # Build + tests on pull requests
│       └── release.yml           # Build + tests + signed universal APK + GitHub Release on main
├── gradle/
│   ├── libs.versions.toml        # Version Catalog (latest stable FOSS dependencies only)
│   └── wrapper/
├── build-logic/                  # Pure JVM/Kotlin Gradle content compiler (Markdown/YAML -> SQLite FTS5)
├── content/                      # Manually maintained books, authors, and author images
│   ├── authors/
│   ├── images/authors/
│   └── books/
└── app/                          # Native Android app (Kotlin + Jetpack Compose)
```

## Quick Start

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Build debug APK (compiles content/ into SQLite FTS5 DB automatically)
./gradlew assembleDebug

# Install on a connected device
./gradlew installDebug
```

## Content Format

All book content lives under `content/` and is compiled into a read-only SQLite FTS5 database
inside the APK during the Gradle build.

### Directory Layout

```text
content/
├── authors/
│   └── terlingua-team.yml        # author profile
├── images/
│   └── authors/
│       └── terlingua-team.png    # avatar referenced by `avatar: images/authors/...`
└── books/
    └── blazor-basics/            # book slug (lowercase, digits, single hyphens)
        ├── book.yml              # title, author, summary, date, updated, tags, image, draft
        ├── 01-getting-started/   # NN-chapter-slug; number sets display order
        │   ├── chapter.yml       # title, summary, draft
        │   ├── 01-intro.md       # NN-subchapter-slug.md
        │   └── 02-setup.md
        └── 02-components/
            └── ...
```

Chapters and subchapters are ordered numerically by their prefix (`01`, `05`, `10` gaps are fine).
Drafts (`draft: true`) and future-dated books (`date` after the UTC build date) are skipped.

### Author Manifest (`content/authors/<id>.yml`)

```yaml
name: Terlingua Team
email: hello@terlingua.example
bio: Building free, open-source learning tools.
avatar: images/authors/terlingua-team.png
socials:
  github: terlingua
  mastodon: "@terlingua@mastodon.social"
```

### Subchapter Format (`NN-slug.md`)

```markdown
---
title: What is this concept?
questions:
  - id: runtime                   # optional, defaults to q1, q2…; keep stable once published
    prompt: Where does the code run?
    explanation: Shown after a correct answer or when revealed.   # optional
    options:
      - text: On a remote server
      - text: Locally on your device
        correct: true
  - id: properties
    prompt: Which statements are true?
    type: multiple                # optional: single | multiple
    options:
      - id: offline               # optional, defaults to a, b, c…
        text: It works without internet access
        correct: true
      - text: It requires an account
---
Markdown body…
```

- `type` is inferred when omitted: more than one `correct: true` option implies `multiple`.
- Set `type: multiple` explicitly to show checkboxes even when only one option is correct.
- Prompts, options, and explanations support Markdown (inline code, emphasis, links).
- Reader progress is keyed on question and option IDs—keep IDs stable once published.
- Invalid content (no correct option, fewer than 2 options, unnumbered files, missing `chapter.yml`, malformed YAML, duplicate IDs) fails the Gradle build with a clear error list.
- Quote option text that YAML would parse as booleans, numbers, or null (`"Yes"`, `"50"`), and avoid `yes`, `no`, `true`, `false`, and `null` as IDs.

## Offline Progress & Sharing

- A section is complete once every question has been answered correctly, or—if it has no questions—once it has been opened.
- Progress is stored locally on device using the `BookProgress` v1 schema (`schemaVersion = 1`).
- **Share / Backup:** Export a `.terlingua-progress.json` file via the Android Share Sheet (works over Signal, WhatsApp, Telegram, Email, Bluetooth, Quick Share, Syncthing, or local Files via Storage Access Framework).
- **QR Transfer:** Transfer a single book's progress air-gapped between two devices using a compressed QR code (open-source ZXing + CameraX; no Google Play Services).
- **Conflict-Free Merge:** Imports default to a safe merge (`answeredCorrectly` is sticky via logical OR, earliest `firstVisitedAt` is preserved, and newest `updatedAt` wins for last-visited location), with a preview diff before applying and an optional Replace mode.

## Releases & Obtainium

Every push to `main` runs tests, builds a **single universal signed release `.apk`**, and publishes a
**full GitHub Release** (never pre-release) with a `.sha256` checksum.

### Installing with Obtainium

1. Install [Obtainium](https://github.com/ImranR98/Obtainium).
2. Add a new app with source URL: `https://github.com/terlingua/terlingua-android`
3. Set the APK filter regex (if needed) to `.*\.apk$`.
4. Verify and pin the signing certificate SHA-256 fingerprint on first install using `apksigner` or Obtainium's built-in hash check:
   ```bash
   apksigner verify --print-certs terlingua-books-*.apk
   ```

### CI Signing Secrets

GitHub Actions (`release.yml`) expects these repository secrets:

- `KEYSTORE_BASE64` — Base64-encoded release `.jks`/`.keystore`
- `KEYSTORE_PASSWORD` — Keystore password
- `KEY_ALIAS` — Signing key alias
- `KEY_PASSWORD` — Signing key password

## LLM Context Export

`export.sh` writes `docs/llm/dump.txt`, a snapshot of the repository for LLM context:

- **`## File Tree`** — Indented tree of **all git-tracked files** (including `content/`)
- **`## Path only`** — Binary files outside `content/` and `docs/`
- **`## Files`** — Full text of source, Gradle config, workflows, and `README.md` (`content/` and `docs/` file bodies are excluded)

```bash
bash export.sh                         # whole repository (excluding content/ and docs/ bodies)
bash export.sh -- app/src              # narrow with git pathspecs
bash export.sh -f -o /tmp/book.txt -- content/books/my-book   # explicitly include full text of one book
```

## Technology & Licensing

All dependencies are free and open-source software compatible with **GNU AGPLv3** — no Google Play Services, no proprietary SDKs, and no payment required, ever.

Licensed under **GNU AGPLv3**. See [LICENSE](LICENSE).
