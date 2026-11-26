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
- **About & Licenses** — In-app screen listing the AGPLv3 license, source link and every bundled component

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

Requirements: JDK 21 (or 25) and an Android SDK with platform 37 and build-tools 36.

```bash
./gradlew -p build-logic test       # content compiler tests (YAML/Markdown -> SQLite FTS5)
./gradlew :app:testDebugUnitTest    # unit, integration and Robolectric Compose UI tests
./gradlew :app:assembleDebug        # compiles content/ into the APK automatically
./gradlew :app:installDebug         # install on a connected device
./gradlew testAll                   # both test suites
```

The build works with an empty `content/books/` (the app shows an empty state). Tests never read
`content/`; they use fixtures in `app/src/test/resources/fixtures/`.

## Architecture

- **`build-logic/`** — Gradle plugin `terlingua.content`. A per-variant `compile<Variant>TerlinguaContent`
  task (configuration-cache safe, cacheable) validates `content/`, renders Markdown to a JSON block tree
  with build-time syntax highlighting, writes `terlingua/terlingua-books.db` (with an FTS5 index) and
  copies referenced images. Output is wired in with `variant.sources.assets.addGeneratedSourceDirectory`,
  so nothing generated is committed.
- **`app/`** — single-activity Compose app. `domain/` (models, `BookProgress` schema v1, merge/codec
  logic), `data/` (bundled-SQLite content reader, Room 3 progress store, DataStore settings, SAF/FileProvider
  I/O), `ui/` (Navigation 3 destinations, `ViewModel` + `StateFlow` per screen), `feedback/` (central
  ripple + haptic + `SoundPool` feedback).
- The content database is opened with the **bundled SQLite driver** so FTS5 is available on every API level.

## Keyboard, mouse and large screens

- Tablets, unfolded foldables, Chromebooks and desktop windows show the table of contents and the
  reader side by side (`NavigableListDetailPaneScaffold`); state survives folding and resizing.
- `Esc` goes back, `←`/`→` move between sections in the reader, `Tab` moves focus with visible focus rings,
  and mouse hover/wheel work everywhere. No orientation or aspect-ratio locks are declared.

## Content Format

All book content lives under `content/` and is compiled into a read-only SQLite FTS5 database
inside the APK during the Gradle build.

### Book and chapter manifests

```yaml
# content/books/<book-slug>/book.yml
title: Blazor Basics
author: terlingua-team          # author id (file name in content/authors), or a list
summary: Learn the fundamentals.
date: 2026-01-15                # optional; books dated after the UTC build date are skipped
updated: 2026-03-01             # optional
tags: [dotnet, web]
image: images/books/blazor.png  # optional, relative to content/
draft: false                    # optional

# content/books/<book-slug>/<NN>-<chapter-slug>/chapter.yml
title: Getting Started
summary: Optional chapter summary.
draft: false
```

Images in Markdown (`![alt](../../../images/x.png)` or `/images/x.png`) are bundled when they resolve to a
file under `content/`; remote images are shown as links because the app has no network access.

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

### Creating a signing key (once)

```bash
keytool -genkeypair -v -keystore release.keystore -alias terlingua -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.keystore > release.keystore.base64   # paste into the KEYSTORE_BASE64 secret
```

Keep the keystore safe: Obtainium (and Android) only accept updates signed with the same key.

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

## Feedback settings

Buttons and quiz answers combine Material ripples, haptics (`HapticFeedbackType`, honouring the system
touch-feedback setting) and tiny bundled WAV cues played through `SoundPool`. Sound and haptics can be
toggled independently in Settings; sounds never play in silent, vibrate or Do Not Disturb modes, or while
the app is in the background.

## Technology & Licensing

All dependencies are free and open-source software compatible with **GNU AGPLv3** — no Google Play Services, no proprietary SDKs, and no payment required, ever.

Licensed under **GNU AGPLv3**. See [LICENSE](LICENSE).
