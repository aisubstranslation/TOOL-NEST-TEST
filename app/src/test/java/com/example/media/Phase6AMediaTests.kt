package com.example.media

import com.example.data.model.JobRecoveryHelper
import com.example.data.model.JobStatus
import com.example.data.model.JobStatusTransitions
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Phase6AMediaTests {

  // ==========================================
  // 1. JobStatusTransitions
  // ==========================================

  @Test
  fun testJobStatusTransitionsValidPaths() {
    assertTrue(JobStatusTransitions.canTransition(JobStatus.QUEUED, JobStatus.PREPARING))
    assertTrue(JobStatusTransitions.canTransition(JobStatus.PREPARING, JobStatus.PROCESSING))
    assertTrue(JobStatusTransitions.canTransition(JobStatus.PROCESSING, JobStatus.COMPLETED))
    assertTrue(JobStatusTransitions.canTransition(JobStatus.PROCESSING, JobStatus.FAILED))
    assertTrue(JobStatusTransitions.canTransition(JobStatus.PROCESSING, JobStatus.CANCELLED))
    assertTrue(JobStatusTransitions.canTransition(JobStatus.PROCESSING, JobStatus.INTERRUPTED))
    assertTrue(JobStatusTransitions.canTransition(JobStatus.PREPARING, JobStatus.WAITING_FOR_INPUT))
    assertTrue(JobStatusTransitions.canTransition(JobStatus.WAITING_FOR_INPUT, JobStatus.QUEUED))

    // Terminal retries: FAILED, CANCELLED, INTERRUPTED can transition back to QUEUED
    assertTrue(JobStatusTransitions.canTransition(JobStatus.FAILED, JobStatus.QUEUED))
    assertTrue(JobStatusTransitions.canTransition(JobStatus.CANCELLED, JobStatus.QUEUED))
    assertTrue(JobStatusTransitions.canTransition(JobStatus.INTERRUPTED, JobStatus.QUEUED))
  }

  @Test
  fun testJobStatusTransitionsIdempotent() {
    for (status in JobStatus.values()) {
      assertTrue("Self-transition for $status should be valid", JobStatusTransitions.canTransition(status, status))
      assertEquals(status, JobStatusTransitions.transition(status, status))
    }
  }

  @Test
  fun testJobStatusCannotTransitionOutOfCompleted() {
    for (status in JobStatus.values()) {
      if (status == JobStatus.COMPLETED) continue
      assertFalse(
        "COMPLETED must not transition to $status",
        JobStatusTransitions.canTransition(JobStatus.COMPLETED, status)
      )
      assertThrows(IllegalStateException::class.java) {
        JobStatusTransitions.transition(JobStatus.COMPLETED, status)
      }
    }
  }

  @Test
  fun testJobStatusInvalidDirectJumps() {
    // Cannot jump directly from QUEUED to COMPLETED
    assertFalse(JobStatusTransitions.canTransition(JobStatus.QUEUED, JobStatus.COMPLETED))
    assertThrows(IllegalStateException::class.java) {
      JobStatusTransitions.transition(JobStatus.QUEUED, JobStatus.COMPLETED)
    }

    // Cannot jump from PREPARING to COMPLETED without PROCESSING
    assertFalse(JobStatusTransitions.canTransition(JobStatus.PREPARING, JobStatus.COMPLETED))
  }

  // ==========================================
  // 2. Job Recovery Logic
  // ==========================================

  @Test
  fun testJobRecoveryTransitions() {
    // In-flight active jobs must recover to INTERRUPTED
    assertEquals(JobStatus.INTERRUPTED, JobRecoveryHelper.getRecoveredStatus(JobStatus.PROCESSING))
    assertEquals(JobStatus.INTERRUPTED, JobRecoveryHelper.getRecoveredStatus(JobStatus.PREPARING))
    assertEquals(JobStatus.INTERRUPTED, JobRecoveryHelper.getRecoveredStatus(JobStatus.WAITING_FOR_INPUT))

    assertTrue(JobRecoveryHelper.shouldRecover(JobStatus.PROCESSING))
    assertTrue(JobRecoveryHelper.shouldRecover(JobStatus.PREPARING))
    assertTrue(JobRecoveryHelper.shouldRecover(JobStatus.WAITING_FOR_INPUT))

    // QUEUED, COMPLETED, FAILED, CANCELLED, INTERRUPTED stay unchanged
    assertEquals(JobStatus.QUEUED, JobRecoveryHelper.getRecoveredStatus(JobStatus.QUEUED))
    assertEquals(JobStatus.COMPLETED, JobRecoveryHelper.getRecoveredStatus(JobStatus.COMPLETED))
    assertEquals(JobStatus.FAILED, JobRecoveryHelper.getRecoveredStatus(JobStatus.FAILED))
    assertEquals(JobStatus.CANCELLED, JobRecoveryHelper.getRecoveredStatus(JobStatus.CANCELLED))
    assertEquals(JobStatus.INTERRUPTED, JobRecoveryHelper.getRecoveredStatus(JobStatus.INTERRUPTED))

    assertFalse(JobRecoveryHelper.shouldRecover(JobStatus.QUEUED))
    assertFalse(JobRecoveryHelper.shouldRecover(JobStatus.COMPLETED))
    assertFalse(JobRecoveryHelper.shouldRecover(JobStatus.FAILED))
    assertFalse(JobRecoveryHelper.shouldRecover(JobStatus.CANCELLED))
  }

  // ==========================================
  // 3. DuplicateJobHelper
  // ==========================================

  @Test
  fun testDuplicateJobHelperStableKeys() {
    val key1 = DuplicateJobHelper.buildKey(17, "content://media/audio/123", "{\"format\":\"M4A\"}")
    val key2 = DuplicateJobHelper.buildKey(17, "content://media/audio/123", "{\"format\":\"M4A\"}")
    val keyWhitespace = DuplicateJobHelper.buildKey(17, "  content://media/audio/123  ", " {\"format\":\"M4A\"} ")

    assertEquals(key1, key2)
    assertEquals(key1, keyWhitespace)

    val keyDiffTool = DuplicateJobHelper.buildKey(16, "content://media/audio/123", "{\"format\":\"M4A\"}")
    val keyDiffUri = DuplicateJobHelper.buildKey(17, "content://media/audio/456", "{\"format\":\"M4A\"}")
    val keyDiffOptions = DuplicateJobHelper.buildKey(17, "content://media/audio/123", "{\"format\":\"WAV\"}")

    assertFalse(key1 == keyDiffTool)
    assertFalse(key1 == keyDiffUri)
    assertFalse(key1 == keyDiffOptions)
  }

  // ==========================================
  // 4. StorageRequirementCalculator
  // ==========================================

  @Test
  fun testStorageRequirementCalculator() {
    val margin = StorageRequirementCalculator.SAFETY_MARGIN_BYTES // 300 MB = 314,572,800 bytes
    assertEquals(314572800L, margin)

    val estimatedOutput = 50L * 1024L * 1024L // 50 MB
    val required = StorageRequirementCalculator.calculateRequiredFreeSpace(estimatedOutput)
    assertEquals(350L * 1024L * 1024L, required)

    // Exact boundary
    assertTrue(StorageRequirementCalculator.hasEnoughFreeSpace(required, estimatedOutput))

    // Exactly 1 byte less than required
    assertFalse(StorageRequirementCalculator.hasEnoughFreeSpace(required - 1L, estimatedOutput))

    // Plenty of space
    assertTrue(StorageRequirementCalculator.hasEnoughFreeSpace(required + 1000L, estimatedOutput))

    // Negative estimated bound is coerced to 0 + margin
    val zeroReq = StorageRequirementCalculator.calculateRequiredFreeSpace(-100L)
    assertEquals(margin, zeroReq)
  }

  // ==========================================
  // 5. WavHeaderHelper
  // ==========================================

  @Test
  fun testWavHeaderHelperHeaderStructure() {
    val sampleRate = 44100
    val channels = 2
    val dataBytes = 176400L // 1 second of stereo 16-bit PCM

    val header = WavHeaderHelper.createPcm16Header(sampleRate, channels, dataBytes)
    assertEquals(44, header.size)

    val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

    // RIFF magic (offset 0..3)
    val riff = String(header, 0, 4)
    assertEquals("RIFF", riff)

    // Total chunk size at offset 4: dataBytes + 36
    val totalChunkSize = buffer.getInt(4).toLong() and 0xFFFFFFFFL
    assertEquals(dataBytes + 36L, totalChunkSize)

    // WAVE magic (offset 8..11)
    val wave = String(header, 8, 4)
    assertEquals("WAVE", wave)

    // fmt subchunk magic (offset 12..15)
    val fmt = String(header, 12, 4)
    assertEquals("fmt ", fmt)

    // Subchunk1 size (offset 16): 16 for PCM
    val subchunk1Size = buffer.getInt(16)
    assertEquals(16, subchunk1Size)

    // Audio format (offset 20): 1 = PCM
    val audioFormat = buffer.getShort(20).toInt()
    assertEquals(1, audioFormat)

    // Channels (offset 22)
    val numChannels = buffer.getShort(22).toInt()
    assertEquals(channels, numChannels)

    // Sample rate (offset 24)
    val sRate = buffer.getInt(24)
    assertEquals(sampleRate, sRate)

    // Byte rate (offset 28): sampleRate * channels * 2
    val byteRate = buffer.getInt(28)
    assertEquals(sampleRate * channels * 2, byteRate)

    // Block align (offset 32): channels * 2
    val blockAlign = buffer.getShort(32).toInt()
    assertEquals(channels * 2, blockAlign)

    // Bits per sample (offset 34): 16
    val bitsPerSample = buffer.getShort(34).toInt()
    assertEquals(16, bitsPerSample)

    // data subchunk magic (offset 36..39)
    val dataTag = String(header, 36, 4)
    assertEquals("data", dataTag)

    // Subchunk2 size at offset 40
    val subchunk2Size = buffer.getInt(40).toLong() and 0xFFFFFFFFL
    assertEquals(dataBytes, subchunk2Size)
  }

  @Test
  fun testWavHeaderPatchingAndValidation() {
    val tempFile = File.createTempFile("test_audio", ".wav")
    try {
      val sampleRate = 44100
      val channels = 2
      val payloadSize = 88200 // 0.5s of stereo 16-bit PCM

      // Write placeholder 0 header then payload
      val placeholderHeader = WavHeaderHelper.createPcm16Header(sampleRate, channels, 0L)
      FileOutputStream(tempFile).use { fos ->
        fos.write(placeholderHeader)
        fos.write(ByteArray(payloadSize))
      }

      // Pre-patch: declared size is 0, file length is 44 + payloadSize -> validation should fail size match
      val prePatchInfo = WavHeaderHelper.validateWavFile(tempFile)
      assertFalse(prePatchInfo.isValid)

      // Patch the sizes
      WavHeaderHelper.patchWavSizes(tempFile)

      // Post-patch validation
      val postPatchInfo = WavHeaderHelper.validateWavFile(tempFile)
      assertTrue(postPatchInfo.errorMessage ?: "Expected valid", postPatchInfo.isValid)
      assertEquals(sampleRate, postPatchInfo.sampleRate)
      assertEquals(channels, postPatchInfo.channels)
      assertEquals(16, postPatchInfo.bitsPerSample)
      assertEquals(payloadSize.toLong(), postPatchInfo.dataSizeBytes)
      assertEquals(500L, postPatchInfo.durationMs) // 88200 / 176400 * 1000 = 500ms
    } finally {
      tempFile.delete()
    }
  }

  @Test
  fun testWavValidationRejectsCorruptHeaders() {
    val tempFile = File.createTempFile("corrupt_test", ".wav")
    try {
      // 1. File smaller than 44 bytes
      tempFile.writeBytes(ByteArray(20))
      assertFalse(WavHeaderHelper.validateWavFile(tempFile).isValid)

      // 2. Corrupt RIFF magic
      val header = WavHeaderHelper.createPcm16Header(44100, 2, 0)
      header[0] = 'B'.code.toByte()
      tempFile.writeBytes(header)
      val riffInfo = WavHeaderHelper.validateWavFile(tempFile)
      assertFalse(riffInfo.isValid)
      assertTrue(riffInfo.errorMessage?.contains("RIFF") == true)
    } finally {
      tempFile.delete()
    }
  }

  // ==========================================
  // 6. WavMath
  // ==========================================

  @Test
  fun testWavMathCalculations() {
    val sampleRate = 44100
    val channels = 2

    // 1 second of audio at 44100Hz stereo 16-bit: 44100 * 2 * 2 = 176,400 bytes
    val oneSecBytes = WavMath.durationToPcm16Bytes(1.0, sampleRate, channels)
    assertEquals(176400L, oneSecBytes)

    // With 44 bytes header
    val fullWavSize = WavMath.estimateWavSizeBytes(1.0, sampleRate, channels)
    assertEquals(176444L, fullWavSize)

    // Sample count to bytes: 44100 samples -> 176400 bytes
    val sampleBytes = WavMath.samplesToPcm16Bytes(44100L, channels)
    assertEquals(176400L, sampleBytes)

    // Byte count to duration
    val durSec = WavMath.pcm16BytesToDurationSec(176400L, sampleRate, channels)
    assertEquals(1.0, durSec, 0.0001)

    val durMs = WavMath.pcm16BytesToDurationMs(176400L, sampleRate, channels)
    assertEquals(1000L, durMs)

    // 3.8 GB WAV limit check
    assertFalse(WavMath.exceedsWavLimit(3_799_999_999L))
    assertFalse(WavMath.exceedsWavLimit(3_800_000_000L))
    assertTrue(WavMath.exceedsWavLimit(3_800_000_001L))
    assertTrue(WavMath.exceedsWavLimit(4_000_000_000L))
  }

  // ==========================================
  // 7. PcmConversionHelper
  // ==========================================

  @Test
  fun testPcmConversionValuesAndClipping() {
    // 0.0f -> 0x0000
    val zeroShort = PcmConversionHelper.convertSingleFloatToShort(0.0f)
    assertEquals(0.toShort(), zeroShort)
    val (zeroLow, zeroHigh) = PcmConversionHelper.convertSingleFloatTo16Bit(0.0f)
    assertEquals(0.toByte(), zeroLow)
    assertEquals(0.toByte(), zeroHigh)

    // 1.0f -> 0x7FFF (32767)
    val maxShort = PcmConversionHelper.convertSingleFloatToShort(1.0f)
    assertEquals(32767.toShort(), maxShort)
    val (maxLow, maxHigh) = PcmConversionHelper.convertSingleFloatTo16Bit(1.0f)
    assertEquals(0xFF.toByte(), maxLow)
    assertEquals(0x7F.toByte(), maxHigh)

    // -1.0f -> 0x8000 (-32768)
    val minShort = PcmConversionHelper.convertSingleFloatToShort(-1.0f)
    assertEquals((-32768).toShort(), minShort)
    val (minLow, minHigh) = PcmConversionHelper.convertSingleFloatTo16Bit(-1.0f)
    assertEquals(0x00.toByte(), minLow)
    assertEquals(0x80.toByte(), minHigh)

    // Clipping above 1.0f -> 0x7FFF
    assertEquals(32767.toShort(), PcmConversionHelper.convertSingleFloatToShort(1.5f))
    assertEquals(32767.toShort(), PcmConversionHelper.convertSingleFloatToShort(10.0f))

    // Clipping below -1.0f -> 0x8000 (-32768)
    assertEquals((-32768).toShort(), PcmConversionHelper.convertSingleFloatToShort(-1.5f))
    assertEquals((-32768).toShort(), PcmConversionHelper.convertSingleFloatToShort(-50.0f))

    // Buffer conversion test
    val floatBuffer = ByteBuffer.allocate(3 * 4).order(ByteOrder.nativeOrder())
    floatBuffer.putFloat(0.0f)
    floatBuffer.putFloat(1.0f)
    floatBuffer.putFloat(-1.0f)
    floatBuffer.flip()

    val outBytes = ByteArray(6)
    val bytesWritten = PcmConversionHelper.convertFloatTo16Bit(floatBuffer, outBytes, 3)
    assertEquals(6, bytesWritten)

    val outBuffer = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)
    assertEquals(0.toShort(), outBuffer.getShort())
    assertEquals(32767.toShort(), outBuffer.getShort())
    assertEquals((-32768).toShort(), outBuffer.getShort())
  }

  // ==========================================
  // 8. DurationToleranceChecker
  // ==========================================

  @Test
  fun testDurationToleranceChecker() {
    // Under 30,000 ms: 300 ms is greater than 1%
    val shortTolerance = DurationToleranceChecker.calculateToleranceMs(10_000L) // 1% is 100ms
    assertEquals(300L, shortTolerance)

    // Over 30,000 ms: 1% dominates
    val longTolerance = DurationToleranceChecker.calculateToleranceMs(60_000L) // 1% is 600ms
    assertEquals(600L, longTolerance)

    val oneHourTolerance = DurationToleranceChecker.calculateToleranceMs(3_600_000L) // 1% is 36,000ms
    assertEquals(36_000L, oneHourTolerance)

    // Pass cases
    assertTrue(DurationToleranceChecker.isWithinTolerance(10_250L, 10_000L)) // +250ms <= 300ms
    assertTrue(DurationToleranceChecker.isWithinTolerance(9_750L, 10_000L))  // -250ms <= 300ms
    assertTrue(DurationToleranceChecker.isWithinTolerance(60_500L, 60_000L)) // +500ms <= 600ms

    // Fail cases
    assertFalse(DurationToleranceChecker.isWithinTolerance(10_350L, 10_000L)) // +350ms > 300ms
    assertFalse(DurationToleranceChecker.isWithinTolerance(9_650L, 10_000L))  // -350ms > 300ms
    assertFalse(DurationToleranceChecker.isWithinTolerance(60_700L, 60_000L)) // +700ms > 600ms
  }

  // ==========================================
  // 9. MediaErrorMapper
  // ==========================================

  @Test
  fun testMediaErrorMapperMessages() {
    // Security / File not found
    val secMsg = MediaErrorMapper.mapErrorToUserFriendlyMessage(SecurityException("Permission denied"))
    assertTrue(secMsg.contains("Cannot read selected file"))

    val fnfMsg = MediaErrorMapper.mapErrorToUserFriendlyMessage(FileNotFoundException("File missing"))
    assertTrue(fnfMsg.contains("Cannot read selected file"))

    // No audio
    val noAudioMsg = MediaErrorMapper.mapErrorToUserFriendlyMessage(IllegalStateException("Input has no audio"))
    assertTrue(noAudioMsg.contains("no audio track"))

    // Unsupported codec
    val codecMsg = MediaErrorMapper.mapErrorToUserFriendlyMessage(IllegalStateException("Unsupported codec: video/hevc"))
    assertTrue(codecMsg.contains("Unsupported codec"))

    // Out of memory
    val oomMsg = MediaErrorMapper.mapErrorToUserFriendlyMessage(OutOfMemoryError("Failed to allocate"))
    assertTrue(oomMsg.contains("Out of memory"))

    // Storage
    val storageMsg = MediaErrorMapper.mapErrorToUserFriendlyMessage(IOException("Not enough storage space"))
    assertTrue(storageMsg.contains("storage space"))

    // Cancellation
    val cancelMsg = MediaErrorMapper.mapErrorToUserFriendlyMessage(IllegalStateException("Job was cancelled by user"))
    assertTrue(cancelMsg.contains("cancelled"))

    // Interrupted
    val interruptedMsg = MediaErrorMapper.mapErrorToUserFriendlyMessage(IllegalStateException("Job interrupted by shutdown"))
    assertTrue(interruptedMsg.contains("TOOLSNEST was closed"))

    // Generic
    val genericMsg = MediaErrorMapper.mapErrorToUserFriendlyMessage(RuntimeException("Random glitch"))
    assertTrue(genericMsg.contains("Processing failed"))
  }

  @Test
  fun testMediaErrorMapperTechnicalDetailsExcludesPrivateData() {
    val exception = IllegalArgumentException("Decoder init failed for mime audio/mp4a-latm")
    val details = MediaErrorMapper.extractTechnicalDetails(exception)
    assertEquals("IllegalArgumentException: Decoder init failed for mime audio/mp4a-latm", details)

    val nullDetails = MediaErrorMapper.extractTechnicalDetails(null)
    assertEquals("Unknown error", nullDetails)
  }
}
