package com.example.myapplication

import android.graphics.Bitmap
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix as GLMatrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 滤镜类型
enum class FilterType {
    NONE, BLACK_WHITE, VINTAGE, WARM, COOL, BRIGHT, CONTRAST, SATURATE
}

data class EditorState(
    val rotation: Float,
    val scale: Float,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val cropRect: Rect? = null
)

// 编辑器整体界面（单页）
@Composable
fun ImageEditorScreen(
    selectedImageUri: Uri?,
    onSelectImage: () -> Unit,
    showImageSearch: Boolean,
    onOpenImageSearch: () -> Unit,
    onDismissImageSearch: () -> Unit,
    onImageSelectedFromSearch: (Uri) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 编辑状态
    var rotation by rememberSaveable { mutableStateOf(0f) }
    var scale by rememberSaveable { mutableStateOf(1f) }
    var offsetX by rememberSaveable { mutableStateOf(0f) }
    var offsetY by rememberSaveable { mutableStateOf(0f) }
    var currentFilter by rememberSaveable { mutableStateOf(FilterType.NONE) }
    var showCropMode by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }

    // 裁剪框状态
    var cropRect by remember { mutableStateOf(Rect(0f, 0f, 0f, 0f)) }
    var canvasSize by remember { mutableStateOf(Size(0f, 0f)) }

    val history = remember { mutableStateListOf<EditorState>() }

    fun currentCropSnapshot(): Rect? =
        if (cropRect.width > 0f && cropRect.height > 0f) {
            Rect(cropRect.left, cropRect.top, cropRect.right, cropRect.bottom)
        } else {
            null
        }

    fun pushHistorySnapshot() {
        val snapshot = EditorState(
            rotation = rotation,
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            cropRect = currentCropSnapshot()
        )
        // 避免重复快照堆积：只有和最后一个不同才入栈
        if (history.isEmpty() || history.last() != snapshot) {
            history.add(snapshot)
        }
    }

    fun undo() {
        if (history.isNotEmpty()) {
            val lastIndex = history.lastIndex
            val last = history[lastIndex]
            history.removeAt(lastIndex)
            rotation = last.rotation
            scale = last.scale
            offsetX = last.offsetX
            offsetY = last.offsetY
            cropRect = last.cropRect ?: Rect(0f, 0f, 0f, 0f)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TopHeaderWithEffects(
            onSelectImage = onSelectImage,
            onOpenImageSearch = onOpenImageSearch
        )

        // 编辑画布区域（带渐变边框 + 发光）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = Color(0xFF66CCFF),
                    spotColor = Color(0xFF66CCFF)
                )
                .background(
                    brush = Brush.linearGradient(
                        listOf(
                            Color(0xFFFFD700),
                            Color(0xFFFFA500),
                            Color(0xFFB388FF)
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF050A1A))
                    .onSizeChanged { size ->
                        canvasSize = Size(size.width.toFloat(), size.height.toFloat())
                    },
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageUri != null) {
                    // 判断是图片还是视频
                    val mediaType = selectedImageUri.getMediaType(context)
                    
                    if (mediaType == MediaType.VIDEO) {
                        // 视频预览模式
                        VideoPlayerView(
                            videoUri = selectedImageUri,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp)),
                            autoPlay = true,
                            looping = true
                        )
                    } else {
                        // 图片编辑模式
                        val imageModifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    // 只有真的发生平移或缩放时才记录历史
                                    if (pan != Offset.Zero || zoom != 1f) {
                                        // 在这一次"步骤"生效前先记录当前状态，形成一个撤销节点
                                        pushHistorySnapshot()
                                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    }
                                }
                            }

                        Box(
                            modifier = imageModifier
                        ) {
                            OpenGLImageCanvas(
                                imageUri = selectedImageUri,
                                rotation = rotation,
                                scale = scale,
                                offsetX = offsetX,
                                offsetY = offsetY,
                                filter = currentFilter,
                                modifier = Modifier.fillMaxSize()
                            )

                            // 渐变遮罩，增加金属高光质感
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.08f),
                                                Color.Transparent,
                                                Color(0xFF66CCFF).copy(alpha = 0.2f)
                                            )
                                        )
                                    )
                            )

                            // 裁剪框
                            if (showCropMode) {
                                CropOverlay(
                                    cropRect = cropRect,
                                    onCropRectChange = { cropRect = it },
                                    onCropInteractionStart = { pushHistorySnapshot() }
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = Color(0xFF9FA8DA),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "从相册选择图片或视频",
                            fontSize = 14.sp,
                            color = Color(0xFFCFD8DC)
                        )
                        GradientOutlineButton(
                            text = "从相册选择",
                            icon = Icons.Default.Palette,
                            onClick = onSelectImage
                        )
                    }
                }
            }
        }

        // 滤镜选择栏
        if (showFilters) {
            FilterSelector(
                currentFilter = currentFilter,
                onFilterSelected = { currentFilter = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        // 工具栏：裁剪 / 旋转 / 撤销 / 滤镜（炫酷渐变按钮）
        // 只在选择图片时显示编辑工具，视频只预览
        val isVideo = selectedImageUri?.getMediaType(context) == MediaType.VIDEO
        if (!isVideo) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GradientToolButton(
                    modifier = Modifier.weight(1f),
                    text = "裁剪",
                    icon = Icons.Default.Palette,
                    enabled = selectedImageUri != null
                ) {
                    if (selectedImageUri != null) {
                        showCropMode = !showCropMode
                        showFilters = false
                    }
                }

                GradientToolButton(
                    modifier = Modifier.weight(1f),
                    text = "旋转",
                    icon = Icons.Default.Palette,
                    enabled = selectedImageUri != null
                ) {
                    if (selectedImageUri != null) {
                        pushHistorySnapshot()
                        rotation = (rotation + 90f) % 360f
                        showCropMode = false
                        showFilters = false
                    }
                }

                GradientToolButton(
                    modifier = Modifier.weight(1f),
                    text = "滤镜",
                    icon = Icons.Default.Palette,
                    enabled = selectedImageUri != null
                ) {
                    if (selectedImageUri != null) {
                        showFilters = !showFilters
                        showCropMode = false
                    }
                }

                GradientToolButton(
                    modifier = Modifier.weight(1f),
                    text = "撤销",
                    icon = Icons.Default.Palette,
                    enabled = history.isNotEmpty()
                ) {
                    undo()
                }
            }

            // 保存按钮（仅图片）
            if (selectedImageUri != null) {
                GradientToolButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = "保存图片",
                    icon = Icons.Default.Save,
                    enabled = true
                ) {
                    scope.launch {
                        saveImageToGallery(
                            context = context,
                            imageUri = selectedImageUri,
                            rotation = rotation,
                            scale = scale,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            filter = currentFilter,
                            cropRect = if (showCropMode && cropRect.width > 0f) cropRect else null,
                            canvasSize = canvasSize
                        )
                    }
                }
            }
        }

        // 图片搜索选择弹层
        if (showImageSearch) {
            ImageSearchDialog(
                onDismiss = onDismissImageSearch,
                onImageSelected = onImageSelectedFromSearch
            )
        }
    }
}

// 使用 OpenGL ES 绘制图片，实现与 AsyncImage + graphicsLayer 相同的旋转/缩放/平移和滤镜效果
@Composable
fun OpenGLImageCanvas(
    imageUri: Uri,
    rotation: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    filter: FilterType,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 通过 Coil 异步加载 Bitmap
    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(imageUri) {
        // 在后台线程使用 Coil 加载，并强制转为兼容 OpenGL 的 ARGB_8888 Bitmap
        withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(imageUri)
                    .build()
                val result = loader.execute(request).drawable
                // 统一走扩展函数 toBitmap()，内部已经处理 HARDWARE/非 ARGB_8888 等情况
                val bmp = result?.toBitmap()
                withContext(Dispatchers.Main) {
                    bitmap = bmp
                }
            } catch (_: Exception) {
                // 加载失败时忽略，保留空画布
            }
        }
    }

    // 记住一个 Renderer 实例，跨重组共用
    val renderer = remember {
        ImageGLRenderer()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(2)
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
            }
        },
        update = { view ->
            renderer.updateState(
                bitmap = bitmap,
                rotation = rotation,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                filter = filter
            )
            view.requestRender()
        }
    )
}

// OpenGL ES 渲染器：负责加载纹理、应用矩阵变换和滤镜
class ImageGLRenderer : GLSurfaceView.Renderer {
    @Volatile
    private var bitmap: Bitmap? = null

    @Volatile
    private var rotation: Float = 0f

    @Volatile
    private var scale: Float = 1f

    @Volatile
    private var offsetX: Float = 0f

    @Volatile
    private var offsetY: Float = 0f

    @Volatile
    private var filterType: FilterType = FilterType.NONE

    private var program = 0
    private var textureId = 0
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    private val mvpMatrix = FloatArray(16)

    // 顶点坐标（覆盖整个视口，NDC）
    private val vertexCoords = floatArrayOf(
        -1f, 1f,
        -1f, -1f,
        1f, 1f,
        1f, -1f
    )

    // 纹理坐标
    private val texCoords = floatArrayOf(
        0f, 0f,
        0f, 1f,
        1f, 0f,
        1f, 1f
    )

    private var vertexBuffer = java.nio.ByteBuffer
        .allocateDirect(vertexCoords.size * 4)
        .order(java.nio.ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(vertexCoords).position(0) }

    private var texBuffer = java.nio.ByteBuffer
        .allocateDirect(texCoords.size * 4)
        .order(java.nio.ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(texCoords).position(0) }

    fun updateState(
        bitmap: Bitmap?,
        rotation: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        filter: FilterType
    ) {
        this.bitmap = bitmap
        this.rotation = rotation
        this.scale = scale
        this.offsetX = offsetX
        this.offsetY = offsetY
        this.filterType = filter
    }

    override fun onSurfaceCreated(
        unused: javax.microedition.khronos.opengles.GL10?,
        config: javax.microedition.khronos.egl.EGLConfig?
    ) {
        GLES20.glClearColor(0.02f, 0.04f, 0.09f, 1f)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        textureId = createTexture()
    }

    override fun onSurfaceChanged(
        unused: javax.microedition.khronos.opengles.GL10?,
        width: Int,
        height: Int
    ) {
        surfaceWidth = width
        surfaceHeight = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(unused: javax.microedition.khronos.opengles.GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val bmp = bitmap ?: return

        // 每一帧都确保纹理数据最新（简单起见，不做复杂缓存）
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)

        GLES20.glUseProgram(program)

        val aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        val aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        val uMvpMatrix = GLES20.glGetUniformLocation(program, "uMvpMatrix")
        val uFilterType = GLES20.glGetUniformLocation(program, "uFilterType")

        // 计算与 Compose graphicsLayer 大致一致的矩阵
        GLMatrix.setIdentityM(mvpMatrix, 0)

        // 偏移：Compose 中 offsetX/offsetY 是以 px 为单位，换算到 NDC
        if (surfaceWidth > 0 && surfaceHeight > 0) {
            val tx = 2f * offsetX / surfaceWidth
            val ty = -2f * offsetY / surfaceHeight
            GLMatrix.translateM(mvpMatrix, 0, tx, ty, 0f)
        }

        // 旋转
        GLMatrix.rotateM(mvpMatrix, 0, rotation, 0f, 0f, 1f)

        // 缩放
        GLMatrix.scaleM(mvpMatrix, 0, scale, scale, 1f)

        GLES20.glUniformMatrix4fv(uMvpMatrix, 1, false, mvpMatrix, 0)
        GLES20.glUniform1i(uFilterType, filterType.ordinal)

        // 顶点数据
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(
            aPosition,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            vertexBuffer
        )

        // 纹理坐标
        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glVertexAttribPointer(
            aTexCoord,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            texBuffer
        )

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aTexCoord)
    }

    private fun createTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val id = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexParameterf(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )
        GLES20.glTexParameterf(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )
        GLES20.glTexParameterf(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE.toFloat()
        )
        GLES20.glTexParameterf(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE.toFloat()
        )
        return id
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        return program
    }

    companion object {
        // 顶点着色器：应用 MVP 矩阵
        private const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            uniform mat4 uMvpMatrix;
            void main() {
                vTexCoord = aTexCoord;
                gl_Position = uMvpMatrix * vec4(aPosition, 0.0, 1.0);
            }
        """

        // 片元着色器：根据 FilterType 实现与 ColorMatrix 大致一致的滤镜效果
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            uniform int uFilterType;

            void main() {
                vec4 color = texture2D(uTexture, vTexCoord);

                if (uFilterType == 1) {
                    // BLACK_WHITE
                    float g = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                    color = vec4(vec3(g), color.a);
                } else if (uFilterType == 2) {
                    // VINTAGE：降低饱和度
                    float g = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                    color.rgb = mix(vec3(g), color.rgb, 0.4);
                } else if (uFilterType == 3) {
                    // WARM：增强红色和整体亮度
                    color.r *= 1.2;
                    color.g *= 1.1;
                    color.b *= 0.95;
                } else if (uFilterType == 4) {
                    // COOL：增强蓝色
                    color.r *= 0.95;
                    color.b *= 1.2;
                } else if (uFilterType == 5) {
                    // BRIGHT：整体提亮
                    color.rgb *= 1.3;
                } else if (uFilterType == 6) {
                    // CONTRAST：简单提升对比
                    color.rgb = (color.rgb - 0.5) * 1.15 + 0.5;
                } else if (uFilterType == 7) {
                    // SATURATE：提高饱和度
                    float g = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                    color.rgb = mix(vec3(g), color.rgb, 2.0);
                }

                gl_FragColor = color;
            }
        """
    }
}

// 裁剪框拖拽类型（只允许拖动四条边）
enum class CropDragType {
    NONE, LEFT, RIGHT, TOP, BOTTOM
}

// 裁剪框覆盖层
@Composable
fun CropOverlay(
    cropRect: Rect,
    onCropRectChange: (Rect) -> Unit,
    onCropInteractionStart: () -> Unit = {}
) {
    val density = LocalDensity.current
    var dragType by remember { mutableStateOf(CropDragType.NONE) }
    var initialRect by remember { mutableStateOf<Rect?>(null) }
    var canvasSize by remember { mutableStateOf(Size(0f, 0f)) }
    val touchThreshold = with(density) { 30.dp.toPx() }

    val latestCrop by rememberUpdatedState(cropRect)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                canvasSize = Size(size.width.toFloat(), size.height.toFloat())
            }
            .pointerInput(canvasSize) {
                if (canvasSize.width == 0f || canvasSize.height == 0f) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val currentRect =
                            if (latestCrop.width > 0f && latestCrop.height > 0f) {
                                latestCrop
                            } else {
                                // 如果还没有裁剪框，就以当前画布边界作为初始框
                                Rect(0f, 0f, canvasSize.width, canvasSize.height)
                            }
                        initialRect = currentRect

                        dragType = when {
                            kotlin.math.abs(offset.x - currentRect.left) < touchThreshold &&
                                offset.y in currentRect.top..currentRect.bottom -> CropDragType.LEFT
                            kotlin.math.abs(offset.x - currentRect.right) < touchThreshold &&
                                offset.y in currentRect.top..currentRect.bottom -> CropDragType.RIGHT
                            kotlin.math.abs(offset.y - currentRect.top) < touchThreshold &&
                                offset.x in currentRect.left..currentRect.right -> CropDragType.TOP
                            kotlin.math.abs(offset.y - currentRect.bottom) < touchThreshold &&
                                offset.x in currentRect.left..currentRect.right -> CropDragType.BOTTOM
                            else -> CropDragType.NONE
                        }

                        if (dragType == CropDragType.NONE) {
                            initialRect = null
                        } else {
                            onCropInteractionStart()
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (dragType == CropDragType.NONE) {
                            return@detectDragGestures
                        }
                        change.consumeAllChanges()
                        initialRect?.let { rect ->
                            val newRect = when (dragType) {
                                CropDragType.LEFT -> {
                                    val newLeft = (rect.left + dragAmount.x)
                                        .coerceIn(0f, rect.right - 100f)
                                    Rect(newLeft, rect.top, rect.right, rect.bottom)
                                }

                                CropDragType.RIGHT -> {
                                    val newRight = (rect.right + dragAmount.x)
                                        .coerceIn(rect.left + 100f, canvasSize.width)
                                    Rect(rect.left, rect.top, newRight, rect.bottom)
                                }

                                CropDragType.TOP -> {
                                    val newTop = (rect.top + dragAmount.y)
                                        .coerceIn(0f, rect.bottom - 100f)
                                    Rect(rect.left, newTop, rect.right, rect.bottom)
                                }

                                CropDragType.BOTTOM -> {
                                    val newBottom = (rect.bottom + dragAmount.y)
                                        .coerceIn(rect.top + 100f, canvasSize.height)
                                    Rect(rect.left, rect.top, rect.right, newBottom)
                                }

                                else -> rect
                            }
                            onCropRectChange(newRect)
                            initialRect = newRect
                        }
                    },
                    onDragEnd = {
                        dragType = CropDragType.NONE
                        initialRect = null
                    }
                )
            }
    ) {
        // 使用 canvasSize 来初始化裁剪框尺寸
        val canvasWidth = if (canvasSize.width > 0f) canvasSize.width else 1000f
        val canvasHeight = if (canvasSize.height > 0f) canvasSize.height else 1000f

        // 初始化裁剪框（如果为空，默认等于画布边界）
        val rect = if (cropRect.width == 0f || cropRect.height == 0f) {
            Rect(0f, 0f, canvasWidth, canvasHeight)
        } else {
            cropRect
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            // 绘制半透明遮罩
            val path = Path().apply {
                addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
                addRect(rect)
                fillType = PathFillType.EvenOdd
            }
            drawPath(path, Color.Black.copy(alpha = 0.5f))

            // 绘制裁剪框边框
            val strokeWidth = with(density) { 3.dp.toPx() }
            drawRect(
                color = Color(0xFF00E5FF),
                topLeft = rect.topLeft,
                size = rect.size,
                style = Stroke(width = strokeWidth)
            )

            // 绘制边缘拖拽手柄
            val handleLength = with(density) { 48.dp.toPx() }
            val handleThickness = with(density) { 4.dp.toPx() }

            fun edgeColor(edge: CropDragType) =
                if (dragType == edge) Color(0xFFFFD700) else Color(0xFF00E5FF)

            // 顶部
            drawRect(
                color = edgeColor(CropDragType.TOP),
                topLeft = Offset(
                    rect.center.x - handleLength / 2,
                    rect.top - handleThickness / 2
                ),
                size = Size(handleLength, handleThickness)
            )
            // 底部
            drawRect(
                color = edgeColor(CropDragType.BOTTOM),
                topLeft = Offset(
                    rect.center.x - handleLength / 2,
                    rect.bottom - handleThickness / 2
                ),
                size = Size(handleLength, handleThickness)
            )
            // 左侧
            drawRect(
                color = edgeColor(CropDragType.LEFT),
                topLeft = Offset(
                    rect.left - handleThickness / 2,
                    rect.center.y - handleLength / 2
                ),
                size = Size(handleThickness, handleLength)
            )
            // 右侧
            drawRect(
                color = edgeColor(CropDragType.RIGHT),
                topLeft = Offset(
                    rect.right - handleThickness / 2,
                    rect.center.y - handleLength / 2
                ),
                size = Size(handleThickness, handleLength)
            )
        }
    }
}

// 滤镜选择器
@Composable
fun FilterSelector(
    currentFilter: FilterType,
    onFilterSelected: (FilterType) -> Unit,
    modifier: Modifier = Modifier
) {
    val filters = listOf(
        FilterType.NONE to "原图",
        FilterType.BLACK_WHITE to "黑白",
        FilterType.VINTAGE to "复古",
        FilterType.WARM to "暖色",
        FilterType.COOL to "冷色",
        FilterType.BRIGHT to "明亮",
        FilterType.CONTRAST to "对比",
        FilterType.SATURATE to "饱和"
    )

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(filters) { (filter, name) ->
            val isSelected = filter == currentFilter

            Box(
                modifier = Modifier
                    .width(70.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = if (isSelected) {
                            Brush.linearGradient(
                                listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF))
                            )
                        } else {
                            Brush.linearGradient(
                                listOf(Color(0xFF2C2C2C), Color(0xFF1A1A1A))
                            )
                        }
                    )
                    .padding(8.dp)
                    .clickable { onFilterSelected(filter) },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else Color(0xFF888888),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = name,
                        fontSize = 11.sp,
                        color = if (isSelected) Color.White else Color(0xFF888888),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}


