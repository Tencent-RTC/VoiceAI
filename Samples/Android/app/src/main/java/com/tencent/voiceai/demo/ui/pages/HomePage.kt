// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 首页

package com.tencent.voiceai.demo.ui.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.voiceai.demo.R
import com.tencent.voiceai.demo.ui.theme.BackgroundColor
import com.tencent.voiceai.demo.ui.theme.GreenEnd
import com.tencent.voiceai.demo.ui.theme.GreenStart
import com.tencent.voiceai.demo.ui.theme.PurpleEnd
import com.tencent.voiceai.demo.ui.theme.PurpleStart
import com.tencent.voiceai.demo.ui.theme.TextPrimary
import com.tencent.voiceai.demo.ui.theme.TextSecondary

/**
 * 首页。通过回调把功能入口的点击事件交给上层导航处理。
 */
@Composable
fun HomePage(
    onVoiceInput: () -> Unit = {},
    onChat: () -> Unit = {},
    onSettings: () -> Unit = {}
) {

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .verticalScroll(scrollState)
    ) {

        Header(onSettings = onSettings)

        Spacer(modifier = Modifier.height(16.dp))

        DemoCard(
            number = "01",
            title = "语音输入",
            subtitle = "体验实时语音识别",
            description = "实时识别 · 一句话识别 · 语音文件识别",
            icon = Icons.Default.Mic,
            gradient = listOf(
                PurpleStart,
                PurpleEnd
            ),
            iconColor = Color(0xFFB875FF),
            imageResId = R.drawable.ic_voice_input,
            imageSizeDp = 96,
            onClick = onVoiceInput
        )

        Spacer(modifier = Modifier.height(12.dp))

        DemoCard(
            number = "02",
            title = "AI 对话",
            subtitle = "与 AI 自然地说话",
            description = "实时语音对话 · 智能理解 · 流畅回复",
            icon = Icons.Default.Mic,
            gradient = listOf(
                GreenStart,
                GreenEnd
            ),
            iconColor = Color(0xFF35D7C6),
            imageResId = R.drawable.ic_ai_chat,
            imageSizeDp = 110,
            onClick = onChat
        )

        Spacer(modifier = Modifier.height(20.dp))

        Footer()

        Spacer(modifier = Modifier.height(30.dp))
    }
}


/* ---------------------------------------------------
 * Header
 * --------------------------------------------------- */

@Composable
private fun Header(onSettings: () -> Unit) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 28.dp,
                end = 20.dp,
                top = 28.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {

        Column {

            Text(
                text = "VoiceAI",
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.5).sp,
                style = MaterialTheme.typography.displaySmall.copy(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF2B6CF6),
                            Color(0xFF6E8BF5),
                            Color(0xFF9B4DEB)
                        )
                    )
                )
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = "探索声音的无限可能",
                fontSize = 15.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF48E4FF),
                                Color(0xFF8B5CFF)
                            )
                        ),
                        RoundedCornerShape(10.dp)
                    )
            )
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFFE7ECF7))
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "设置",
                tint = Color(0xFF4B5468),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}


/* ---------------------------------------------------
 * Demo Card
 * --------------------------------------------------- */

@Composable
private fun DemoCard(
    number: String,
    title: String,
    subtitle: String,
    description: String,
    icon: ImageVector,
    gradient: List<Color>,
    iconColor: Color,
    imageResId: Int? = null,
    imageSizeDp: Int = 144,
    onClick: () -> Unit
) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = iconColor.copy(alpha = 0.25f),
                spotColor = iconColor.copy(alpha = 0.25f)
            )
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(gradient)
            )
            .clickable(onClick = onClick)
            .padding(
                start = 24.dp,
                top = 10.dp,
                bottom = 10.dp,
                end = 18.dp
            )
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = number,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = iconColor
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = iconColor
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = description,
                    fontSize = 9.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(
                            iconColor.copy(alpha = 0.85f)
                        )
                        .padding(
                            horizontal = 14.dp,
                            vertical = 5.dp
                        )
                ) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            text = "立即体验",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = "›",
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            DemoIcon(
                icon = icon,
                color = iconColor,
                imageResId = imageResId,
                imageSizeDp = imageSizeDp
            )
        }
    }
}


/* ---------------------------------------------------
 * Large Demo Icon
 * --------------------------------------------------- */

@Composable
private fun DemoIcon(
    icon: ImageVector,
    color: Color,
    imageResId: Int? = null,
    imageSizeDp: Int = 144
) {
    if (imageResId != null) {
        Image(
            painter = painterResource(id = imageResId),
            contentDescription = null,
            modifier = Modifier.size(imageSizeDp.dp)
        )
        return
    }

    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.45f),
                        color.copy(alpha = 0.08f),
                        Color.Transparent
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    color.copy(alpha = 0.18f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/* ---------------------------------------------------
 * Footer
 * --------------------------------------------------- */

@Composable
private fun Footer() {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .width(35.dp)
                .height(1.dp)
                .background(Color(0xFFC7CEDD))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "技术驱动 · 声音未来",
            fontSize = 11.sp,
            color = Color(0xFF9AA2B4)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .width(35.dp)
                .height(1.dp)
                .background(Color(0xFFC7CEDD))
        )
    }
}
