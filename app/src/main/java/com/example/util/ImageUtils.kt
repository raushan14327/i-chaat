package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageUtils {
    fun uriToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (originalBitmap == null) return null
            
            // Limit dimension to 250px for fast profile sync
            val maxDim = 250
            val width = originalBitmap.width
            val height = originalBitmap.height
            
            val scaledBitmap = if (width > maxDim || height > maxDim) {
                val ratio = width.toFloat() / height.toFloat()
                val (newWidth, newHeight) = if (width > height) {
                    (maxDim * ratio).toInt() to maxDim
                } else {
                    maxDim to (maxDim / ratio).toInt()
                }
                Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
            } else {
                originalBitmap
            }
            
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            val bytes = outputStream.toByteArray()
            Base64.encodeToString(bytes, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
