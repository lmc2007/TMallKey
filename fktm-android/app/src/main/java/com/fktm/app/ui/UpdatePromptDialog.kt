package com.fktm.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fktm.app.update.UpdateDownloadState
import com.fktm.app.update.UpdateRelease

/**
 * 启动时发现新版本的弹窗。
 *
 * 只在**第一次**弹（用户点过「稍后」本次进程内不再打扰），
 * 想彻底忽略某个版本请到「关于 → 检查更新 → 忽略此版本」。
 *
 * 下载状态跟着 [UpdateDownloadState] 走：
 * 空闲 → 显示「稍后 / 立即更新」；下载中 → 进度条 + 「取消」；失败 → 错误原因。
 */
@Composable
fun UpdatePromptDialog(
    release: UpdateRelease,
    downloadState: UpdateDownloadState,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    val running = downloadState as? UpdateDownloadState.Running
    val failed = downloadState as? UpdateDownloadState.Failed

    AlertDialog(
        onDismissRequest = { if (running == null) onDismiss() },
        title = {
            Text("发现新版本 v${release.versionName}", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column {
                if (running != null) {
                    val total = running.totalBytes ?: 0L
                    if (total > 0) {
                        LinearProgressIndicator(
                            progress = { (running.downloadedBytes.toFloat() / total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "正在下载 ${formatBytes(running.downloadedBytes)} / ${formatBytes(total)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "正在下载 ${formatBytes(running.downloadedBytes)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                } else {
                    Text(
                        "当前版本已不是最新，建议更新后再用。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                    Spacer(Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            release.changelog.ifBlank { "（该版本没有填写更新说明）" },
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(10.dp),
                        )
                    }
                    if (failed != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "更新失败：${failed.message}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (running != null) {
                TextButton(onClick = onCancelDownload) { Text("取消下载") }
            } else {
                TextButton(onClick = onUpdate) { Text("立即更新") }
            }
        },
        dismissButton = {
            if (running == null) {
                TextButton(onClick = onDismiss) { Text("稍后") }
            }
        },
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.0f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}
