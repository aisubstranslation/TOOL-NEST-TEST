package com.example.bgremove

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.FloatBuffer

class ModelMissingException(message: String = "Model file missing") : IOException(message)
class ModelFormatException(message: String = "Model format not supported") : IOException(message)

object U2NetRunner {

  const val MODEL_ASSET_PATH = "models/u2netp.onnx"
  const val EXPECTED_INPUT_SIZE = 320

  /**
   * Checks if the U2-Net model asset exists.
   */
  fun isModelAssetPresent(context: Context): Boolean {
    return try {
      context.assets.open(MODEL_ASSET_PATH).use { true }
    } catch (_: Exception) {
      false
    }
  }

  /**
   * Loads the model bytes from assets or cache.
   */
  private fun loadModelBytes(context: Context): ByteArray {
    return try {
      context.assets.open(MODEL_ASSET_PATH).use { input ->
        input.readBytes()
      }
    } catch (e: FileNotFoundException) {
      throw ModelMissingException("Model file missing")
    } catch (e: Exception) {
      throw ModelMissingException("Model file missing: ${e.message}")
    }
  }

  /**
   * Runs U2-Net inference on the preprocessed 320x320 float tensor [1, 3, 320, 320].
   * Returns raw float array of the first output map.
   */
  suspend fun runInference(
    context: Context,
    preprocessedTensor: FloatArray
  ): FloatArray = withContext(Dispatchers.Default) {
    var env: OrtEnvironment? = null
    var session: OrtSession? = null
    var inputTensor: OnnxTensor? = null
    var results: OrtSession.Result? = null

    try {
      val modelBytes = loadModelBytes(context)

      env = OrtEnvironment.getEnvironment()
      val sessionOptions = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(2)
      }
      session = env.createSession(modelBytes, sessionOptions)

      val inputName = session.inputNames.firstOrNull()
        ?: throw ModelFormatException("Model has no input")
      val outputName = session.outputNames.firstOrNull()
        ?: throw ModelFormatException("Model has no output")

      // Verify output shape metadata if available
      val outputInfo = session.outputInfo[outputName]?.info as? TensorInfo
      if (outputInfo != null) {
        val shape = outputInfo.shape
        // Expected shape [1, 1, 320, 320] or [-1, 1, 320, 320]
        val has320 = shape.any { it == 320L }
        if (!has320 && shape.isNotEmpty()) {
          throw ModelFormatException("Model format not supported")
        }
      }

      // Create OnnxTensor from FloatBuffer with shape [1, 3, 320, 320]
      val shape = longArrayOf(1, 3, EXPECTED_INPUT_SIZE.toLong(), EXPECTED_INPUT_SIZE.toLong())
      val floatBuffer = FloatBuffer.wrap(preprocessedTensor)
      inputTensor = OnnxTensor.createTensor(env, floatBuffer, shape)

      results = session.run(mapOf(inputName to inputTensor))

      val rawOutput = results.get(0)?.value
      val outputFloats = extractFloatArray(rawOutput)
        ?: throw ModelFormatException("Model format not supported")

      if (outputFloats.size != EXPECTED_INPUT_SIZE * EXPECTED_INPUT_SIZE) {
        throw ModelFormatException("Model format not supported: unexpected output length ${outputFloats.size}")
      }

      outputFloats
    } finally {
      try { results?.close() } catch (_: Exception) {}
      try { inputTensor?.close() } catch (_: Exception) {}
      try { session?.close() } catch (_: Exception) {}
      try { env?.close() } catch (_: Exception) {}
    }
  }

  /**
   * Safely unpacks multidimensional float arrays from ONNX Runtime (e.g. Array<Array<Array<FloatArray>>>).
   */
  @Suppress("UNCHECKED_CAST")
  fun extractFloatArray(raw: Any?): FloatArray? {
    if (raw == null) return null

    val totalElements = EXPECTED_INPUT_SIZE * EXPECTED_INPUT_SIZE
    val flat = FloatArray(totalElements)
    var index = 0

    fun flatten(obj: Any?) {
      when (obj) {
        is FloatArray -> {
          for (f in obj) {
            if (index < totalElements) {
              flat[index++] = f
            }
          }
        }
        is Array<*> -> {
          for (item in obj) {
            flatten(item)
          }
        }
        is Float -> {
          if (index < totalElements) {
            flat[index++] = obj
          }
        }
      }
    }

    flatten(raw)
    return if (index == totalElements) flat else null
  }
}
