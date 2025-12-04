package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64 as AndroidBase64

/**
 * AI 图像编辑服务 - 调用阿里云通义千问（Qwen）图像编辑 API
 * 
 * 注意：AccessKey 应该放在更安全的位置（如 BuildConfig 或环境变量），
 * 这里为了演示方便直接写在代码中，实际项目中请使用更安全的方式。
 */
object AiImageService {
    // TODO: 建议将密钥移到 BuildConfig 或 gradle.properties，避免硬编码
    // 注意：DashScope API 需要使用专门的 API Key，不是传统的 AccessKey ID/Secret
    // 请从阿里云 DashScope 控制台获取 API Key：https://dashscope.console.aliyun.com/
    // 如果提供的是 AccessKey ID，需要先转换为 API Key 或使用签名方式
    // 请替换为您的实际密钥
    private const val ACCESS_KEY_ID = "YOUR_ACCESS_KEY_ID"
    private const val ACCESS_KEY_SECRET = "YOUR_ACCESS_KEY_SECRET"
    
    // DashScope API Key（从 DashScope 控制台获取）
    // 请替换为您的实际 API Key
    private const val DASHSCOPE_API_KEY = "YOUR_DASHSCOPE_API_KEY"
    
    // DashScope API 端点（多模态对话 API，用于图像编辑）
    // 根据 Python SDK：dashscope.base_http_api_url = 'https://dashscope.aliyuncs.com/api/v1'
    // MultiModalConversation.call 使用的端点应该是：
    private const val API_ENDPOINT = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"
    
    // 注意：如果上面的端点不行，可能需要检查 Python SDK 实际使用的完整端点路径
    
    // 使用 qwen-image-edit 模型进行图像编辑（根据 Python 示例）
    // 注意：qwen-image-edit 支持文本指令，而 qwen-image-edit-plus 可能不支持
    private const val MODEL = "qwen-image-edit"
    
    private val client = OkHttpClient()
    
    // 时区格式
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    /**
     * 调用 Qwen 图像编辑 API
     * 
     * @param context Android Context
     * @param imageUri 原始图片 URI
     * @param prompt 用户输入的文本指令（如："把背景换成蓝天白云"）
     * @return 处理后的图片 Bitmap，失败返回 null
     */
    suspend fun editImage(
        context: Context,
        imageUri: Uri,
        prompt: String
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            // 1. 将图片 URI 转换为 Base64
            val imageBase64 = imageUriToBase64(context, imageUri)
                ?: return@withContext Result.failure(Exception("无法读取图片"))
            
            // 验证图片 Base64 是否有效
            if (imageBase64.isEmpty()) {
                return@withContext Result.failure(Exception("图片 Base64 编码为空"))
            }
            android.util.Log.d("AiImageService", "图片 Base64 长度: ${imageBase64.length}")
            
            // 2. 构建请求体 JSON 字符串（先构建字符串用于签名，再创建 RequestBody）
            val requestBodyJson = buildRequestBodyJson(imageBase64, prompt)
            // 只打印请求体的前500字符（避免日志过长，图片 base64 很长）
            android.util.Log.d("AiImageService", "请求体 JSON 预览: ${requestBodyJson.take(500)}...")
            android.util.Log.d("AiImageService", "请求体完整长度: ${requestBodyJson.length} 字符")
            val requestBody = requestBodyJson.toRequestBody("application/json".toMediaType())
            
            // 3. 构建 HTTP 请求（使用 DashScope API Key 认证）
            // DashScope API 通常使用 API Key 方式，如果 AccessKey 是 API Key，直接使用
            // 如果 AccessKey 是传统的 ID/Secret，需要实现签名算法
            val request = buildRequest(requestBody, requestBodyJson)
            
            // 4. 发送请求并解析响应
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            // 记录请求详情（调试用）
            android.util.Log.d("AiImageService", "API 响应码: ${response.code}")
            android.util.Log.d("AiImageService", "API 响应: ${responseBody?.take(500)}")  // 只记录前500字符
            
            if (!response.isSuccessful) {
                // 如果是 401 错误，提示用户检查 API Key
                val errorMsg = if (response.code == 401) {
                    "认证失败：请确认已从 DashScope 控制台获取正确的 API Key，并确保 API Key 有效。\n" +
                    "获取地址：https://dashscope.console.aliyun.com/\n" +
                    "错误详情: $responseBody"
                } else {
                    "API 请求失败: ${response.code} - $responseBody"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }
            
            // 5. 解析响应中的图片 Base64
            val resultBitmap = parseResponseToBitmap(responseBody ?: "")
            
            if (resultBitmap != null) {
                Result.success(resultBitmap)
            } else {
                Result.failure(Exception("无法解析 API 响应"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 将图片 URI 转换为 Base64 字符串
     */
    private suspend fun imageUriToBase64(context: Context, uri: Uri): String? {
        return try {
            val imageLoader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(uri)
                .build()
            val drawable = imageLoader.execute(request).drawable
            val bitmap = drawable?.toBitmap() ?: return null
            
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            val imageBytes = outputStream.toByteArray()
            
            // 使用 Android 标准 Base64（兼容所有版本）
            AndroidBase64.encodeToString(imageBytes, AndroidBase64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
        null
    }
}

    /**
     * 构建 API 请求体 JSON 字符串
     * 根据 Python SDK 示例，使用 messages 格式
     * 参考：MultiModalConversation.call 的请求格式
     * 
     * 注意：确保图片格式正确，使用 data URI 格式
     */
    private fun buildRequestBodyJson(imageBase64: String, prompt: String): String {
        // 验证输入
        if (imageBase64.isEmpty()) {
            throw IllegalArgumentException("图片 Base64 不能为空")
        }
        if (prompt.isEmpty()) {
            throw IllegalArgumentException("文本指令不能为空")
        }
        
        // 构建 data URI 格式的图片
        val imageDataUri = "data:image/jpeg;base64,$imageBase64"
        android.util.Log.d("AiImageService", "图片 Base64 长度: ${imageBase64.length}")
        android.util.Log.d("AiImageService", "data URI 总长度: ${imageDataUri.length}")
        android.util.Log.d("AiImageService", "data URI 前100字符: ${imageDataUri.take(100)}")
        
        // 构建 messages 数组（严格按照 Python 示例格式）
        val messagesArray = org.json.JSONArray()
        val message = JSONObject()
        message.put("role", "user")
        
        val contentArray = org.json.JSONArray()
        
        // 添加图片对象（使用 data URI 格式）
        val imageObj = JSONObject()
        imageObj.put("image", imageDataUri)
        contentArray.put(imageObj)
        android.util.Log.d("AiImageService", "已添加图片对象，image 字段值长度: ${imageDataUri.length}")
        
        // 添加文本对象
        val textObj = JSONObject()
        textObj.put("text", prompt)
        contentArray.put(textObj)
        android.util.Log.d("AiImageService", "已添加文本对象: $prompt")
        
        message.put("content", contentArray)
        messagesArray.put(message)
        
        android.util.Log.d("AiImageService", "Messages 数组长度: ${messagesArray.length()}")
        android.util.Log.d("AiImageService", "Content 数组长度: ${contentArray.length()}")
        
        // 构建完整的请求体（根据 Python SDK）
        val json = JSONObject().apply {
            put("model", MODEL)
            put("messages", messagesArray)
            put("result_format", "message")
            put("stream", false)
            // 注意：Python 示例中没有 n 参数，可能不需要
            // put("n", 1)
            put("watermark", true)
            put("negative_prompt", "")
        }
        
        val jsonString = json.toString()
        android.util.Log.d("AiImageService", "构建的请求体长度: ${jsonString.length}")
        
        // 详细验证和日志
        try {
            val parsedJson = JSONObject(jsonString)
            android.util.Log.d("AiImageService", "=== 请求体结构验证 ===")
            android.util.Log.d("AiImageService", "模型: ${parsedJson.optString("model")}")
            
            val messages = parsedJson.getJSONArray("messages")
            android.util.Log.d("AiImageService", "Messages 数组长度: ${messages.length()}")
            
            if (messages.length() > 0) {
                val firstMessage = messages.getJSONObject(0)
                android.util.Log.d("AiImageService", "Message role: ${firstMessage.optString("role")}")
                
                val content = firstMessage.getJSONArray("content")
                android.util.Log.d("AiImageService", "Content 数组长度: ${content.length()}")
                
                var imageCount = 0
                for (i in 0 until content.length()) {
                    val item = content.getJSONObject(i)
                    android.util.Log.d("AiImageService", "Content[$i] keys: ${item.keys().asSequence().joinToString()}")
                    
                    if (item.has("image")) {
                        imageCount++
                        val imageValue = item.getString("image")
                        android.util.Log.d("AiImageService", "找到图片 #$imageCount，长度: ${imageValue.length}")
                        android.util.Log.d("AiImageService", "图片前50字符: ${imageValue.take(50)}")
                        android.util.Log.d("AiImageService", "图片是否以 data: 开头: ${imageValue.startsWith("data:")}")
                    }
                    if (item.has("text")) {
                        android.util.Log.d("AiImageService", "找到文本: ${item.getString("text")}")
                    }
                }
                android.util.Log.d("AiImageService", "总共找到 $imageCount 张图片")
                
                // 如果找到图片但 API 说没找到，记录警告
                if (imageCount > 0) {
                    android.util.Log.w("AiImageService", "⚠️ 代码验证：找到 $imageCount 张图片")
                    android.util.Log.w("AiImageService", "如果 API 仍返回 'got 0'，可能原因：")
                    android.util.Log.w("AiImageService", "  1. API 端点不正确")
                    android.util.Log.w("AiImageService", "  2. 图片格式不符合 API 要求")
                    android.util.Log.w("AiImageService", "  3. API 解析 data URI 时出现问题")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AiImageService", "验证请求体失败", e)
        }
        
        // 打印请求体的前500字符（用于调试，不包含完整图片数据）
        val preview = jsonString.take(500)
        android.util.Log.d("AiImageService", "请求体预览: $preview...")
        
        return jsonString
    }
    
    /**
     * 构建 HTTP 请求
     * DashScope API 必须使用 X-DashScope-API-Key 头进行认证
     * 
     * 重要：DashScope API 需要使用专门的 API Key，不是传统的 AccessKey ID/Secret
     * 请从阿里云 DashScope 控制台获取：https://dashscope.console.aliyun.com/
     */
    private fun buildRequest(requestBody: RequestBody, requestBodyJson: String): Request {
        val builder = Request.Builder()
            .url(API_ENDPOINT)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
        
        // 确定使用哪个 API Key
        val apiKey = if (DASHSCOPE_API_KEY.isNotEmpty()) {
            DASHSCOPE_API_KEY
        } else {
            ACCESS_KEY_ID
        }
        
        // DashScope API 使用 Authorization: Bearer {API_KEY} 格式
        // 根据 Python SDK 和官方文档，应该使用 Bearer 认证
        builder.addHeader("Authorization", "Bearer $apiKey")
        
        // 添加调试信息（可以在 Logcat 中查看）
        android.util.Log.d("AiImageService", "=== API 请求信息 ===")
        android.util.Log.d("AiImageService", "端点: $API_ENDPOINT")
        android.util.Log.d("AiImageService", "模型: $MODEL")
        android.util.Log.d("AiImageService", "API Key: ${apiKey.take(15)}...")
        android.util.Log.d("AiImageService", "请求体: ${requestBodyJson.take(200)}...")
        
        return builder.build()
    }
    
    /**
     * 计算阿里云 API 签名
     * 参考：https://help.aliyun.com/document_detail/315526.html
     */
    private fun calculateSignature(timestamp: String, requestBodyJson: String): String {
        try {
            // 1. 构建规范请求字符串
            val canonicalRequest = buildCanonicalRequest(timestamp, requestBodyJson)
            
            // 2. 计算签名
            val mac = Mac.getInstance("HmacSHA1")
            val secretKey = SecretKeySpec(ACCESS_KEY_SECRET.toByteArray(), "HmacSHA1")
            mac.init(secretKey)
            val signatureBytes = mac.doFinal(canonicalRequest.toByteArray())
            
            // 3. Base64 编码
            return AndroidBase64.encodeToString(signatureBytes, AndroidBase64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }
    
    /**
     * 构建规范请求字符串（用于签名）
     */
    private fun buildCanonicalRequest(timestamp: String, requestBodyJson: String): String {
        // 构建规范请求字符串
        val canonicalString = StringBuilder()
        canonicalString.append("POST\n")  // HTTP Method
        canonicalString.append("/api/v1/services/aigc/image-generation/generation\n")  // URI
        canonicalString.append("timestamp=$timestamp\n")  // Query String
        canonicalString.append("content-type:application/json\n")  // Headers
        canonicalString.append("\n")  // 空行
        canonicalString.append(requestBodyJson)  // Body
        
        return canonicalString.toString()
    }
    
    /**
     * 解析 API 响应，提取图片 Base64 并转换为 Bitmap
     * 根据 MultiModalConversation API 的响应格式：
     * {
     *   "output": {
     *     "choices": [
     *       {
     *         "message": {
     *           "content": [
     *             {
     *               "image": "data:image/jpeg;base64,..." 或 "url": "..."
     *             }
     *           ]
     *         }
     *       }
     *     ]
     *   }
     * }
     */
    private fun parseResponseToBitmap(responseBody: String): Bitmap? {
        return try {
            val json = JSONObject(responseBody)
            android.util.Log.d("AiImageService", "解析响应: ${json.toString().take(1000)}")
            
            val output = json.optJSONObject("output")
            if (output != null) {
                // 尝试解析 choices 数组（MultiModalConversation 格式）
                val choices = output.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val message = firstChoice.optJSONObject("message")
                    if (message != null) {
                        val content = message.optJSONArray("content")
                        if (content != null && content.length() > 0) {
                            // 查找图片内容
                            for (i in 0 until content.length()) {
                                val item = content.getJSONObject(i)
                                val imageBase64 = item.optString("image", "")
                                val imageUrl = item.optString("url", "")
                                
                                if (imageBase64.isNotEmpty()) {
                                    // 处理 Base64 字符串（可能包含 data URI 前缀）
                                    val base64Data = imageBase64.removePrefix("data:image/jpeg;base64,")
                                        .removePrefix("data:image/png;base64,")
                                        .removePrefix("data:image/webp;base64,")
                                    val imageBytes = AndroidBase64.decode(base64Data, AndroidBase64.NO_WRAP)
                                    return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                } else if (imageUrl.isNotEmpty()) {
                                    // TODO: 如果返回的是 URL，需要再次下载图片
                                    android.util.Log.w("AiImageService", "返回的是图片 URL，需要下载: $imageUrl")
                                    return null
                                }
                            }
                        }
                    }
                }
                
                // 兼容旧格式：output.results
                val results = output.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val firstResult = results.getJSONObject(0)
                    val imageBase64 = firstResult.optString("image", "")
                    val imageUrl = firstResult.optString("url", "")
                    
                    if (imageBase64.isNotEmpty()) {
                        val base64Data = imageBase64.removePrefix("data:image/jpeg;base64,")
                            .removePrefix("data:image/png;base64,")
                        val imageBytes = AndroidBase64.decode(base64Data, AndroidBase64.NO_WRAP)
                        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    }
                }
            }
            
            // 如果都没有找到，记录日志
            android.util.Log.e("AiImageService", "无法从响应中解析图片: $responseBody")
            null
        } catch (e: Exception) {
            android.util.Log.e("AiImageService", "解析响应失败", e)
            e.printStackTrace()
            null
        }
    }
}
