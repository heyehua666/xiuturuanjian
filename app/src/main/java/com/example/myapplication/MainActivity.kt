package com.example.myapplication

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private var selectedImageUri by mutableStateOf<Uri?>(null)
    private var selectedBottomNav by mutableStateOf(0) // 0: 修图, 1: AI修图, 2: 我的
    private var hasReadImagesPermission by mutableStateOf(false)
    private var showImageSearch by mutableStateOf(false)
    
    // AI修图页面专用的图片URI（与普通修图页面分开管理）
    private var aiSelectedImageUri by mutableStateOf<Uri?>(null)
    private var aiShowImageSearch by mutableStateOf(false)

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            when (selectedBottomNav) {
                0 -> selectedImageUri = uri
                1 -> aiSelectedImageUri = uri
                else -> {}
            }
        }

    // 修图页：使用系统 Photo Picker 支持图片或视频（无需存储权限）
    private val pickEditorVisualMediaLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (selectedBottomNav == 0) {
                selectedImageUri = uri
            }
        }

    private val readImagesPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasReadImagesPermission = granted
            if (granted) {
                when (selectedBottomNav) {
                    0 -> showImageSearch = true
                    1 -> aiShowImageSearch = true
                    else -> {}
                }
            }
        }

    // 低版本（<33）使用 OpenDocument 选择图片或视频
    private val pickEditorOpenDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (selectedBottomNav == 0) {
                selectedImageUri = uri
                // 可选：持久化权限（如果需要长期访问）：
                // uri?.let { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化读取图片权限状态
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        hasReadImagesPermission =
            ContextCompat.checkSelfPermission(this, permission) == PermissionChecker.PERMISSION_GRANTED

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF050816)
                ) {
                    Scaffold(
                        bottomBar = {
                            BottomNavigationBar(selectedIndex = selectedBottomNav) { index ->
                                selectedBottomNav = index
                            }
                        },
                        containerColor = Color.Transparent
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color(0xFF050816),
                                            Color(0xFF090F2C),
                                            Color(0xFF1B2437)
                                        )
                                    )
                                )
                        ) {
                            when (selectedBottomNav) {
                                0 -> ImageEditorScreen(
                                    selectedImageUri = selectedImageUri,
                                    onSelectImage = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            pickEditorVisualMediaLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                            )
                                        } else {
                                            pickEditorOpenDocumentLauncher.launch(arrayOf("image/*", "video/*"))
                                        }
                                    },
                                    showImageSearch = showImageSearch,
                                    onOpenImageSearch = {
                                        if (hasReadImagesPermission) {
                                            showImageSearch = true
                                        } else {
                                            readImagesPermissionLauncher.launch(permission)
                                        }
                                    },
                                    onDismissImageSearch = { showImageSearch = false },
                                    onImageSelectedFromSearch = { uri ->
                                        selectedImageUri = uri
                                        showImageSearch = false
                                    }
                                )
                                1 -> AiEditorScreen(
                                    selectedImageUri = aiSelectedImageUri,
                                    onSelectImage = { pickImageLauncher.launch("image/*") },
                                    showImageSearch = aiShowImageSearch,
                                    onOpenImageSearch = {
                                        if (hasReadImagesPermission) {
                                            aiShowImageSearch = true
        } else {
                                            readImagesPermissionLauncher.launch(permission)
                                        }
                                    },
                                    onDismissImageSearch = { aiShowImageSearch = false },
                                    onImageSelectedFromSearch = { uri ->
                                        aiSelectedImageUri = uri
                                        aiShowImageSearch = false
                                    }
                                )
                                2 -> ProfileScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}
