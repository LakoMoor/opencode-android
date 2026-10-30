# OpenCode for Android — local core, original UI, terminal

An Android shell that runs the **original OpenCode 2.2.2** inside an embedded Ubuntu ARM64 via PRoot. Its unmodified web UI opens in an Android WebView. Sessions, tools, permissions, files and the terminal are served by the OpenCode core on the phone itself. No computer, remote OpenCode or separate Termux is needed. For the LLM you can connect your own OpenAI-compatible server.

This is a port of the original web UI, not a redrawn simplified chat. Full parity with every desktop feature is not certified: Electron OS integrations, separate language servers, external MCP programs and tools need verification and suitable Linux ARM64 dependencies.

## Install and run

1. Install the APK from the [Releases](../../releases) page (or `app/build/outputs/apk/debug/app-debug.apk` after building) on an ARM64 device with Android 8.0+. A current Android System WebView is required.
2. Keep at least **1.5 GB** of free storage. The APK is fully self-contained: Ubuntu, the OpenCode core and the dev tools (Git, Python 3, curl, ripgrep, SSH, CA certificates) ship inside the APK already unpacked and are simply copied into the environment — no `apt`/`dpkg` and no internet needed. Internet is only required for your LLM provider.
   Measured on an Android 14 ARM64 emulator in airplane mode: first launch to a ready UI takes **68 s**, later launches about **9 s**. Node.js/npm are not bundled.
3. The core starts when the app opens; the original UI opens when you tap the button (or automatically after a start you triggered). Projects live locally in `/workspace` (the app's private directory).
4. Configure a provider and create a session in the original UI. The terminal runs in the same Linux environment: you can clone Git repositories and install more tools.
5. Android "Back" navigates history; after leaving to the start screen you can open the log or stop the core. Uninstalling the app removes local projects and sessions.

The core listens only on `127.0.0.1` on a random port and is protected by a random password. The UI is authorised with a one-time pairing code. An Android foreground service keeps the process alive and shows a notification with a stop button. Android/OEM may still kill the process under memory or power restrictions.

## Terminal

The start screen offers **OpenCode terminal (TUI)** — the original console interface of `opencode` — and a **Linux shell (bash)**. It is a real PTY (via the `pty-bridge.py` helper running on the bundled Python) with the xterm.js emulator, which ships in the APK and works offline. A key bar above the keyboard provides Esc, Tab, Ctrl, Alt, arrows, Home/End, PgUp/PgDn and common symbols. The terminal and the web UI share provider settings, projects (`/workspace`) and sessions. "Back" asks for confirmation because closing stops the process.

## Your own OpenAI-compatible server

On the start screen tap **Custom OpenAI-compatible server**:

- Enter the base URL, e.g. `http://192.168.1.10:1234/v1` (LM Studio) or `http://192.168.1.10:11434/v1` (Ollama). The `/chat/completions` path is added by the core; do not include it.
- Enter an API key if the server requires authorisation.
- Tap **Check and choose model**, or type the model ID manually if `/models` is not supported.
- Save. If the core was running, the app restarts it with the new `myserver` provider and the chosen model.

A configuration template is written to `/root/.config/opencode/opencode.json` using the OpenCode **v2** schema: `providers`, `package`, `settings`, `models`. Other settings in the file are preserved. All other providers, models and options are available in the original UI.

The model must support tool calling for agent features. On a physical phone `localhost` means the phone; use the LAN address for a server on your computer. In the Android emulator the host computer is reachable at `10.0.2.2`. Use HTTPS for external APIs: HTTP sends requests and the key in clear text.

In the Android Vault a copy of the settings is encrypted with AES-GCM using an Android Keystore key. The core's working configuration contains the key in a private Linux file accessible only to the app and its tools, like a regular OpenCode config. Backup and data transfer are disabled.

## Build

Requires JDK 17, Python 3, Android SDK platform 36, build-tools 35.0.0 and internet:

```sh
python3 scripts/prepare-runtime.py   # also runs scripts/prepare-tools.py (offline packages)
export ANDROID_HOME=/path/to/android/sdk
./gradlew assembleDebug testDebugUnitTest lint
```

`prepare-runtime.py` fetches the pinned upstream archives, verifies SHA-256 from `runtime/lock.json`, and prepares assets and ARM64 libraries. `prepare-tools.py` resolves Ubuntu 24.04 packages and their dependencies, verifies them against the archive index and bundles them as a pre-extracted overlay. Large binaries are excluded from Git. Archives are packed as `.tgz` so Android's Asset Manager does not alter their contents or names.

APK: `app/build/outputs/apk/debug/app-debug.apk` (about 180 MB). It is signed with the debug key and meant for testing. GitHub Actions repeats the preparation and build.

**Why targetSdk 28:** PRoot runs guest Linux ELF files from app-private storage. Android forbids such `exec` for apps with targetSdk ≥ 29. This is a deliberate limitation of this distribution scheme; the build does not meet Google Play requirements. The lint check `ExpiredTargetSdkVersion` is disabled with an explanation in Gradle; all other checks run.

## Tests

- Unit tests: HTTP/SSE with UTF-8, JSON fallback, Bearer auth, errors, cancellation, URL validation, tar extraction and path-traversal protection.
- The `RuntimeSmoke` integration test starts the **real** core and checks `/api/info`, the original HTML, shell, Git, Python, reading/writing a local file and creating a PTY. A test OpenAI-compatible model verifies the configuration and a streamed reply without a paid LLM.

```sh
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w ai.opencode.android.test/ai.opencode.android.RuntimeSmoke
```

Run the integration test on a dedicated test device/emulator: it restarts the core, temporarily changes the provider, creates a test session and the file `.android-smoke`.

## Known limitations

- ARM64 only; PRoot depends on `ptrace` being allowed by the device. Verified on Android ARM64 emulators; other devices still need testing.
- The workspace lives inside the app. The Android file picker is supported for attachments; downloads from the UI are saved through the system dialog (up to 25 MB).
- The microphone is requested from Android on demand by the original UI. Voice features need a suitable provider.
- The full OAuth, MCP, LSP, GUI plugin, external-browser return and desktop-integration matrix has not been exercised. The presence of the original UI alone does not prove 100% functional parity.
- The development tools are bundled, so first launch works offline. The LLM needs network access unless a model runs separately on the phone.
- The app UI is available in English (default) and Russian (follows the device language).

Sources and licenses of bundled components: [THIRD_PARTY.md](THIRD_PARTY.md). OpenCode is not modified; this project is not an official Anomaly app.
