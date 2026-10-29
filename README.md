# CrazeCode for Android

CrazeCode is a mobile-first coding workspace for Android, built around a responsive editor and a privacy-minded local workspace.

## Current capabilities

- Custom CrazeCode SVG mark in the editor header and vector/adaptive Android launcher icon.
- Responsive dark IDE UI for phones and landscape layouts.
- CodeMirror language modes and automatic file-extension detection for many common source formats.
- Manual language-mode selector and keyword/snippet suggestions while typing; Ctrl-Space opens suggestions.
- Multiple tabs, local workspace files, file creation/deletion, search, snippets, font size, line numbers, word wrap, and autosave.
- Import files through Android's document picker.
- Choose a persistent Android folder through the Storage Access Framework; Save writes the current file into that folder. If no folder is selected, Android's Save As picker is used.
- Run JavaScript in a temporary worker where WebView supports it.
- Sandboxed HTML/SVG preview and CSS preview using the workspace index.html.
- Built-in virtual workspace console commands: help, ls, pwd, cat, echo, touch, mkdir, rm, date, whoami, clear.
- JDK 17 / Android Gradle build workflow.

## Limitations and next milestones

- The console is a virtual workspace simulator, not a Linux shell. It does not run arbitrary OS commands or real Git.
- Syntax highlighting and suggestions are not language runtimes. JavaScript execution and HTML/CSS preview are the current run paths; Python, Java, C/C++, Kotlin and other languages need their own runtimes/compilers.
- Language support modes and some editor resources are loaded from a CDN when online; plain text fallback is available.
- Full Git operations, LSP/IntelliSense servers, debugger integration, remote SSH/containers, extension marketplace, and autonomous AI agents are not implemented yet. These require separate backends, runtimes, and explicit integrations.

## Build

Open the repository's **Actions** tab and download the `CrazeCode-debug-apk` artifact from a successful workflow run.
