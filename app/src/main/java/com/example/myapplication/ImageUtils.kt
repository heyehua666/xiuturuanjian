package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 保存图片到相册
suspend fun saveImageToGallery(
    context: Context,
    imageUri: Uri,
    rotation: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    filter: FilterType,
    cropRect: androidx.compose.ui.geometry.Rect?,
    canvasSize: androidx.compose.ui.geometry.Size
) {
    withContext(Dispatchers.IO) {
        // 1. 使用Coil加载图片为Bitmap
        val imageLoader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(imageUri)
            .build()
        val drawable = imageLoader.execute(request).drawable
        val originalBitmap = drawable?.toBitmap() ?: return@withContext

        // 2. 应用旋转变换
        var processedBitmap = originalBitmap
        if (rotation != 0f) {
            val matrix = Matrix()
            matrix.postRotate(rotation)
            processedBitmap = Bitmap.createBitmap(
                originalBitmap,
                0,
                0,
                originalBitmap.width,
                originalBitmap.height,
                matrix,
                true
            )
            if (processedBitmap != originalBitmap) {
                originalBitmap.recycle()
            }
        }

        // 3. 计算图片在画布上的实际显示区域（ContentScale.Fit）
        val bitmapWidth = processedBitmap.width.toFloat()
        val bitmapHeight = processedBitmap.height.toFloat()
        val canvasWidth = canvasSize.width
        val canvasHeight = canvasSize.height

        // 计算适应画布的缩放比例（保持宽高比）
        val fitScale = minOf(canvasWidth / bitmapWidth, canvasHeight / bitmapHeight)
        val displayedWidth = bitmapWidth * fitScale
        val displayedHeight = bitmapHeight * fitScale

        // 计算图片在画布中的位置（居中）
        val imageLeft = (canvasWidth - displayedWidth) / 2f
        val imageTop = (canvasHeight - displayedHeight) / 2f

        // 4. 应用用户缩放和偏移后的实际显示区域
        val userScaledWidth = displayedWidth * scale
        val userScaledHeight = displayedHeight * scale
        val userImageLeft = imageLeft + offsetX
        val userImageTop = imageTop + offsetY

        // 5. 如果指定了裁剪区域，应用裁剪
        var finalBitmap = processedBitmap
        if (cropRect != null && cropRect.width > 0f && cropRect.height > 0f && canvasWidth > 0f && canvasHeight > 0f) {
            // 将裁剪区域从画布坐标转换为Bitmap坐标
            // 裁剪框相对于画布的位置
            val cropLeft = cropRect.left
            val cropTop = cropRect.top
            val cropRight = cropRect.right
            val cropBottom = cropRect.bottom

            // 计算裁剪框与图片显示区域的交集
            val intersectLeft = maxOf(cropLeft, userImageLeft)
            val intersectTop = maxOf(cropTop, userImageTop)
            val intersectRight = minOf(cropRight, userImageLeft + userScaledWidth)
            val intersectBottom = minOf(cropBottom, userImageTop + userScaledHeight)

            if (intersectRight > intersectLeft && intersectBottom > intersectTop) {
                // 将交集区域从画布坐标转换为Bitmap坐标
                val relativeLeft = (intersectLeft - userImageLeft) / userScaledWidth
                val relativeTop = (intersectTop - userImageTop) / userScaledHeight
                val relativeWidth = (intersectRight - intersectLeft) / userScaledWidth
                val relativeHeight = (intersectBottom - intersectTop) / userScaledHeight

                val cropX = (relativeLeft * bitmapWidth).toInt().coerceIn(0, processedBitmap.width)
                val cropY = (relativeTop * bitmapHeight).toInt().coerceIn(0, processedBitmap.height)
                val cropWidth =
                    (relativeWidth * bitmapWidth).toInt().coerceIn(1, processedBitmap.width - cropX)
                val cropHeight =
                    (relativeHeight * bitmapHeight).toInt().coerceIn(1, processedBitmap.height - cropY)

                finalBitmap = Bitmap.createBitmap(
                    processedBitmap,
                    cropX,
                    cropY,
                    cropWidth,
                    cropHeight
                )
                if (finalBitmap != processedBitmap) {
                    processedBitmap.recycle()
                }
            }
        } else {
            // 如果没有裁剪，应用用户缩放
            if (scale != 1f) {
                val scaledWidthInt = (bitmapWidth * scale).toInt().coerceAtLeast(1)
                val scaledHeightInt = (bitmapHeight * scale).toInt().coerceAtLeast(1)
                finalBitmap =
                    Bitmap.createScaledBitmap(processedBitmap, scaledWidthInt, scaledHeightInt, true)
                if (finalBitmap != processedBitmap) {
                    processedBitmap.recycle()
                }
            }
        }

        // 6. 应用滤镜
        var resultBitmap = finalBitmap
        if (filter != FilterType.NONE) {
            val filteredBitmap = applyFilterToBitmap(finalBitmap, filter)
            if (filteredBitmap != finalBitmap) {
                // 如果创建了新bitmap，需要回收旧的（如果不是原始bitmap）
                if (finalBitmap != processedBitmap && finalBitmap != originalBitmap) {
                    finalBitmap.recycle()
                }
                resultBitmap = filteredBitmap
            }
        }

        // 7. 保存到MediaStore
        val contentValues = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "edited_image_${System.currentTimeMillis()}.jpg"
            )
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/EditedImages")
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }
        }

        // 清理临时Bitmap（只回收我们创建的临时bitmap，不回收原始bitmap）
        if (resultBitmap != processedBitmap && resultBitmap != originalBitmap) {
            resultBitmap.recycle()
        }
    }
}

// 将Drawable转换为Bitmap（对同一 package 下所有文件可见）
fun android.graphics.drawable.Drawable.toBitmap(): Bitmap {
    if (this is android.graphics.drawable.BitmapDrawable) {
        val source = this.bitmap
        val needsSafeCopy =
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                source.config == Bitmap.Config.HARDWARE) ||
                source.config != Bitmap.Config.ARGB_8888 ||
                !source.isMutable

        return if (needsSafeCopy) {
            source.copy(Bitmap.Config.ARGB_8888, /* mutable = */ true)
        } else {
            source
        }
    }

    val safeWidth = intrinsicWidth.takeIf { it > 0 } ?: 1
    val safeHeight = intrinsicHeight.takeIf { it > 0 } ?: 1
    val bitmap = Bitmap.createBitmap(
        safeWidth,
        safeHeight,
        Bitmap.Config.ARGB_8888
    )
    val canvas = AndroidCanvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

// 应用滤镜到Bitmap
private fun applyFilterToBitmap(bitmap: Bitmap, filterType: FilterType): Bitmap {
    val filteredBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = AndroidCanvas(filteredBitmap)
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
    }

    val colorMatrix = android.graphics.ColorMatrix()
    when (filterType) {
        FilterType.BLACK_WHITE -> {
            colorMatrix.setSaturation(0f)
        }

        FilterType.VINTAGE -> {
            colorMatrix.setSaturation(0.4f)
        }

        FilterType.WARM -> {
            val scaleMatrix = android.graphics.ColorMatrix(
                floatArrayOf(
                    1.2f, 0f, 0f, 0f, 0f,
                    0f, 1.1f, 0f, 0f, 0f,
                    0f, 0f, 0.95f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            colorMatrix.postConcat(scaleMatrix)
        }

        FilterType.COOL -> {
            val scaleMatrix = android.graphics.ColorMatrix(
                floatArrayOf(
                    0.95f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1.2f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            colorMatrix.postConcat(scaleMatrix)
        }

        FilterType.BRIGHT -> {
            val scaleMatrix = android.graphics.ColorMatrix(
                floatArrayOf(
                    1.3f, 0f, 0f, 0f, 0f,
                    0f, 1.3f, 0f, 0f, 0f,
                    0f, 0f, 1.3f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            colorMatrix.postConcat(scaleMatrix)
        }

        FilterType.CONTRAST -> {
            colorMatrix.setSaturation(1.2f)
            val scaleMatrix = android.graphics.ColorMatrix(
                floatArrayOf(
                    1.15f, 0f, 0f, 0f, 0f,
                    0f, 1.15f, 0f, 0f, 0f,
                    0f, 0f, 1.15f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            colorMatrix.postConcat(scaleMatrix)
        }

        FilterType.SATURATE -> {
            colorMatrix.setSaturation(2f)
        }

        else -> return filteredBitmap
    }

    paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
    canvas.drawBitmap(bitmap, 0f, 0f, paint)

    return filteredBitmap
}


