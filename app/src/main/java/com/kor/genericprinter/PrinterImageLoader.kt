package com.kor.genericprinter.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

object PrinterImageLoader {

    fun fromBitmap(bitmap: Bitmap): Bitmap = bitmap

    fun fromDrawable(context: Context, resId: Int): Bitmap {
        return BitmapFactory.decodeResource(context.resources, resId)
            ?: throw IllegalArgumentException("Invalid drawable resource")
    }

    fun fromFile(file: File): Bitmap {
        return BitmapFactory.decodeFile(file.absolutePath)
            ?: throw IllegalArgumentException("Invalid image file")
    }

    fun fromFilePath(path: String): Bitmap {
        return BitmapFactory.decodeFile(path)
            ?: throw IllegalArgumentException("Invalid image path")
    }

    fun fromAssets(context: Context, assetName: String): Bitmap {
        context.assets.open(assetName).use {
            return BitmapFactory.decodeStream(it)
                ?: throw IllegalArgumentException("Invalid asset image")
        }
    }

    fun fromBytes(bytes: ByteArray): Bitmap {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("Invalid image bytes")
    }
}
