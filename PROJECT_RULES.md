# TOOLSNEST - PROJECT RULES (every task must follow these)

## 1. What this project is
- NATIVE ANDROID app (Kotlin + Jetpack Compose + Material 3), single module "app". It is NOT a website and has NO server. Ignore any web, server-side, Firebase or remote-inference notes that come from templates.
- Names: the Gradle namespace and ALL Kotlin packages are com.example (com.example.ui, com.example.data, ...). The applicationId is com.toolsnest.app. minSdk is 26. NEVER rename packages or the applicationId.
- 20 tools with FIXED numbering (see PROGRESS.md). Categories: Image (1, 2, 3, 4, 5, 20), Files (6, 7, 8, 9), PDF (10 to 15), Video & Audio (16 to 19).

## 2. Offline and privacy
- No INTERNET permission. No analytics, ads, login, payments, API keys or network libraries. Every tool works fully offline. CI fails if INTERNET appears in the BUILT APK.
- No storage permissions. Use Photo Picker, Storage Access Framework (OpenDocument, OpenMultipleDocuments, CreateDocument, OpenDocumentTree), MediaStore (Pictures/TOOLSNEST, Movies/TOOLSNEST) and FileProvider.

## 3. Things that must NOT be changed (they broke CI before)
- Kotlin, KSP, AGP, Gradle and JDK versions.
- gradle.properties lines: -Djava.awt.headless=true inside org.gradle.jvmargs, and ksp.use.ksp2=true.
- The signing logic in app/build.gradle.kts: the debug build type uses the custom debug keystore ONLY if file("${rootDir}/debug.keystore") exists, otherwise the standard "debug" signing config. Never create or commit a keystore. No google-services plugin and no google-services.json.
- app/src/test/resources/robolectric.properties (sdk=34). Prefer pure JVM unit tests without Android classes.
- .github/workflows/android.yml, unless the current phase explicitly tells you to change it.
- Add dependencies ONLY when the phase prompt names them, with versions that really exist. Never invent artifact names. If a version might not resolve, say so instead of guessing.

## 4. CI gates (GitHub Actions runs unit tests, lint, assembleDebug and a permission check)
- A lint ERROR or a failing unit test turns the build red. Therefore: guard every API-level-dependent call with Build.VERSION.SDK_INT (minSdk is 26), never hardcode user-visible text, give icons a contentDescription (or mark decorative), use start/end instead of left/right, leave no unused or misspelled resources and no missing translations.
- Unit tests: write only assertions whose expected values you verified by hand. Tests must be deterministic (no real time, no randomness without a fixed seed, no files outside a temp dir). Instrumented tests (androidTest) are NOT run by CI but must still compile.
- Never claim "compiled" or "tested". Only GitHub Actions decides.

## 5. Localisation
- English (default), Urdu (right-to-left), Hindi.
- Shared strings live in res/values/strings.xml (+ values-ur, values-hi) with identical key sets. tool<N>_name and tool<N>_desc live in the shared strings.xml.
- Every other string of a tool lives in that tool's own files: values/strings_tool_<N>.xml, values-ur/strings_tool_<N>.xml, values-hi/strings_tool_<N>.xml (identical keys, prefix tool<N>_). Shared UI components use the prefix common_.
- Every new screen must work in RTL.

## 6. Architecture and extension points (do not restructure)
- Tool registry: com.example.model.Tool and ToolRegistry (id, category, phase, name/description resources, keywords in en/ur/hi, icon).
- Tool screens: each phase adds ONE module file implementing com.example.tools.ToolModule (screens: Map<Int, @Composable (onBack: () -> Unit) -> Unit>) and ONE line in com.example.tools.ToolModules.kt. A tool without a registered screen shows ToolShellScreen with "Coming in Phase N". When its screen is registered the shell disappears automatically. NEVER edit MainActivity routing to add a tool.
- Settings: later phases add DataStore keys in their own file and add one composable to com.example.ui.settings.SettingsExtensions.kt. Do not rewrite SettingsScreen.kt.
- Application class com.example.ToolsNestApp holds appScope (application-scoped CoroutineScope).
- All heavy work goes through com.example.tools.common.ToolJobRunner (created in Phase 2). Tools never call appScope.launch directly. Phase 6A re-implements the inside of ToolJobRunner (persistence, foreground service) WITHOUT changing its public API, so tools written earlier keep working unchanged.
- Reuse FileLayer, theme, history repository, size formatter, filename generator and the shared components from Phase 2. Never duplicate them.

## 7. Quality bar for every tool
- Real processing. Output is verified (exists, size > 0, valid signature, readable) BEFORE success is shown. The original is never overwritten. Partial output is deleted on failure or cancel. Friendly errors with a recovery hint (no stack traces). No fake progress or fake time estimates. No buttons that do nothing. One history row per finished or failed file. Screen state survives rotation.
- Target device: 2 GB RAM and 16 GB storage. Limits: image 50 MB and 48 megapixels, PDF 150 MB, video 1000 MB, audio 500 MB. Check limits BEFORE processing. Stream large files, run one heavy job at a time, close every stream, bitmap, codec, renderer and retriever.

## 8. Work protocol (saves free quota and allows resuming)
- Read PROJECT_RULES.md and PROGRESS.md first. Do ONLY the current phase. Never start the next phase. Never regenerate files that belong to finished phases or finished steps.
- Work in numbered STEPS in order. After each step update PROGRESS.md (tick the step, list the files created or changed).
- Output only new or changed files, each COMPLETE (no "..." omissions). No long reports (15 lines maximum at the end).
- If you must stop (output limit or quota), stop at a step boundary AFTER updating PROGRESS.md and end with exactly: CHECKPOINT: Phase <N>, step <X> done. Next: step <Y>. Reply "continue".
- On "continue": read PROGRESS.md, check that the files of ticked steps really exist, then do ONLY the first unticked step.
- If anything in a phase prompt is impossible (library missing, API unavailable), say so plainly. Never replace it with a fake or placeholder implementation.
