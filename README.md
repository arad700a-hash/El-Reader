# Novel Reader (کتاب‌خوان رمان) — native Android EPUB reader

Kotlin · Jetpack Compose · Room · DataStore · Storage Access Framework · **Readium Kotlin Toolkit 3.2.0** (real EPUB engine, not a WebView-wrapped website).
Persian (RTL) UI by default, English included, dark theme by default.

> **Status: source code only. It has never been compiled, installed or run.**
> The authoring sandbox had no Android SDK and no access to Maven/Google repositories, so no APK exists and
> every Android/Readium-dependent feature is **untested**. See `docs/TEST_REPORT.md` for the exact status of each
> feature and `docs/KNOWN_LIMITATIONS.md` for known gaps. Do not treat this as a finished release.

## Build
Versions (JDK 17, Gradle 8.11.1, AGP 8.9.1, Kotlin 2.3.20, KSP 2.3.4, compileSdk 36) and the root cause of the earlier AndroidForge `kspDebugKotlin` failure are in `docs/BUILD.md`. The fix has **not** been confirmed by a real build.
See [`docs/BUILD.md`](docs/BUILD.md) (Android Studio, command line, signed release, GitHub Actions).

## Changing name / icon / package id
- App name: `app/src/main/res/values/strings.xml` and `values-en/strings.xml` → `app_name`
  (strings are generated from a table; if you edit the XML by hand, keep both languages in sync).
- Icon: `res/drawable/ic_launcher_foreground.xml`, `res/values/colors.xml` (background).
- Package id: `appId` at the top of `app/build.gradle.kts` (+ `namespace` if you also move the Kotlin package).

## Install an APK (once you have built one)
Copy the `.apk` to the phone, open it, allow "install unknown apps" for the file manager when asked.
Debug builds use the id `com.novelreader.app.debug` and can sit next to a release build.

## Custom fonts
Imported TTF/OTF fonts are served to the EPUB through a composite Readium container + CSS `@font-face` injection
(the EPUB file itself is never modified). Design, risks and device test plan: [`docs/FONT_IMPORT.md`](docs/FONT_IMPORT.md).
