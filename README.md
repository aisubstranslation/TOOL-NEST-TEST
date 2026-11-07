# TOOLSNEST

TOOLSNEST is a fully offline, private multipurpose utilities suite for Android built with Kotlin, Jetpack Compose, and Material 3.

## Phase 1: Foundation Overview
Phase 1 establishes the complete architecture and foundation for 20 offline tools across four categories (Image, Files, PDF, Video & Audio):
- **User Interface & Navigation**: Material 3 theme (supporting Light, Dark, and Follow System modes with brand deep blue and orange accents), bottom navigation (Home, Categories, History, Settings), responsive search with multi-language indexing, and dedicated category filtering.
- **Localization**: Native support for English, Urdu (with native Right-to-Left layout), and Hindi, switchable on-device without external libraries.
- **Data Layer**: Room database storing operation history with reactive Flow queries, accompanied by Preferences DataStore for user settings.
- **File & Storage Layer**: Streamed ContentResolver helpers, secure FileProvider file sharing, strict file size limit validation, safe Unicode file sanitization, and isolated cache management.
- **Extension Architecture**: Modular registration pattern allowing subsequent phases to seamlessly add tools without modifying core routing or foundation files.
- **CI / CD Pipeline**: GitHub Actions workflow running tests, lint checks, debug APK assembly, and an automated AAPT2 security gate ensuring zero `android.permission.INTERNET` permissions in built artifacts.

## Tool Status
| # | Tool | Category | Phase | Status |
|---|---|---|---|---|
| 1 | Image Compressor | Image | 2 | Not implemented yet |
| 2 | Image Resizer | Image | 2 | Not implemented yet |
| 3 | Image Converter | Image | 2 | Not implemented yet |
| 4 | Image Cropper | Image | 3 | Not implemented yet |
| 5 | Remove Image Metadata | Image | 2 | Not implemented yet |
| 6 | File Information | Files | 3 | Not implemented yet |
| 7 | SHA-256 Hash Generator | Files | 3 | Not implemented yet |
| 8 | ZIP Creator | Files | 3 | Not implemented yet |
| 9 | QR Code Generator | Files | 4 | Not implemented yet |
| 10 | PDF Merge | PDF | 4 | Not implemented yet |
| 11 | PDF Split | PDF | 4 | Not implemented yet |
| 12 | PDF Compressor | PDF | 5 | Not implemented yet |
| 13 | PDF to Images | PDF | 5 | Not implemented yet |
| 14 | Images to PDF | PDF | 5 | Not implemented yet |
| 15 | Rotate PDF | PDF | 4 | Not implemented yet |
| 16 | Video to Audio | Video & Audio | 6A | Not implemented yet |
| 17 | Audio Converter | Video & Audio | 6A | Not implemented yet |
| 18 | Video Compressor | Video & Audio | 6B | Not implemented yet |
| 19 | Audio Trimmer | Video & Audio | 6B | Not implemented yet |
| 20 | AI Background Remover | Image | 7 | Not implemented yet |

## Extension Architecture
1. **Tool Registration**: Every tool is defined in `com.example.model.ToolRegistry`. Each subsequent phase adds a file implementing `com.example.tools.ToolModule`:
   ```kotlin
   interface ToolModule {
     val screens: Map<Int, @Composable (onBack: () -> Unit) -> Unit>
   }
   ```
2. **Pluggable Modules**: New modules are registered by appending one line to `allToolModules` in `com.example.tools.ToolModules.kt`. Unimplemented tools automatically display `ToolShellScreen` with "Coming in Phase N" notices.
3. **Settings Extensions**: New tool settings can be plugged directly into `com.example.ui.settings.SettingsExtensions.kt` without touching `SettingsScreen.kt`.

## Building Locally
Prerequisites: JDK 17 and Android SDK (API 34+ / compileSdk 36).
```bash
# Run JVM unit tests & Robolectric tests
gradle testDebugUnitTest

# Run Android Lint
gradle lintDebug

# Assemble Debug APK
gradle assembleDebug
```

## GitHub Actions & CI Workflow
The automated workflow (`.github/workflows/android.yml`) executes on pushes, pull requests, and manual triggers:
1. Executes unit and Robolectric tests (`testDebugUnitTest`).
2. Runs Android Lint (`lintDebug`).
3. Assembles the debug APK (`assembleDebug`).
4. **Permission Security Gate**: Inspects the compiled APK with AAPT2 and guarantees that `android.permission.INTERNET` is never present.
5. Uploads test and lint reports as artifacts.
6. Uploads the debug APK as an artifact.

## Downloading and Installing the APK
1. Navigate to the GitHub repository's **Actions** tab.
2. Select the latest successful workflow run.
3. Under the **Artifacts** section at the bottom, download **TOOLSNEST-debug-apk**.
4. Extract the ZIP to obtain the debug APK file.
5. **Installation Note**: If updating, uninstall any previous debug build from the device first, as debug keystore signatures can vary between different CI runner environments.
