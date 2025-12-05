package com.example.myapplication

import android.net.Uri
import android.webkit.MimeTypeMap

/**
 * 媒体类型枚举
 * 用于区分图片和视频文件
 */
enum class MediaType {
    IMAGE,
    VIDEO,
    UNKNOWN
}

/**
 * 根据 URI 判断媒体类型
 */
fun Uri.getMediaType(context: android.content.Context): MediaType {
    val mimeType = getMimeType(context, this)
    return when {
        mimeType?.startsWith("image/") == true -> MediaType.IMAGE
        mimeType?.startsWith("video/") == true -> MediaType.VIDEO
        else -> {
            // 尝试从文件扩展名判断
            val path = toString().lowercase()
            when {
                path.endsWith(".jpg") || path.endsWith(".jpeg") || 
                path.endsWith(".png") || path.endsWith(".gif") || 
                path.endsWith(".webp") || path.endsWith(".bmp") -> MediaType.IMAGE
                path.endsWith(".mp4") || path.endsWith(".avi") || 
                path.endsWith(".mov") || path.endsWith(".mkv") || 
                path.endsWith(".3gp") || path.endsWith(".webm") -> MediaType.VIDEO
                else -> MediaType.UNKNOWN
            }
        }
    }
}

/**
 * 获取 URI 的 MIME 类型
 */
fun getMimeType(context: android.content.Context, uri: Uri): String? {
    return when (uri.scheme) {
        "content" -> {
            context.contentResolver.getType(uri)
        }
        "file" -> {
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.path)
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
        else -> null
    }
}

