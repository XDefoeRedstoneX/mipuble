# mipuble

A native Android EPUB reader (mipub for short), built in Kotlin with Jetpack Compose.

This is a portfolio project that deliberately tackles four pain points that
mainstream reader apps tend to get wrong:

| Feature | The pain it fixes |
| --- | --- |
| **Super-precise brightness** | OS sliders are coarse and floor too high for night reading. mipuble overrides the window brightness in exact **±1%** steps — only while reading — and restores the system default on exit. |
| **Natural sorting** | Most apps shelve series as *Vol 1, Vol 10, Vol 11, Vol 2…* (lexicographic). mipuble compares digit runs numerically, so *Vol 2* comes before *Vol 10*. The broken sort is kept as a menu option so you can see the difference. |
| **Custom organization** | Color-coded categories you define (palette or any hue), plus a **drag-and-drop** "My order" arrangement that's persisted — hand-rolled on `LazyGridState`, no library. |
| **Storage efficiency** | A **metadata-only** remote library: sync lists cloud books at ~zero local cost; bytes download **on demand** with live progress, and "Remove download" evicts the file while keeping the book on your shelf. |

---

## Install & run (from zero)

You don't need any Android experience to run this. Pick one of the two paths.

### Path A — Android Studio (recommended)

1. **Install Android Studio** (free): <https://developer.android.com/studio>.
   Run the installer and accept the defaults — it bundles everything (JDK,
   Android SDK, emulator).
2. **Get the code**, either way works:
   - With git: `git clone https://github.com/XDefoeRedstoneX/mipuble.git`
   - Without git: on the GitHub page, **Code ▸ Download ZIP**, then unzip.
3. **Open it**: Android Studio ▸ *Open* ▸ select the `mipuble` folder (the one
   containing `settings.gradle.kts`). The first open triggers a **Gradle sync**
   that downloads dependencies — give it a few minutes.
4. **Pick somewhere to run it**:
   - **Your phone**: enable Developer Options (Settings ▸ About phone ▸ tap
     *Build number* 7 times), then turn on **USB debugging** (Settings ▸
     Developer options), plug the phone in over USB, and accept the trust
     prompt on the phone.
   - **Or an emulator**: in Android Studio, *Device Manager* ▸ *Create
     device* ▸ pick any phone (e.g. Pixel 8) ▸ pick a recent system image ▸
     finish.
5. **Press the green ▶ Run button.** The app installs and launches with a
   seeded demo library, a readable sample book, and a fake "cloud" library to
   try download-on-demand — no sign-in or setup needed.

### Path B — command line only (no Android Studio)

Requirements: **JDK 17+** and the **Android SDK command-line tools**.

```bash
# 1. Install a JDK (Ubuntu/Debian example; on macOS: brew install temurin@17)
sudo apt-get install openjdk-17-jdk

# 2. Install the Android SDK command-line tools
#    Download "Command line tools only" from https://developer.android.com/studio#command-line-tools-only
mkdir -p ~/android-sdk/cmdline-tools
unzip commandlinetools-*.zip -d ~/android-sdk/cmdline-tools
mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest

# 3. Accept licenses and install the needed packages
export ANDROID_HOME=~/android-sdk
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

# 4. Build the APK
git clone https://github.com/XDefoeRedstoneX/mipuble.git
cd mipuble
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk

# 5. Install on a USB-connected phone (USB debugging enabled, see Path A step 4)
$ANDROID_HOME/platform-tools/adb install app/build/outputs/apk/debug/app-debug.apk
```

No phone handy? You can also copy `app-debug.apk` to a phone any way you like
(cloud drive, cable, messaging app to yourself) and open it there — Android
will ask you to allow installs from that source.

### Using the app

- The library is pre-seeded with demo books; **The mipuble Sample** opens for
  real — tap it to read.
- **+** imports any `.epub` from your phone's storage.
- **↻** syncs the (built-in demo) cloud library; dimmed covers with a badge
  are remote — tap to download with live progress, long-press ▸ *Remove
  download* to reclaim the space.
- Long-press a book to file it into a color category; sort menu ▸ *My order*
  to drag books around; the reader's ⚙ has themes and the ±1% brightness
  control.

### Connecting real Google Drive (optional, for developers)

The cloud library ships against an offline fake so everything works with zero
setup. The real Drive client (`DriveRemoteLibrarySource`, REST v3) is included
but inert until you create OAuth credentials of your own. The complete
beginner-friendly walkthrough — Google Cloud Console, SHA-1, code wiring,
troubleshooting — is in **[docs/GOOGLE_DRIVE_SETUP.md](docs/GOOGLE_DRIVE_SETUP.md)**.

---

## Tech stack

| Concern | Choice |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture — `:domain` is a pure-JVM Gradle module; data/UI live in `:app` |
| State | MVVM (`StateFlow<UiState>`) everywhere; the Reader is deliberately MVI as a contrast piece |
| DI | Hilt (KSP) |
| Async | Coroutines + Flow |
| Local storage | Room (4 schema versions with migrations) + DataStore |
| EPUB engine | Custom parser (container.xml → OPF → spine) + WebView streaming straight from the zip, JS disabled |
| Cloud | Google Drive REST v3 over OkHttp, behind a pluggable `RemoteLibrarySource` |
| Tests | 58 JVM unit tests — parser, natural sort, settings bounds, use cases, ViewModel |
| CI | GitHub Actions: lint + unit tests + assemble on every push |

## Building & testing

```bash
./gradlew assembleDebug              # build the APK
./gradlew testDebugUnitTest :domain:test   # run all unit tests
./gradlew lintDebug                  # Android lint
```

## Bundled fonts

The reader ships three optional typefaces, all licensed under the
[SIL Open Font License 1.1](https://openfontlicense.org):
[Literata](https://github.com/googlefonts/literata) (© The Literata Project),
[Inter](https://github.com/rsms/inter) (© Rasmus Andersson), and
[Atkinson Hyperlegible](https://www.brailleinstitute.org/freefont/)
(© Braille Institute of America).

## Project status

- [x] **Phase 0** — Foundation & tooling (buildable skeleton, CI)
- [x] **Phase 1** — Library screen + natural sorting
- [x] **Phase 2** — EPUB parsing & reader rendering
- [x] **Phase 3** — Reader UX + precise brightness
- [x] **Phase 4** — Custom organization (colors + drag-and-drop)
- [x] **Phase 5** — Download on demand (Google Drive)
- [x] **Phase 6** — Multi-module split, tests, a11y, polish

See `CLAUDE.md` for per-phase implementation notes.
