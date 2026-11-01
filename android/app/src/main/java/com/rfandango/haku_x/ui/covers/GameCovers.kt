package com.rfandango.haku_x.ui.library

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlin.math.max

/**
 * The covers the user picked for their games: each one copied, scaled down, into the app's own
 * files (covers/), by the game's key (GameOverrides.gameKey). Every call blocks: off the main thread.
 */
class GameCovers(context: Context) {
  private val dir = File(context.filesDir, "covers")

  private fun file(key: String) = File(dir, key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".jpg")

  fun load(key: String): ImageBitmap? {
    val f = file(key)
    if (!f.isFile) return null
    val bitmap = BitmapFactory.decodeFile(f.path) ?: return null
    bitmap.prepareToDraw()
    return bitmap.asImageBitmap()
  }

  /** Reads the image at uri, scales it to MAX_SIDE, and keeps it as the game's cover. */
  fun save(resolver: ContentResolver, uri: Uri, key: String): Boolean {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
      BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return false
    val longest = max(decoded.width, decoded.height)
    val scaled = if (longest > MAX_SIDE) {
      val ratio = MAX_SIDE.toFloat() / longest
      Bitmap.createScaledBitmap(decoded, (decoded.width * ratio).toInt().coerceAtLeast(1), (decoded.height * ratio).toInt().coerceAtLeast(1), true)
    } else {
      decoded
    }
    dir.mkdirs()
    val target = file(key)
    val temp = File(dir, target.name + ".tmp")
    val written = temp.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    return written && temp.renameTo(target)
  }

  fun delete(key: String) {
    file(key).delete()
  }

  private companion object {
    const val MAX_SIDE = 800
  }
}