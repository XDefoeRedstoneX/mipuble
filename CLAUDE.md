# CLAUDE.md

Guidance for working in this repository.

## What this is

**mipuble** — a native Android EPUB reader written in Kotlin + Jetpack Compose.
A portfolio project; code quality, architecture clarity, and testability are
first-class goals (not just "does it run").

## Architecture

Clean Architecture with three layers across two Gradle modules:

```
:domain      # Pure Kotlin/JVM module — entities, repository interfaces,
             # use-cases, sorting. No Android dependency; the module boundary
             # enforces it. JSR-330 @Inject only (Hilt wiring stays in :app).
:app
├── data     # Room, DataStore, Drive/fake remote sources, repository impls
└── ui       # Compose screens, ViewModels, theme  (presentation)
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
./gradlew assembleDebug                     # build
./gradlew testDebugUnitTest :domain:test    # unit tests (app + domain)
./gradlew lintDebug                         # lint
```

CI (`.github/workflows/ci.yml`) runs lint + unit tests + assembleDebug on every push.

## Roadmap

- **Phase 0** ✅ Foundation & tooling.
- **Phase 1** ✅ Library screen + natural sorting (the `NaturalOrderComparator`).
  Room-backed library seeded with demo books; sorting happens in the domain
  layer (`ObserveLibraryUseCase`) because SQLite can't express natural order.
- **Phase 2** ✅ Custom EPUB parser (container.xml → OPF → spine) + WebView reader.
  Parser is pure JVM (DOM, no Android) so it's unit-tested without Robolectric.
  Reader is the MVI showcase screen; chapters stream from the zip via a WebView
  `shouldInterceptRequest` bridge — the book is never unpacked to disk. Books
  imported via SAF; a bundled `sample.epub` is seeded on first launch.
- **Phase 3** ✅ Reader UX + precise ±1% brightness via window attributes; themes via DataStore.
  Brightness overrides `Window.attributes.screenBrightness` only while reading and
  restores the system default on exit. Themes/font/line-spacing persist in DataStore;
  theme + spacing apply by injecting an override stylesheet into the served HTML
  bytes (JS stays disabled), font via `WebView.textZoom`. Stepping/clamping rules
  live in the pure `ReaderSettingsBounds` (unit-tested).
- **Phase 4** ✅ Categories (colors) + drag-and-drop persisted ordering.
  Categories are a Room table (name + packed ARGB color); deleting one
  un-assigns its books atomically. Drag-and-drop is hand-rolled on
  `LazyGridState.layoutInfo` (`ReorderableGrid.kt`) — only available in
  "My order" sort with no filter, persisted via one transactional write.
- **Phase 5** ✅ Metadata-only remote library + on-demand download/eviction.
  `RemoteLibrarySource` abstracts the backend: a real `DriveRemoteLibrarySource`
  (Drive REST v3 over OkHttp, behind `DriveAuthProvider`) and a
  `FakeRemoteLibrarySource` (bundled sample) bound by default so it runs
  offline. Sync inserts metadata-only books (filePath=null, remoteId set);
  download streams bytes to disk with progress (in-memory `StateFlow`);
  evict deletes the file but keeps metadata. Migration 3→4.
- **Phase 6** ✅ Multi-module split, test coverage, a11y, polish.
  `domain` extracted to a pure-JVM `:domain` Gradle module (boundary now
  enforced, not conventional). LibraryViewModel covered by JVM tests via a
  `MainDispatcherRule`; README gained a from-zero install guide.

## Backlog (deferred — do not implement until asked)

UI/perf:
- Bookmark (category) sidebar scroll is laggy — needs a performance look at the drawer's category list.
- `AssignCategoryDialog` ("add book to bookmark") needs a scroll container — a long category list overflows and can't be reached.

Dedup:
- Duplicates that share the same name OR the same volume but have different
  translators are NOT deduplicated. Current dedup is content-hash OR
  `series|volume` key; revisit so differently-translated copies of the same
  volume collapse.

Volume numbering:
- Support decimal/comma volume numbers (e.g. "Vol 1.5"). Volume is currently an
  `Int` in `TitleNormalizer`/`Book`/dedup key — needs to allow fractional
  chapters/side-volumes.

Manual rename (follows from decimal volumes):
- A manual "Rename book" flow: select a book → popup that splits the title into
  separate [series name] | [volume number] fields → enter → with an
  "add to bookmark" check. (Like the review sheet but explicit name+volume split,
  invokable on demand per book.)

Reader:
- Page count should reflect the EPUB's real page count, not a converted/derived
  number.
- Swipe (paged) page-turn is broken: the `PAGED_CSS` column layout renders the
  whole chapter as 1–2 lines that run off-screen horizontally, consuming the
  entire page count until it's no longer scrollable. Paged mode has been fragile
  across several attempts (`PAGED_CSS` in `ReaderThemeColors.kt` + paging/scroll
  logic in `ReaderScreen.kt`) — needs a proper, device-tested rework, not another
  blind CSS tweak.
