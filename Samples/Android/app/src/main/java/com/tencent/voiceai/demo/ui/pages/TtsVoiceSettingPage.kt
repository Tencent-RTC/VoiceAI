// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - TTS 音色设置页面

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tencent.voiceai.demo.ui.components.PageScaffold
import com.tencent.voiceai.demo.ui.theme.TextPrimary
import com.tencent.voiceai.demo.ui.theme.TextSecondary
import com.tencent.voiceai.demo.viewmodel.TtsVoice
import com.tencent.voiceai.demo.viewmodel.TtsVoiceSettingViewModel

@Composable
fun TtsVoiceSettingPage(
    onBack: () -> Unit,
    viewModel: TtsVoiceSettingViewModel = viewModel()
) {
    PageScaffold(title = "音色设置", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                text = "选择默认合成音色",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFFFFFF))
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                viewModel.voices.forEachIndexed { index, voice ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = Color(0xFFEDF0F6),
                            thickness = 1.dp
                        )
                    }
                    VoiceItem(
                        voice = voice,
                        selected = voice.voiceId == viewModel.selectedVoiceId,
                        onClick = { viewModel.selectVoice(voice.voiceId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceItem(
    voice: TtsVoice,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF0F3FA)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.RecordVoiceOver,
                contentDescription = voice.displayName,
                tint = if (selected) Color(0xFF4C9AFF) else Color(0xFF9AA5B8),
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = voice.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "voiceId: ${voice.voiceId}",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        Icon(
            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (selected) "已选择" else "未选择",
            tint = if (selected) Color(0xFF4C9AFF) else Color(0xFFD5D9E3),
            modifier = Modifier.size(22.dp)
        )
    }
}
