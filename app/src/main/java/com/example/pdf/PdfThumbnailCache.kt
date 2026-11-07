package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PdfThumbnailCache(maxEntries: Int = 12) {

  private val cache = object : LruCache<String, Bitmap>(maxEntries) {
    override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Bitmap?, newValue: Bitmap?) {
      if (evicted && oldValue != null && !oldValue.isRecycled) {
        oldValue.recycle()
      }
    }
  }

  suspend fun getThumbnail(
    context: Context,
    pdfFile: File,
    pageIndex: Int,
    maxDimensionPx: Int = 300
  ): Bitmap? {
    val key = "${pdfFile.absolutePath}_${pdfFile.lastModified()}_page_$pageIndex"
    synchronized(cache) {
      val existing = cache.get(key)
      if (existing != null && !existing.isRecycled) {
        return existing
      }
    }

    val rendered = withContext(Dispatchers.IO) {
      PdfFileHelper.renderPageThumbnail(context, pdfFile, pageIndex, maxDimensionPx)
    }

    if (rendered != null) {
      synchronized(cache) {
        cache.put(key, rendered)
      }
    }

    return rendered
  }

  fun clear() {
    synchronized(cache) {
      cache.evictAll()
    }
  }
}
