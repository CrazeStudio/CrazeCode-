# CrazeCode for Android

CrazeCode is a hybrid Android IDE starter with a mobile-first editor, Android file picker integration, save-to-storage support, Termux handoff for real shell commands, and an optional connection to local code-server.

## Current capabilities

- Android debug APK built by GitHub Actions.
- CodeMirror editor with JavaScript, Python, HTML/XML, CSS and JSON modes when the CDN is reachable; a plain-text fallback remains available.
- Android Storage Access Framework open and save actions.
- Draft autosave in WebView local storage.
- Launch Termux and connect to code-server at `http://127.0.0.1:8080`.
- Responsive phone and landscape/tablet layout.

## Build the APK

1. Open this repository's Actions tab.
2. Select **CrazeCode Android APK**.
3. Run the workflow, or push a change to the project.
4. Download the **CrazeCode-debug-apk** artifact from a completed run.
5. Extract `app-debug.apk` and install it on Android.

The workflow uses JDK 17 and Android Gradle Plugin 8.7.3.

## Use the terminal

Install Termux from its official F-Droid or GitHub source, then run:

```sh
pkg update
pkg install git nodejs python clang
mkdir -p ~/projects
cd ~/projects
```

For a full VS Code-style workbench, install code-server from Termux's TUR repository:

```sh
pkg install tur-repo
pkg install code-server
code-server --bind-addr 127.0.0.1:8080
```

Return to CrazeCode and tap **VS Code**. Keep code-server bound to localhost; do not expose an unauthenticated server to Wi-Fi or the public internet.

## Limitations

This is a functional hybrid IDE foundation, not a complete VS Code reimplementation. CodeMirror currently loads from a CDN (plain-text fallback is available offline), and terminal commands run in the separate Termux app. Direct in-app PTY, Git UI integration, offline-bundled editor assets and extension management are future work.

CrazeCode is independent and is not affiliated with Microsoft or Visual Studio Code.
