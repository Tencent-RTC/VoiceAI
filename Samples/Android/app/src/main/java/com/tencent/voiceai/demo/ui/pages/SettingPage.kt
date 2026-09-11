// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 设置页面

package com.tencent.voiceai.demo.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tencent.voiceai.demo.ui.components.PageScaffold
import com.tencent.voiceai.demo.ui.theme.TextPrimary
import com.tencent.voiceai.demo.ui.theme.TextSecondary
import com.tencent.voiceai.demo.viewmodel.SettingViewModel
import com.tencent.voiceai.demo.viewmodel.TtsVoiceSettingViewModel

@Composable
fun SettingPage(
    onBack: () -> Unit,
    onVoiceSetting: () -> Unit = {},
    onVoiceprintRegister: () -> Unit = {},
    viewModel: SettingViewModel = viewModel()
) {
    val ttsVoiceViewModel = viewModel<TtsVoiceSettingViewModel>()

    PageScaffold(title = "设置", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // 播放相关
            SettingGroup {
                SelectItem(
                    icon = Icons.Default.Headset,
                    title = "默认音色",
                    value = ttsVoiceViewModel.selectedVoice?.displayName ?: "未选择",
                    onClick = onVoiceSetting
                )
                Divider()
                SelectItem(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "声纹注册",
                    value = "",
                    onClick = onVoiceprintRegister
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 关于
            Text(
                text = "关于 VoiceAI",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            SettingGroup {
                SelectItem(
                    icon = Icons.Default.ChevronRight,
                    title = "版本",
                    value = "1.0.0",
                    onClick = {}
                )
            }
        }
    }
}

@Composable
private fun SettingGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFFFFF))
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        content()
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 44.dp),
        color = Color(0xFFEDF0F6),
        thickness = 1.dp
    )
}

@Composable
private fun SelectItem(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF0F3FA)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color(0xFF4C9AFF),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            fontSize = 13.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.width(6.dp))

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color(0xFFB5BCCB),
            modifier = Modifier.size(20.dp)
        )
    }
}
