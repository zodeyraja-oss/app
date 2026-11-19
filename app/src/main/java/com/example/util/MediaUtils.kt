package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object MediaUtils {
    /**
     * Compresses an image picked via Android Photo Picker into a Base64 JPEG
     * small enough to encrypt and store safely inside a Firestore message.
     */
    suspend fun uriToCompressedBase64(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            val sampleSize = if (maxDim > 900) (maxDim / 720).coerceAtLeast(2) else 1

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return@withContext null

            val scaled = scaleDown(rawBitmap, 720)
            if (scaled != rawBitmap) rawBitmap.recycle()

            val baos = ByteArrayOutputStream()
            var quality = 78
            do {
                baos.reset()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                quality -= 12
            } while (baos.size() > 260_000 && quality >= 30)

            scaled.recycle()
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    fun base64ToBitmap(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxEdge && h <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / maxOf(w, h).toFloat()
        return Bitmap.createScaledBitmap(
            bitmap,
            (w * ratio).toInt().coerceAtLeast(1),
            (h * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    fun extractYouTubeVideoId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (Regex("^[a-zA-Z0-9_-]{11}$").matches(trimmed)) return trimmed

        val patterns = listOf(
            Regex("(?:v=|/v/|embed/|shorts/|live/)([a-zA-Z0-9_-]{11})"),
            Regex("youtu\\.be/([a-zA-Z0-9_-]{11})")
        )
        for (pattern in patterns) {
            val match = pattern.find(trimmed)
            val id = match?.groupValues?.getOrNull(1)
            if (!id.isNullOrBlank()) return id
        }
        return null
    }
}
