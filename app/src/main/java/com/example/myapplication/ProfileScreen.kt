package com.example.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// "我的"页面
@Composable
fun ProfileScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 顶部头像和用户信息
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GradientAvatarWithShine()

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "用户昵称",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF5F5F5)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ID: 123456789",
                    fontSize = 14.sp,
                    color = Color(0xFFB0BEC5)
                )
            }

            GradientOutlineButton(
                text = "编辑",
                icon = Icons.Default.Edit,
                onClick = { /* TODO: 编辑资料 */ }
            )
        }

        // 功能菜单
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileMenuItem(
                icon = Icons.Default.Favorite,
                text = "我的收藏",
                onClick = { /* TODO */ }
            )
            ProfileMenuItem(
                icon = Icons.Default.History,
                text = "历史记录",
                onClick = { /* TODO */ }
            )
            ProfileMenuItem(
                icon = Icons.Default.Settings,
                text = "设置",
                onClick = { /* TODO */ }
            )
            ProfileMenuItem(
                icon = Icons.Default.Info,
                text = "关于我们",
                onClick = { /* TODO */ }
            )
            ProfileMenuItem(
                icon = Icons.Default.Share,
                text = "分享应用",
                onClick = { /* TODO */ }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // 版本信息
        Text(
            text = "版本 1.0.0",
            fontSize = 12.sp,
            color = Color(0xFF666666),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ProfileMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E)
                    )
                )
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = text,
                fontSize = 16.sp,
                color = Color(0xFFF5F5F5),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF888888),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}


