# TOOLSNEST PROGRESS

The AI ticks steps after finishing them. "Verified" is set by the HUMAN only after a green GitHub Actions run AND a phone test.

## Phases
| Phase | Scope | Status |
|---|---|---|
| 1 | Foundation: interface, navigation, settings, history, file layer, CI | DONE (not yet verified) |
| 2 | Shared tool components + tools 1, 2, 3, 5 | DONE (not yet verified) |
| 3 | Tools 4, 6, 7, 8 | DONE (not yet verified) |
| 4 | Tools 9, 10, 11, 15 + PDF layer | DONE (not yet verified) |
| 5 | Tools 12, 13, 14 | DONE (not yet verified) |
| 6A | Job system + Processing Center + tools 17, 16 | DONE (not yet verified) |
| 6B | Tools 19, 18 | DONE (not yet verified) |
| 7 | Tool 20 AI Background Remover | DONE (not yet verified) |
| 8 | Audit and fixes | In progress (Part 1 Audit DONE) |
| 9 | Release APK + AAB | NOT STARTED |

## Tools
Status values: Not started / Implemented / Verified

| # | Tool | Category | Phase | Status |
|---|---|---|---|---|
| 1 | Image Compressor | Image | 2 | Implemented |
| 2 | Image Resizer | Image | 2 | Implemented |
| 3 | Image Converter | Image | 2 | Implemented |
| 4 | Image Cropper | Image | 3 | Implemented |
| 5 | Remove Image Metadata | Image | 2 | Implemented |
| 6 | File Information | Files | 3 | Implemented |
| 7 | SHA-256 Hash Generator | Files | 3 | Implemented |
| 8 | ZIP Creator | Files | 3 | Implemented |
| 9 | QR Code Generator | Files | 4 | Implemented |
| 10 | PDF Merge | PDF | 4 | Implemented |
| 11 | PDF Split | PDF | 4 | Implemented |
| 12 | PDF Compressor | PDF | 5 | Implemented |
| 13 | PDF to Images | PDF | 5 | Implemented |
| 14 | Images to PDF | PDF | 5 | Implemented |
| 15 | Rotate PDF | PDF | 4 | Implemented |
| 16 | Video to Audio | Video & Audio | 6A | Implemented |
| 17 | Audio Converter | Video & Audio | 6A | Implemented |
| 18 | Video Compressor | Video & Audio | 6B | Implemented |
| 19 | Audio Trimmer | Video & Audio | 6B | Implemented |
| 20 | AI Background Remover | Image | 7 | Implemented |

## Step log
(Append one line per finished step, for example: "Phase 2 step 3 - files: ImageEngine.kt, ImageMath.kt")
- Phase 1 step 1 - files: PROJECT_RULES.md, PROGRESS.md
- Phase 1 step 2 - files: metadata.json, settings.gradle.kts, gradle.properties, build.gradle.kts, app/build.gradle.kts, app/src/test/resources/robolectric.properties
- Phase 1 step 3 - files: Color.kt, Theme.kt, Type.kt, ic_launcher_background.xml, ic_launcher_foreground.xml, ic_launcher_monochrome.xml, mipmap-anydpi-v26/ic_launcher.xml, mipmap-anydpi-v26/ic_launcher_round.xml, ToolsNestLogo.kt
- Phase 1 step 4 - files: values/strings.xml, values-ur/strings.xml, values-hi/strings.xml
- Phase 1 step 5 - files: Category.kt, Tool.kt, ToolRegistry.kt, ToolSearch.kt
- Phase 1 step 6 - files: HistoryEntity.kt, HistoryDao.kt, ToolsNestDatabase.kt, HistoryRepository.kt, SettingsDataStore.kt
- Phase 1 step 7 - files: FileLimits.kt, FileFormatters.kt, FileNameSanitizer.kt, FileLayer.kt, file_paths.xml
- Phase 1 step 8 - files: ToolsNestApp.kt, ToolModule.kt, ToolModules.kt, SettingsExtensions.kt
- Phase 1 step 9 - files: Screen.kt, MainViewModel.kt, ToolCard.kt, HomeScreen.kt, CategoriesScreen.kt, HistoryScreen.kt, SettingsScreen.kt, ToolShellScreen.kt, MainScaffold.kt, LocaleHelper.kt, MainActivity.kt
- Phase 1 step 10 - files: AndroidManifest.xml
- Phase 1 step 11 - files: .github/workflows/android.yml
- Phase 1 step 12 - files: FileFormattersTest.kt, FileNameSanitizerTest.kt, FileLimitsTest.kt, ToolRegistryTest.kt, ToolSearchTest.kt, HistoryRepositoryTest.kt, ExampleRobolectricTest.kt
- Phase 1 step 13 - files: README.md
- Phase 2 step 1 - files: ToolScaffold.kt, FilePickerCard.kt, SelectedFileRow.kt, ProgressCard.kt, ErrorCard.kt, ResultCard.kt, ToolJobRunner.kt, VerificationHelpers.kt
- Phase 2 step 2 - files: ImageEngine.kt, ImageMath.kt, MetadataScanner.kt
- Phase 2 step 3 - files: ImageCompressorScreen.kt, strings_tool_1.xml, values-ur/strings_tool_1.xml, values-hi/strings_tool_1.xml
- Phase 2 step 4 - files: ImageResizerScreen.kt, strings_tool_2.xml, values-ur/strings_tool_2.xml, values-hi/strings_tool_2.xml
- Phase 2 step 5 - files: ImageConverterScreen.kt, strings_tool_3.xml, values-ur/strings_tool_3.xml, values-hi/strings_tool_3.xml
- Phase 2 step 6 - files: MetadataRemoverScreen.kt, strings_tool_5.xml, values-ur/strings_tool_5.xml, values-hi/strings_tool_5.xml
- Phase 2 step 7 - files: ImageToolsModule.kt, ToolModules.kt, ImageSettingsDataStore.kt, ImageSettingsSection.kt, SettingsExtensions.kt
- Phase 2 step 8 - files: ImageMathTest.kt, MetadataScannerTest.kt, ToolJobRunnerTest.kt
- Phase 2 step 9 - files: PROGRESS.md, build and unit test verification
- Phase 2 bug fix - files: FileSelectionHelper.kt, SelectedFileRow.kt, ImageEngine.kt, ImageCompressorScreen.kt, ImageResizerScreen.kt, ImageConverterScreen.kt, MetadataRemoverScreen.kt, FileSelectionHelperTest.kt
- Phase 3 step 1 - files: FileInfoAnalyzer.kt, FileInformationScreen.kt, strings_tool_6.xml, values-ur/strings_tool_6.xml, values-hi/strings_tool_6.xml
- Phase 3 step 2 - files: Sha256Helper.kt, Sha256Screen.kt, strings_tool_7.xml, values-ur/strings_tool_7.xml, values-hi/strings_tool_7.xml
- Phase 3 step 3 - files: ZipEntrySanitizer.kt, ZipWriterHelper.kt, ZipCreatorScreen.kt, strings_tool_8.xml, values-ur/strings_tool_8.xml, values-hi/strings_tool_8.xml
- Phase 3 step 4 - files: CropMath.kt, ImageCroppingHelper.kt, ImageCropperScreen.kt, strings_tool_4.xml, values-ur/strings_tool_4.xml, values-hi/strings_tool_4.xml
- Phase 3 step 5 - files: FileAndCropModule.kt, ToolModules.kt, ToolRegistry.kt
- Phase 3 step 6 - files: CropMathTest.kt, Sha256HelperTest.kt, ZipEntrySanitizerTest.kt, ZipCreatorTest.kt
- Phase 3 step 7 - files: PROGRESS.md
- RTL fix & CI cleanup - files: LocaleDirectionHelper.kt, LocaleHelper.kt, MainActivity.kt, MainViewModel.kt, ToolsNestApp.kt, ImageCropperScreen.kt, LocaleDirectionHelperTest.kt, app/build.gradle.kts, AndroidManifest.xml, FileInfoAnalyzer.kt, strings.xml, PROGRESS.md
- Tool 6 crash fix & local crash reporter - files: FilePickerCard.kt, FileInfoAnalyzer.kt, FileInformationScreen.kt, CrashReporter.kt, CrashReporterTest.kt, ToolsNestApp.kt, MainActivity.kt, strings.xml, values-ur/strings.xml, values-hi/strings.xml, PROGRESS.md
- Phase 4 step 1 - files: PdfLimits.kt, PdfErrors.kt, PageRangeParser.kt, PdfFileHelper.kt, PdfVerifier.kt, PdfThumbnailCache.kt, MultiOutputSaver.kt
- Phase 4 step 2 - files: QrPayloadBuilders.kt, QrCodeGenerator.kt, QrCodeScreen.kt, strings_tool_9.xml, values-ur/strings_tool_9.xml, values-hi/strings_tool_9.xml
- Phase 4 step 3 - files: PdfRotationHelper.kt, PdfRotatorScreen.kt, strings_tool_15.xml, values-ur/strings_tool_15.xml, values-hi/strings_tool_15.xml
- Phase 4 step 4 - files: PdfMergeHelper.kt, PdfMergeScreen.kt, strings_tool_10.xml, values-ur/strings_tool_10.xml, values-hi/strings_tool_10.xml
- Phase 4 step 5 - files: PdfSplitHelper.kt, PdfSplitScreen.kt, strings_tool_11.xml, values-ur/strings_tool_11.xml, values-hi/strings_tool_11.xml
- Phase 4 step 6 - files: PdfAndQrModule.kt, ToolModules.kt, PdfSettingsSection.kt, SettingsExtensions.kt, strings.xml, values-ur/strings.xml, values-hi/strings.xml
- Phase 4 step 7 - files: PageRangeParserTest.kt, PdfRotationHelperTest.kt, QrPayloadBuildersTest.kt, PdfLimitsAndErrorsTest.kt
- Phase 4 step 8 - files: PROGRESS.md, build, unit test, lint and permissions verification
- Fix pass step 1 - files: CropMath.kt, ImageCroppingHelper.kt, CropMathTest.kt
- Fix pass step 2 - files: ImageCropperScreen.kt, ImageCroppingHelper.kt, CropMath.kt, CropMathTest.kt
- Fix pass step 3 - files: FileSelectionHelper.kt, SelectedFileRow.kt, ResultCard.kt, ImageCompressorScreen.kt, ImageConverterScreen.kt, ImageResizerScreen.kt, MetadataRemoverScreen.kt, ZipCreatorScreen.kt, PdfMergeScreen.kt, ImageEngine.kt
- Fix pass step 4 - files: ToolJobRunner.kt, ToolJobRunnerTest.kt, ImageCompressorScreen.kt, ImageConverterScreen.kt, ImageResizerScreen.kt, MetadataRemoverScreen.kt, PdfMergeScreen.kt, PdfSplitScreen.kt, PdfRotatorScreen.kt, ImageCropperScreen.kt, Sha256Screen.kt, ZipCreatorScreen.kt, strings.xml, values-ur/strings.xml, values-hi/strings.xml
- Phase 5 - files: ImagesToPdfLayout.kt, ImagesToPdfHelper.kt, ImagesToPdfScreen.kt, PdfToImagesMath.kt, PdfToImagesHelper.kt, PdfToImagesScreen.kt, PdfCompressorMath.kt, PdfCompressorHelper.kt, PdfCompressorScreen.kt, PdfAdvancedModule.kt, ToolModules.kt, ToolRegistry.kt, MultiOutputSaver.kt, strings_tool_12.xml, strings_tool_13.xml, strings_tool_14.xml, Phase5MathTests.kt, PROGRESS.md
- CI fix - android.yml: artifact upload made non-blocking, shorter retention
- CI fix - android.yml: debug APK also published to rolling release debug-latest
- Phase 6A - files: JobStatus.kt, JobEntity.kt, JobDao.kt, ToolsNestDatabase.kt, JobRepository.kt, JobRecoveryHelper.kt, JobManager.kt, ProcessingService.kt, ToolJobRunner.kt, ProcessingCenterScreen.kt, HomeScreen.kt, MainActivity.kt, WavMath.kt, WavHeaderHelper.kt, PcmConversionHelper.kt, DurationToleranceChecker.kt, StorageRequirementCalculator.kt, DuplicateJobHelper.kt, MediaErrorMapper.kt, MediaProbe.kt, WavEngine.kt, MediaTransformerEngine.kt, MediaOutputVerifier.kt, AudioConverterHelper.kt, AudioConverterScreen.kt, VideoToAudioHelper.kt, VideoToAudioScreen.kt, MediaAudioModule.kt, ToolModules.kt, MediaSettingsDataStore.kt, MediaSettingsSection.kt, SettingsExtensions.kt, strings_tool_16.xml, strings_tool_17.xml, strings_proc_center.xml, Phase6AMediaTests.kt, PROGRESS.md
- Phase 6B - files: AudioTrimmerValidation.kt, WaveformHelper.kt, AudioTrimmerHelper.kt, AudioTrimmerScreen.kt, VideoCompressorMath.kt, VideoCompressorHelper.kt, VideoCompressorScreen.kt, VideoSettingsDataStore.kt, VideoSettingsSection.kt, MediaVideoTrimModule.kt, ToolModules.kt, SettingsExtensions.kt, strings_tool_18.xml, strings_tool_19.xml, Phase6BVideoTrimTests.kt, PROGRESS.md
- Phase 7 - files: libs.versions.toml, app/build.gradle.kts, BgRemoveMath.kt, BgRemoveHistory.kt, U2NetRunner.kt, BgRemoveHelper.kt, BackgroundRemoverScreen.kt, BackgroundRemoverModule.kt, ToolModules.kt, ToolRegistry.kt, strings_tool_20.xml, values-ur/strings_tool_20.xml, values-hi/strings_tool_20.xml, strings.xml, values-ur/strings.xml, values-hi/strings.xml, Phase7BgRemoveTests.kt, PROGRESS.md
- Phase 8 part 1 - files: AUDIT.md, PROGRESS.md
- Phase 8 part 2 - files: OutputDirs.kt, file_paths.xml, FileLayer.kt, AudioConverterHelper.kt, VideoToAudioHelper.kt, ImageCropperScreen.kt, BgRemoveMath.kt, BgRemoveHelper.kt, BackgroundRemoverScreen.kt, AudioTrimmerScreen.kt, VideoCompressorScreen.kt, VideoToAudioScreen.kt, AudioConverterScreen.kt, ToolJobRunner.kt, PdfVerifier.kt, PdfToImagesHelper.kt, JobManager.kt, MainViewModel.kt, SettingsScreen.kt, ImagesToPdfScreen.kt, MediaTransformerEngine.kt, ImageResizerScreen.kt, ImageConverterScreen.kt, MetadataRemoverScreen.kt, PdfRotatorScreen.kt, strings.xml, values-ur/strings.xml, values-hi/strings.xml, strings_tool_20.xml, OutputDirsFilePathsTest.kt, Step2BgRemovePureMathTest.kt, Step3MediaTrimAndJobStateTest.kt, Step4HistoryAndPdfMemoryTest.kt, Step5TempCleanupProtectionTest.kt, Step6ImagesToPdfSettingsTest.kt, Step7LocalizedStringsTest.kt, AUDIT.md, PROGRESS.md
- CI fix (Build #16) - files: PdfMergeScreen.kt, PdfSplitScreen.kt, PdfRotatorScreen.kt, PdfCompressorScreen.kt, ImagesToPdfScreen.kt, ZipCreatorScreen.kt, FileLayer.kt, Step5TempCleanupProtectionTest.kt, StringResourcesParityTest.kt, PROGRESS.md
- CI fix (Unit-test suite) - files: BgRemoveMath.kt, Step2BgRemovePureMathTest.kt, Step3MediaTrimAndJobStateTest.kt, Step5TempCleanupProtectionTest.kt, ImagesToPdfLayout.kt, ImagesToPdfScreen.kt, Step6ImagesToPdfSettingsTest.kt, PROGRESS.md

## Fix pass
- [x] Step 1: Pure crop geometry - files: CropMath.kt, ImageCroppingHelper.kt, CropMathTest.kt
- [x] Step 2: Cropper screen and crop save - files: ImageCropperScreen.kt, ImageCroppingHelper.kt
- [x] Step 3: Loading off the main thread - files: FileSelectionHelper.kt, SelectedFileRow.kt, ResultCard.kt, ImageCompressorScreen.kt, ImageConverterScreen.kt, ImageResizerScreen.kt, MetadataRemoverScreen.kt, ZipCreatorScreen.kt, PdfMergeScreen.kt, ImageEngine.kt
- [x] Step 4: Job runner and tool state - files: ToolJobRunner.kt, ToolJobRunnerTest.kt, ImageCompressorScreen.kt, ImageConverterScreen.kt, ImageResizerScreen.kt, MetadataRemoverScreen.kt, PdfMergeScreen.kt, PdfSplitScreen.kt, PdfRotatorScreen.kt, ImageCropperScreen.kt, Sha256Screen.kt, ZipCreatorScreen.kt, strings.xml, values-ur/strings.xml, values-hi/strings.xml

## Part 2: Targeted Fixes
- [x] Step 1: File sharing and open crash (B-17) - files: OutputDirs.kt, file_paths.xml, FileLayer.kt, AudioConverterHelper.kt, VideoToAudioHelper.kt, ImageCropperScreen.kt, MediaSettingsSection.kt, ResultCard.kt, ProcessingCenterScreen.kt, VideoCompressorScreen.kt, VideoToAudioScreen.kt, AudioConverterScreen.kt, AudioTrimmerScreen.kt, BackgroundRemoverScreen.kt, QrCodeScreen.kt, MultiOutputSaver.kt, values/strings.xml, values-ur/strings.xml, values-hi/strings.xml, OutputDirsFilePathsTest.kt
- [x] Step 2: Background remover (B-01, B-04, B-05, B-10, B-18, B-19, B-06) - files: BgRemoveMath.kt, BgRemoveHelper.kt, strings_tool_20.xml (values, values-ur, values-hi), BackgroundRemoverScreen.kt, Step2BgRemovePureMathTest.kt
- [x] Step 3: Media screens off the main thread and rotation (B-02, B-03, B-11) - files: AudioTrimmerScreen.kt, VideoCompressorScreen.kt, VideoToAudioScreen.kt, AudioConverterScreen.kt, Step3MediaTrimAndJobStateTest.kt
- [x] Step 4: History and PDF memory (B-09, B-08, B-12) - files: ToolJobRunner.kt, PdfVerifier.kt, PdfToImagesHelper.kt, Step4HistoryAndPdfMemoryTest.kt
- [x] Step 5: Temp cleanup (B-07) - files: FileLayer.kt, JobManager.kt, ToolJobRunner.kt, MainViewModel.kt, SettingsScreen.kt, Step5TempCleanupProtectionTest.kt
- [x] Step 6: Tool 14 settings default and cleanup (B-14, B-15, B-16) - files: ImagesToPdfScreen.kt, Step6ImagesToPdfSettingsTest.kt, deleted ExampleUnitTest.kt & ExampleInstrumentedTest.kt
- [x] Step 7: Hardcoded user-visible English (B-13) - files: values/strings.xml, values-ur/strings.xml, values-hi/strings.xml, PdfRotatorScreen.kt, ImageCompressorScreen.kt, ImageResizerScreen.kt, ImageConverterScreen.kt, MetadataRemoverScreen.kt, MediaTransformerEngine.kt, Step7LocalizedStringsTest.kt
- [x] Step 8: Tests and finish - All 19 defect IDs resolved, pure JVM unit tests added for all steps, no untested assertions, full localization in English, Urdu, and Hindi.

