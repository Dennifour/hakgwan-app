# hakgwan-app

학급관리 (class management) Android app — private distribution.

A native Android `WebView` wrapper around a self-contained React-in-HTML app, bundled to run
fully offline (no `INTERNET` permission; all libraries and fonts are local assets). The app
provides seating charts, attendance check-in, a monthly attendance table with Excel export
(SheetJS), and rankings.

## Build

No Android Studio required — build from the command line with a portable JDK 17:

```powershell
$env:JAVA_HOME = 'C:\CodeProjects\tools\jdk-17.0.19+10'
.\gradlew.bat assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

Toolchain: Gradle 8.7 · AGP 8.5.2 · JDK 17 · compile/target SDK 34 · minSdk 29 · no AndroidX.

## Layout

- `app/src/main/java/com/classmgr/hakgwan/MainActivity.java` — native shell + `AndroidFile` JS bridge (file save to `Downloads/학급관리/`, file import, print).
- `app/src/main/assets/index.html` — the full React/HTML app plus bundled vendor libs and Korean fonts.

See `CLAUDE.md` for architecture details.

## Releases

Built APKs are published under [Releases](../../releases).
