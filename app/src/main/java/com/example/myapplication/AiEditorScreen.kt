package com.example.myapplication

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * AI 修图界面
 * 功能：选择图片 -> 输入文字指令 -> 调用 Qwen API 处理 -> 预览结果 -> 保存
 */
@Composable
fun AiEditorScreen(
    selectedImageUri: Uri?,
    onSelectImage: () -> Unit,
    showImageSearch: Boolean,
    onOpenImageSearch: () -> Unit,
    onDismissImageSearch: () -> Unit,
    onImageSelectedFromSearch: (Uri) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 状态管理
    var promptText by rememberSaveable { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 顶部标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AI 智能修图",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF5F5F5)
                )
                Text(
                    text = "输入文字指令，AI 帮你修图",
                    fontSize = 12.sp,
                    color = Color(0xFFB0BEC5)
                )
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientOutlineButton(
                    text = "相册",
                    icon = Icons.Default.Image,
                    onClick = onSelectImage
                )
                GradientOutlineButton(
                    text = "搜索",
                    icon = Icons.Default.Search,
                    onClick = onOpenImageSearch
                )
            }
        }
        
        // 图片预览区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .height(400.dp)
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
                    .background(Color(0xFF050A1A)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    // 显示 AI 处理结果
                    resultBitmap != null -> {
                        androidx.compose.foundation.Image(
                            bitmap = resultBitmap!!.asImageBitmap(),
                            contentDescription = "AI 处理结果",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    // 显示原始图片
                    selectedImageUri != null -> {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "原始图片",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    // 空状态：提示选择图片
                    else -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF9FA8DA),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "选择一张图片开始 AI 修图",
                                fontSize = 14.sp,
                                color = Color(0xFFCFD8DC)
                            )
                        }
                    }
                }
                
                // 处理中遮罩
                if (isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF00E5FF),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "AI 正在处理中...",
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
        
        // 文字指令输入框
        OutlinedTextField(
            value = promptText,
            onValueChange = { promptText = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isProcessing && selectedImageUri != null,
            placeholder = {
                Text(
                    text = "输入修图指令，例如：把背景换成蓝天白云、添加彩虹效果、变成卡通风格...",
                    color = Color(0xFF607D8B)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00E5FF),
                unfocusedBorderColor = Color(0xFF37474F),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            maxLines = 3
        )
        
        // 错误提示
        errorMessage?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFF5252).copy(alpha = 0.2f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = Color(0xFFFF5252)
                    )
                    Text(
                        text = error,
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp
                    )
                }
            }
        }
        
        // 操作按钮区域
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // AI 处理按钮
            GradientToolButton(
                modifier = Modifier.weight(1f),
                text = if (isProcessing) "处理中..." else "AI 修图",
                icon = if (isProcessing) Icons.Default.HourglassEmpty else Icons.Default.AutoAwesome,
                enabled = !isProcessing && selectedImageUri != null && promptText.isNotBlank()
            ) {
                if (selectedImageUri != null && promptText.isNotBlank()) {
                    scope.launch {
                        isProcessing = true
                        errorMessage = null
                        resultBitmap = null
                        
                        val result = AiImageService.editImage(
                            context = context,
                            imageUri = selectedImageUri!!,
                            prompt = promptText
                        )
                        
                        isProcessing = false
                        
                        result.fold(
                            onSuccess = { bitmap ->
                                resultBitmap = bitmap
                                Toast.makeText(context, "AI 修图完成！", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { error ->
                                errorMessage = "处理失败: ${error.message}"
                                Toast.makeText(context, "处理失败: ${error.message}", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }
            }
            
            // 保存按钮
            GradientToolButton(
                modifier = Modifier.weight(1f),
                text = "保存图片",
                icon = Icons.Default.Save,
                enabled = resultBitmap != null && !isProcessing
            ) {
                resultBitmap?.let { bitmap ->
                    scope.launch {
                        try {
                            saveAiResultToGallery(context, bitmap)
                            Toast.makeText(context, "图片已保存到相册", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        
        // 图片搜索弹层
        if (showImageSearch) {
            ImageSearchDialog(
                onDismiss = onDismissImageSearch,
                onImageSelected = onImageSelectedFromSearch
            )
        }
    }
}

/**
 * 保存 AI 处理结果到相册
 */
private suspend fun saveAiResultToGallery(context: android.content.Context, bitmap: Bitmap) {
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "ai_edited_${System.currentTimeMillis()}.jpg")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AIEditedImages")
        }
        
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        
        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }
        }
    }
}
