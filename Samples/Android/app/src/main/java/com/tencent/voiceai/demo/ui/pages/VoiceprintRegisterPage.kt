// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 声纹注册页面

package com.tencent.voiceai.demo.ui.pages

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tencent.voiceai.demo.ui.components.PageScaffold
import com.tencent.voiceai.demo.ui.theme.TextPrimary
import com.tencent.voiceai.demo.ui.theme.TextSecondary
import com.tencent.voiceai.demo.viewmodel.VoiceprintRegisterViewModel

@Composable
fun VoiceprintRegisterPage(
    onBack: () -> Unit,
    viewModel: VoiceprintRegisterViewModel = viewModel()
) {
    val context = LocalContext.current

    // 录音权限申请：在点击「开始注册」时触发，授权成功后立即开始录制。
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startRecording()
        } else {
            android.widget.Toast.makeText(
                context, "未授予麦克风权限，无法进行声纹注册", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 开始注册：先判断录音权限，已授权直接录制，否则发起申请。
    fun startRegisterWithPermission() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startRecording()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // 离开页面（返回或被移出组合）时：停止录制并丢弃当前录音数据。
    DisposableEffect(Unit) {
        onDispose { viewModel.cancelRecording() }
    }

    // 消费一次性提示信息。
    LaunchedEffect(viewModel.toastMessage) {
        viewModel.toastMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }

    PageScaffold(title = "声纹注册", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // 顶部：已注册的声纹文件信息 + 播放按钮
            RegisteredCard(
                fileName = viewModel.registeredFileName,
                durationText = viewModel.registeredDurationText,
                isPlaying = viewModel.isPlaying,
                enabled = !viewModel.isRecording,
                onTogglePlay = { viewModel.togglePlayback() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 中部：提示词
            PromptCard(text = VoiceprintRegisterViewModel.PROMPT_TEXT)

            // 占位撑开，将状态提示与按钮一起推到底部。
            Spacer(modifier = Modifier.weight(1f))

            // 录制状态提示（紧跟在按钮上方）
            RecordStatus(
                isRecording = viewModel.isRecording,
                elapsedText = viewModel.elapsedText(),
                reachedMin = viewModel.reachedMinDuration(),
                minSeconds = (VoiceprintRegisterViewModel.MIN_DURATION_MS / 1000).toInt()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 底部：开始/停止 注册按钮
            RecordButton(
                isRecording = viewModel.isRecording,
                onClick = {
                    if (viewModel.isRecording) viewModel.stopRecording()
                    else startRegisterWithPermission()
                }
            )
        }
    }
}

@Composable
private fun RegisteredCard(
    fileName: String?,
    durationText: String?,
    isPlaying: Boolean,
    enabled: Boolean,
    onTogglePlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFFFFF))
            .padding(16.dp),
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
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = if (fileName != null) Color(0xFF4C9AFF) else Color(0xFF9AA5B8),
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileName ?: "暂无已注册声纹",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (fileName != null) "时长：${durationText ?: "--"}" else "请在下方开始注册",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        // 播放按钮
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    if (fileName != null && enabled) Color(0xFF4C9AFF) else Color(0xFFD5D9E3)
                )
                .then(
                    if (fileName != null && enabled) Modifier.clickable(onClick = onTogglePlay)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "停止播放" else "播放",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun PromptCard(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFFFFF))
            .padding(16.dp)
    ) {
        Text(
            text = "请照着朗读以下内容",
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Text(
            text = text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            lineHeight = 28.sp
        )
    }
}

@Composable
private fun RecordStatus(
    isRecording: Boolean,
    elapsedText: String,
    reachedMin: Boolean,
    minSeconds: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isRecording) {
            Icon(
                imageVector = Icons.Default.FiberManualRecord,
                contentDescription = null,
                tint = Color(0xFFF5533D),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = when {
                isRecording && reachedMin -> "$elapsedText  已达最短时长，可结束"
                isRecording -> "$elapsedText  最少还需录满 ${minSeconds}s"
                else -> "点击下方按钮开始注册"
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isRecording) TextPrimary else TextSecondary
        )
    }
}

@Composable
private fun RecordButton(
    isRecording: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(if (isRecording) Color(0xFFF5533D) else Color(0xFF4C9AFF))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isRecording) "停止注册" else "开始注册",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
