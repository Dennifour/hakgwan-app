# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A native Android wrapper ("학급관리" / class management) around an existing React-in-HTML
app. The original single-file app (`C:\CodeProjects\학급특색활동 3학년\학급특색활동.html`) had to keep
its design and behavior **100% intact**, so instead of porting it to native UI, the whole HTML/JS
bundle is shipped inside a `WebView`. The original `.html` is not modified — only the copy in
`app/src/main/assets/index.html`.

This project lives at an ASCII path (`hakgwan_android`) specifically to avoid Korean-path build issues.

## Build

There is **no Android Studio** here; build from the command line with a portable JDK.

```powershell
$env:JAVA_HOME = 'C:\CodeProjects\tools\jdk-17.0.19+10'
cd C:\CodeProjects\hakgwan_android
.\gradlew.bat assembleDebug
```

Output APK: `app\build\outputs\apk\debug\app-debug.apk`

Toolchain: Gradle 8.7 (wrapper) · AGP 8.5.2 · JDK 17 · compile/target SDK 34, build-tools 34.0.0,
minSdk 29. SDK location is set in `local.properties`. **Note:** the project does **not** use
AndroidX (`android.useAndroidX=false`); `MainActivity` extends the framework `android.app.Activity`,
not AppCompat. Keep new code framework-only.

Do not build/publish a release APK unless explicitly asked.

There is no test suite, lint config, or device/emulator wired up — builds and asset bundling are the
only things that have been verified.

## Architecture

Two layers that talk over a single JavaScript bridge:

### 1. Native shell — `app/src/main/java/com/classmgr/hakgwan/MainActivity.java`
- One `Activity`, one full-screen `WebView` loading `file:///android_asset/index.html`.
- Exposes a `Bridge` object to JS as `window.AndroidFile` with two methods:
  - `saveBase64(filename, base64, mime)` — writes to `Downloads/학급관리/` via `MediaStore`. **Same
    filename overwrites** (mirrors the original app's "같은 날짜 파일은 덮어씌워집니다" behavior). Runs off
    the UI thread.
  - `printPage()` — Android print framework. Currently unused (the print button was replaced by Excel
    export), kept for compatibility.
- `onShowFileChooser` handles HTML `<input type=file>` (used by data import) via `ACTION_GET_CONTENT`.
- `onStop` dispatches a synthetic `beforeunload` event into the page so the web app's auto-save runs
  when the app is backgrounded.
- DOM storage is enabled → the web app's `localStorage` persists on-device.
- **Fully offline:** no `INTERNET` permission. All libraries and fonts are bundled locally.

### 2. Web app — `app/src/main/assets/index.html` (~1670 lines, single file)
React (development build) compiled in-browser by Babel via `<script type="text/babel">`. All vendor
libs are local assets: `react.development.js`, `react-dom.development.js`, `babel.min.js`,
`xlsx.full.min.js` (SheetJS), plus 4 Noto Sans KR `.woff2` weights under `assets/fonts/`.

The bridge shim near the top of `index.html` monkey-patches `HTMLAnchorElement.prototype.click`:
download anchors (`blob:`/`data:` + `download` attr) are intercepted, read as base64, and routed to
`AndroidFile.saveBase64` instead of doing a browser download. `window.print` is redirected to
`AndroidFile.printPage`. This is how the unchanged web app's "download a file" code ends up writing
to Android storage — **preserve this shim if you touch the asset file.**

App structure (component → line in `index.html`):
- `App` (226) — root, view router, owns persisted state.
- `HomeView` (515), `SeatingView` (603) seating chart, `AttendanceView` (825) check-in,
  `AttendanceTableView` (941) monthly table with **Excel export** (`exportExcel`, uses `XLSX.*`),
  `SettingsView` (1175) and its sub-settings, `RankingsModal` (1511).
- Persistence: two `localStorage` keys — `class_mgr_v1` (`STORAGE_KEY`, main data) and
  `class_mgr_sessions_v1` (`SESSION_KEY`, attendance sessions/check-in times).

### Notable history
The original "🖨 인쇄" (window.print) button on the attendance table was replaced with "📊 엑셀 내보내기",
which builds an `.xlsx` for the selected month via SheetJS (`AttendanceTableView.exportExcel`). The
`printPage` bridge remains but is no longer triggered.

## Working on this codebase

- To change app behavior or UI, edit `app/src/main/assets/index.html` (not the original Korean-path
  HTML). It's a single self-contained file; components are plain functions, no module system.
- File save/load and print only work through the bridge — test those paths on-device, since the
  WebView's native integration can't be exercised in a plain browser.
