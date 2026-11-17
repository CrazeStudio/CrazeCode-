# STONE

**STONE** is a monochrome, VS Code-inspired Android code workspace by CrazeStudio.

- Application ID: `com.crazestudio.stone`
- Platform: Android 8.0+ (API 26+)
- UI: Kotlin + Jetpack Compose
- Build: Gradle 8.9, Android Gradle Plugin 8.7.3, JDK 17

## UI included

The supplied design reference is represented by these screens:

- STONE splash / brand mark
- Home dashboard and recent projects
- Code editor with tabs, line numbers, restrained syntax coloring and status bar
- Project explorer
- Integrated terminal
- Extensions catalogue
- Git source control
- Run & Debug launcher
- Settings and theme picker
- Projects and Tools navigation

The visual system uses charcoal / near-black surfaces, white and grey typography, and orange only for actions and active states.

## Implemented foundations

- Create projects from a starter template.
- Import a folder through Android's Storage Access Framework.
- Import ZIP projects with canonical-path checks to block ZIP path traversal.
- Edit and save local files with multiple open tabs.
- Browse, create and delete project files.
- Run commands available inside the Android app sandbox, with a 20-second timeout.
- Clone public HTTPS Git repositories, view local Git status, initialize Git and make local commits using JGit.
- Toggle built-in language profiles in the extension catalogue.
- Configure a local process command in Run & Debug.
- Adjust editor font size and choose visual themes.
- GitHub Actions workflow to build and upload a debug APK.

## Build

Open the repository in Android Studio with JDK 17 and Android SDK 35, then run the `app` configuration. A command-line build can be run on a machine with Gradle 8.9 installed:

```bash
gradle assembleDebug
```

The debug APK output is `app/build/outputs/apk/debug/app-debug.apk`.

## Important runtime limitations

This is an Android application, not a desktop Linux distribution. Android does not include Python, Node.js, Java compilers, C/C++ toolchains or language servers by default. The terminal executes commands available within the app's Android process sandbox; commands such as `python`, `node`, `javac` and `gcc` only work after a compatible runtime/toolchain is provided. Public HTTPS Git clone and local Git operations are implemented. Authenticated push/pull, full LSP IntelliSense, language-specific debugger adapters, and third-party executable VS Code extension loading are not implemented in this first foundation. The extension screen manages built-in editor profiles, not arbitrary VS Code Marketplace packages.

## Security

- No broad storage permission is requested.
- Folder and ZIP access go through system document pickers.
- ZIP extraction rejects output paths that resolve outside the import directory.
- Terminal commands run with the app's Android UID and permissions. Only run commands and projects you trust.
- Git clone accepts HTTPS URLs only. Do not commit secrets or access tokens into project files.
