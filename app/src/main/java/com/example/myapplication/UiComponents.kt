package com.example.myapplication

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// 顶部标题区域（带渐变头像 + 动态扫光效果）
@Composable
fun TopHeaderWithEffects(
    onSelectImage: () -> Unit,
    onOpenImageSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            GradientAvatarWithShine()

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "炫酷修图工坊",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF5F5F5)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFFFFD54F)
                    )
                    Text(
                        text = "渐变 · 扫光 · 金属质感",
                        fontSize = 11.sp,
                        color = Color(0xFFB0BEC5),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        GradientOutlineButton(
            text = "相册",
            icon = Icons.Default.Image,
            onClick = onSelectImage
        )
        Spacer(modifier = Modifier.width(8.dp))
        GradientOutlineButton(
            text = "搜索",
            icon = Icons.Default.Search,
            onClick = onOpenImageSearch
        )
    }
}

// 圆形头像 + 渐变边框 + 动态扫光
@Composable
fun GradientAvatarWithShine() {
    val infiniteTransition = rememberInfiniteTransition(label = "avatarShine")
    // 扫光位置（-1.2 ~ 1.2 表示从左外侧滑到右外侧）
    val shineOffset by infiniteTransition.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            // 扫光速度稍快一点，类似高亮光线一闪而过
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shineOffsetAnim"
    )
    // 透明度随扫光位置变化，经过中间时最亮
    val shineAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shineAlphaAnim"
    )

    Box(
        modifier = Modifier
            .size(56.dp)
            .shadow(
                elevation = 16.dp,
                shape = CircleShape,
                ambientColor = Color(0xFF90CAF9),
                spotColor = Color(0xFF82B1FF)
            )
            // 外层采用多段彩色渐变环，凸显渐变层次
            .background(
                brush = Brush.sweepGradient(
                    listOf(
                        Color(0xFFFFD700),
                        Color(0xFFFF8A65),
                        Color(0xFFB388FF),
                        Color(0xFF64B5F6),
                        Color(0xFF00E5FF),
                        Color(0xFF69F0AE),
                        Color(0xFFFFD700)
                    )
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        // 内部圆形头像区域
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                // 内圈再叠一层径向渐变，增强立体感
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF070B16)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = Color(0xFF80CBC4),
                modifier = Modifier.size(24.dp)
            )
        }

        // 动态扫光高光
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x80FFFFFF),
                            Color(0xFFFFFFFF),
                            Color(0x80FFFFFF),
                            Color.Transparent
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(160f, 160f)
                    ),
                    shape = CircleShape,
                    alpha = shineAlpha.coerceIn(0f, 1f)
                )
        )
    }
}

// 渐变描边按钮（小型）
@Composable
fun GradientOutlineButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        Color(0xFF80DEEA),
                        Color(0xFFB388FF)
                    )
                )
            )
            .clickable { onClick() }
            .padding(horizontal = 1.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFF050816))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFFB0BEC5)
            )
            Text(
                text = text,
                fontSize = 12.sp,
                color = Color(0xFFF5F5F5)
            )
        }
    }
}

// 底部工具按钮（裁剪 / 旋转 / 撤销）
@Composable
fun GradientToolButton(
    modifier: Modifier = Modifier,
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val alpha = if (enabled) 1f else 0.4f

    // 按钮前/后（按下）两种不同的渐变配色
    val backgroundColors = if (isPressed) {
        listOf(
            Color(0xFF55F5FF), // 更亮的青
            Color(0xFF6F7BFF), // 更亮的蓝紫
            Color(0xFFCC80FF)  // 更亮的紫粉
        )
    } else {
        listOf(
            Color(0xFF1F6FEA), // 浅蓝
            Color(0xFF2F7FF5), // 浅一些的亮蓝
            Color(0xFF6C4DFF)  // 浅紫
        )
    }

    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = backgroundColors,
                    start = Offset.Zero,
                    end = Offset.Infinite
                ),
                alpha = alpha
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.White
            )
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

// 底部导航栏
@Composable
fun BottomNavigationBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = Color(0xFF2C2C2C),
        contentColor = Color.White
    ) {
        NavigationBarItem(
            selected = selectedIndex == 0,
            onClick = { onItemSelected(0) },
            icon = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "修图",
                        tint = if (selectedIndex == 0) Color.White else Color(0xFF888888)
                    )
                    if (selectedIndex == 0) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(
                                    color = Color.White,
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .padding(top = 4.dp)
                        )
                    }
                }
            },
            label = {
                Text(
                    "修图",
                    color = if (selectedIndex == 0) Color.White else Color(0xFF888888)
                )
            }
        )

        NavigationBarItem(
            selected = selectedIndex == 1,
            onClick = { onItemSelected(1) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "AI 修图",
                    tint = if (selectedIndex == 1) Color.White else Color(0xFF888888)
                )
            },
            label = {
                Text(
                    "AI修图",
                    color = if (selectedIndex == 1) Color.White else Color(0xFF888888)
                )
            }
        )

        NavigationBarItem(
            selected = selectedIndex == 2,
            onClick = { onItemSelected(2) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "我的",
                    tint = if (selectedIndex == 2) Color.White else Color(0xFF888888)
                )
            },
            label = {
                Text(
                    "我的",
                    color = if (selectedIndex == 2) Color.White else Color(0xFF888888)
                )
            }
        )
    }
}


