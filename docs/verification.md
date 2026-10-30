# Verification

Latest run (October 30, 2026): Android 14 ARM64 emulator, 4 GB RAM, airplane mode, upstream OpenCode 2.2.2.

- First launch to a ready UI, fully offline: **68 s**. Later launches: about **9 s**. Cold start of the start screen: 2.4–3.1 s.
- Bundled tools run inside the PRoot environment: Git 2.43, Python 3.12 (with `ssl` and `sqlite3`), ripgrep 14.1, curl 8.5, OpenSSH 9.6; `git init` works.
- The original web UI opens in the WebView without network access.
- Terminal: bash on a real PTY with on-screen keyboard input (`git --version`, `python3 -c "print(6*7)"`), and the original OpenCode TUI renders correctly.
- Gradle build, unit tests and Android lint pass; only `ExpiredTargetSdkVersion` is excluded for the PRoot scheme described in the README.

## Earlier run (Android 15 emulator, API 35, 1080×2400, 3 GB RAM)

- APK and Android test APK build: successful.
- Unit tests: 7/7 pass.
- Real integration test: Ubuntu/PRoot and the original CLI start; `/api/info` and the original web UI are served; shell, Git and Python run; a file is written and read in `/workspace`; a PTY is created.
- `myserver` configuration: applied by the original core; a session is created; the core sends an authorised request to a local test OpenAI-compatible server; the streamed reply `android-model-ok` is stored in the session context.
- Manually in the UI: home screen, mobile menu, creating a new session and the input field. Screenshot: `screenshots/android-session.png`.

The test model is deterministic; no paid provider was used. These checks do not confirm the full OAuth/MCP/LSP/voice/desktop matrix and do not replace testing on physical phones.
