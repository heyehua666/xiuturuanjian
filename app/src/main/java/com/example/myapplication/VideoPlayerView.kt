package com.example.myapplication

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 视频播放器组件
 * 用于预览 MP4 视频文件
 */
@Composable
fun VideoPlayerView(
    videoUri: Uri,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    looping: Boolean = true
) {
    val context = LocalContext.current
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setVideoURI(videoUri)
                currentUri = videoUri
                setOnPreparedListener { mediaPlayer ->
                    mediaPlayer.isLooping = looping
                    if (autoPlay) {
                        start()
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { videoView ->
            // 当 URI 改变时更新视频
            if (currentUri != videoUri) {
                videoView.setVideoURI(videoUri)
                currentUri = videoUri
                if (autoPlay) {
                    videoView.start()
                }
            }
        }
    )
    
    // 组件销毁时释放资源
    DisposableEffect(videoUri) {
        onDispose {
            // VideoView 会在其生命周期结束时自动释放资源
        }
    }
}

