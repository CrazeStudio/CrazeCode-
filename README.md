# CrazeCode for Android

CrazeCode is a mobile-first hybrid coding workspace for Android. Stage 2 focuses on the editor UI, local workspace files, and a built-in basic console without Termux.

## Current capabilities

- Responsive dark IDE UI with violet branding, mobile and landscape layouts.
- CodeMirror syntax highlighting when the CDN is reachable, with a plain-text fallback.
- Workspace files and multiple editor tabs, persisted in WebView local storage.
- Import and export text/code files using Android's Storage Access Framework or browser file picker.
- Built-in local workspace console: help, ls, pwd, cat, echo, touch, mkdir, rm, date, whoami, clear.
- Basic JavaScript Run action using a short-lived worker where supported by the WebView.
- Search in current file, code snippets, font size, line numbers, word wrap, and autosave controls.
- No Termux launch or code-server integration in this stage.

## Console limitation

The built-in console operates on CrazeCode's virtual workspace only. It does not execute Android/Linux shell commands, run Git, or build Android projects. Those capabilities are reserved for the later terminal-integration stage. JavaScript Run support depends on WebView worker support.

## Build the APK

1. Open this repository's Actions tab.
2. Select **CrazeCode Android APK**.
3. Run the workflow, or push a change to the project.
4. Download the **CrazeCode-debug-apk** artifact from a completed run.
5. Extract app-debug.apk and install it on Android.

The workflow uses JDK 17 and Android Gradle Plugin 8.7.3.
