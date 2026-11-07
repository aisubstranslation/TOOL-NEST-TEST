package com.example.tools.common

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import com.example.engine.ImageEngine
import com.example.file.FileLayer
import com.example.tools.crop.CropMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SelectionUpdateResult(
  val updatedList: List<SelectedFileItem>,
  val addedCount: Int,
  val duplicateCount: Int,
  val overflowCount: Int
)

object ImageThumbnailCache {
  private val cache = object : LruCache<String, Bitmap>(30) {
    override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Bitmap?, newValue: Bitmap?) {
      if (evicted && oldValue != null && !oldValue.isRecycled && oldValue != newValue) {
        oldValue.recycle()
      }
    }
  }

  fun get(key: String): Bitmap? = synchronized(cache) { cache.get(key) }

  fun put(key: String, bitmap: Bitmap) {
    synchronized(cache) {
      cache.put(key, bitmap)
    }
  }

  fun clear() {
    synchronized(cache) {
      cache.evictAll()
    }
  }
}

object FileSelectionHelper {
  const val MAX_FILES = 20

  fun addFiles(
    current: List<SelectedFileItem>,
    incoming: List<SelectedFileItem>,
    maxLimit: Int = MAX_FILES
  ): SelectionUpdateResult {
    val existingUris = current.map { it.uri.toString() }.toHashSet()
    val result = current.toMutableList()
    var added = 0
    var duplicates = 0
    var overflow = 0

    for (item in incoming) {
      val uriStr = item.uri.toString()
      if (existingUris.contains(uriStr)) {
        duplicates++
      } else if (result.size >= maxLimit) {
        overflow++
      } else {
        result.add(item)
        existingUris.add(uriStr)
        added++
      }
    }

    return SelectionUpdateResult(
      updatedList = result,
      addedCount = added,
      duplicateCount = duplicates,
      overflowCount = overflow
    )
  }

  fun removeFile(
    current: List<SelectedFileItem>,
    itemToRemove: SelectedFileItem
  ): List<SelectedFileItem> {
    return current.filterNot { it.uri.toString() == itemToRemove.uri.toString() }
  }

  fun removeByUri(
    current: List<SelectedFileItem>,
    uri: Uri
  ): List<SelectedFileItem> {
    return current.filterNot { it.uri.toString() == uri.toString() }
  }

  /**
   * Loads metadata and file info for picked image URIs on Dispatchers.IO one by one.
   * Reports progress ("Reading 3 of 20") and reports per-file errors without stopping other files.
   * Enforces the maxLimit using addFiles.
   */
  suspend fun loadSelectedImages(
    context: Context,
    uris: List<Uri>,
    currentItems: List<SelectedFileItem>,
    maxLimit: Int = MAX_FILES,
    onProgress: ((current: Int, total: Int) -> Unit)? = null,
    onFileError: ((fileName: String, error: String) -> Unit)? = null
  ): SelectionUpdateResult = withContext(Dispatchers.IO) {
    val existingUris = currentItems.map { it.uri.toString() }.toHashSet()
    val newItems = mutableListOf<SelectedFileItem>()
    val total = uris.size

    for ((index, uri) in uris.withIndex()) {
      onProgress?.invoke(index + 1, total)

      if (existingUris.contains(uri.toString())) {
        continue
      }

      try {
        val fileInfo = FileLayer.getUriFileInfo(context, uri)
        val meta = ImageEngine.readImageMetadata(context, uri)
        val oriented = CropMath.orientedSize(meta.width, meta.height, meta.orientationDegrees)

        newItems.add(
          SelectedFileItem(
            uri = uri,
            name = fileInfo.displayName,
            sizeBytes = if (meta.sizeBytes > 0) meta.sizeBytes else fileInfo.sizeBytes,
            width = oriented.width,
            height = oriented.height
          )
        )
      } catch (e: Exception) {
        val displayName = runCatching { FileLayer.getUriFileInfo(context, uri).displayName }.getOrDefault("image")
        onFileError?.invoke(displayName, e.message ?: "Failed to read image")
      }
    }

    addFiles(currentItems, newItems, maxLimit)
  }

  /**
   * Loads FileInfo for picked URIs on Dispatchers.IO one by one.
   */
  suspend fun loadSelectedFileInfos(
    context: Context,
    uris: List<Uri>,
    onProgress: ((current: Int, total: Int) -> Unit)? = null
  ): List<SelectedFileItem> = withContext(Dispatchers.IO) {
    val list = mutableListOf<SelectedFileItem>()
    val total = uris.size
    for ((index, uri) in uris.withIndex()) {
      onProgress?.invoke(index + 1, total)
      runCatching {
        val info = FileLayer.getUriFileInfo(context, uri)
        list.add(
          SelectedFileItem(
            uri = uri,
            name = info.displayName,
            sizeBytes = info.sizeBytes
          )
        )
      }
    }
    list
  }
}
