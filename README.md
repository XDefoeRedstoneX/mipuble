# mipuble

A native Android EPUB reader (mipub for short), built in Kotlin with Jetpack Compose.

This is a portfolio project that deliberately tackles four pain points that
mainstream reader apps tend to get wrong:

- **Super-precise brightness** — manual ±1% increments that override the system default, in-app only.
- **Natural sorting** — fixes the classic "Vol 1, Vol 10, Vol 2" lexicographic bug.
- **Custom organization** — user-defined category colors and drag-and-drop sort orders.
- **Storage efficiency** — a metadata-only library with on-demand download from Google Drive, so a 7 GB+ collection costs near-zero local space until you actually read a book.

## Tech stack

| Concern | Choice |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture (data / domain / presentation) + MVVM |
| DI | Hilt |
| Async | Coroutines + Flow |
| Local storage | Room + DataStore |
| EPUB engine | Custom parser (container/OPF/spine) + WebView rendering |
| Cloud | Google Drive REST API |
| CI | GitHub Actions |

## Building

```bash
./gradlew assembleDebug
```

Requirements: JDK 17, Android SDK (compileSdk 35), minSdk 26.

## Project status

Built in phases. See `CLAUDE.md` for the roadmap and current progress.

- [x] **Phase 0** — Foundation & tooling (buildable skeleton, CI)
- [x] **Phase 1** — Library screen + natural sorting
- [x] **Phase 2** — EPUB parsing & reader rendering
- [x] **Phase 3** — Reader UX + precise brightness
- [ ] **Phase 4** — Custom organization (colors + drag-and-drop)
- [ ] **Phase 5** — Download on demand (Google Drive)
- [ ] **Phase 6** — Hardening & polish
