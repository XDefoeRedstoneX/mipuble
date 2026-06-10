# CLAUDE.md

Guidance for working in this repository.

## What this is

**mipuble** — a native Android EPUB reader written in Kotlin + Jetpack Compose.
A portfolio project; code quality, architecture clarity, and testability are
first-class goals (not just "does it run").

## Architecture

Clean Architecture with three layers, currently in a single Gradle module
(`:app`) organized by package. Extraction into multiple Gradle modules is
planned for Phase 6.

```
com.mipuble
├── data         # Room, DataStore, Drive, repositories (implementations)
├── domain       # Entities, repository interfaces, use-cases (pure Kotlin, no Android)
└── ui           # Compose screens, ViewModels, theme  (presentation)
```

Rules of thumb:
- `domain` depends on nothing Android-specific and is the most heavily unit-tested layer.
- `ui` talks to `domain` use-cases; ViewModels expose an immutable `UiState` via `StateFlow`.
- `data` implements `domain` repository interfaces; nothing outside `data` knows about Room/Drive.

## State pattern

MVVM by default: each screen has a ViewModel exposing one immutable `UiState`
through `StateFlow`. One screen (the Reader) will be built MVI-style as a
deliberate contrast piece.

## Conventions

- Kotlin official code style; 4-space indent.
- Dependencies live in the version catalog (`gradle/libs.versions.toml`) — no hardcoded versions in build files.
- DI via Hilt; annotation processing via KSP.
- Prefer `StateFlow`/`Flow` over `LiveData`.

## Build & test

```bash
./gradlew assembleDebug      # build
./gradlew testDebugUnitTest  # unit tests
./gradlew lintDebug          # lint
```

CI (`.github/workflows/ci.yml`) runs lint + unit tests + assembleDebug on every push.

## Roadmap

- **Phase 0** ✅ Foundation & tooling.
- **Phase 1** Library screen + natural sorting (the `NaturalOrderComparator`).
- **Phase 2** Custom EPUB parser (container.xml → OPF → spine) + WebView reader.
- **Phase 3** Reader UX + precise ±1% brightness via window attributes; themes via DataStore.
- **Phase 4** Categories (colors) + drag-and-drop persisted ordering.
- **Phase 5** Google Drive metadata-only library + on-demand download/eviction.
- **Phase 6** Multi-module split, broad test coverage, a11y, polish.
