# CrazeCode for Android

CrazeCode is a mobile-first hybrid coding workspace for Android. Stage 2 focuses on editor UI, local workspace files, language modes, suggestions, and a built-in basic console without Termux.

## Current capabilities

- Responsive dark IDE UI with violet branding, mobile and landscape layouts.
- CodeMirror syntax modes for 30+ common file/language types where a matching mode is available.
- Auto-detection by file extension and a manual language-mode selector.
- Inline keyword/snippet suggestions while typing, plus a suggestions button and Ctrl-Space shortcut.
- Workspace files and multiple editor tabs, persisted in WebView local storage.
- Import and export text/code files using Android's Storage Access Framework or browser file picker.
- Run JavaScript in a short-lived worker where the WebView supports it.
- Preview HTML/SVG files and CSS against the workspace index.html in a sandboxed preview.
- Built-in local workspace console: help, ls, pwd, cat, echo, touch, mkdir, rm, date, whoami, clear.
- Search in current file, code snippets, font size, line numbers, word wrap, and autosave controls.
- No Termux launch or code-server integration in this stage.

## Runtime limitations

Language highlighting and suggestions do not mean a language runtime is installed. At this stage, Run executes JavaScript where worker support is available and previews HTML/CSS. Python, Java, C/C++, Kotlin, PHP, SQL, and other languages still need their own interpreter/compiler/runtime before they can execute. The built-in console operates on CrazeCode's virtual workspace only; it is not a Linux or Android shell, does not execute real Git commands, and cannot build Android projects. CodeMirror loads from a CDN when available; offline fallback is plain-text editing.

## Build the APK

1. Open this repository's Actions tab.
2. Select **CrazeCode Android APK**.
3. Run the workflow, or push a change to the project.
4. Download the **CrazeCode-debug-apk** artifact from a completed run.
5. Extract app-debug.apk and install it on Android.

The workflow uses JDK 17 and Android Gradle Plugin 8.7.3.
