package com.fktm.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fktm.app.update.AppUpdateRepository
import com.fktm.app.update.DownloadMode
import com.fktm.app.update.LaunchCheckThreshold
import com.fktm.app.update.UpdateCheckResult
import com.fktm.app.update.UpdateDownloadState
import com.fktm.app.update.UpdateRelease
import com.fktm.app.update.UpdateSettings
import kotlinx.coroutines.launch

/**
 * 「检查更新」卡片。
 *
 * 首次进入时若超过设定阈值会静默检查一次（失败不打扰用户，
 * 只有手动点「检查更新」才会把错误原因显示出来）。
 */
@Composable
fun UpdateCard(repo: AppUpdateRepository, settings: UpdateSettings) {
    val scope = rememberCoroutineScope()

    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    val downloadState by repo.downloadState.collectAsStateWithLifecycle()

    fun runCheck(force: Boolean) {
        if (checking) return
        checking = true
        scope.launch {
            try {
                result = repo.checkForUpdate(force = force)
            } finally {
                checking = false
            }
        }
    }

    // 启动自动检查：走节流阈值，静默进行
    LaunchedEffect(Unit) {
        if (settings.shouldCheckOnLaunch()) runCheck(force = false)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("检查更新", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(
                    "v${repo.currentVersionName} (${repo.currentVersionCode})",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { showSettings = !showSettings }) {
                    Text(if (showSettings) "收起" else "设置", fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { runCheck(force = true) }, enabled = !checking) {
                    if (checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (checking) "检查中" else "检查更新", fontSize = 12.sp)
                }
            }

            val release = (result as? UpdateCheckResult.UpdateAvailable)?.release
                ?: (result as? UpdateCheckResult.Ignored)?.release

            if (!checking) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = statusText(result),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }

            if (result is UpdateCheckResult.Failed && !checking) {
                Spacer(Modifier.height(4.dp))
                Text(
                    (result as UpdateCheckResult.Failed).message,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                )
            }

            if (release != null) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                ReleaseSection(
                    release = release,
                    isIgnored = result is UpdateCheckResult.Ignored,
                    downloadState = downloadState,
                    onDownload = { scope.launch { repo.downloadAndInstall(release) } },
                    onCancel = { repo.cancelDownload() },
                    onIgnore = {
                        repo.ignoreRelease(release)
                        result = UpdateCheckResult.Ignored(release)
                    },
                    onOpenPage = { repo.openReleasePage(release) },
                )
            } else {
                DownloadStatusOnly(downloadState)
            }

            if (showSettings) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                SettingsSection(settings = settings, onChanged = { showSettings = false })
            }
        }
    }
}

@Composable
private fun ReleaseSection(
    release: UpdateRelease,
    isIgnored: Boolean,
    downloadState: UpdateDownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onIgnore: () -> Unit,
    onOpenPage: () -> Unit,
) {
    // 只认「正在下载的就是本卡片这一版」，避免下载别的版本时这里也跟着转
    val active = (downloadState as? UpdateDownloadState.Running)
        ?.takeIf { it.release.versionCode == release.versionCode }

    Text(
        "v${release.versionName} (build ${release.versionCode}) · ${release.channel}",
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        release.changelog.ifBlank { "（该版本未提供更新说明）" },
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )

    if (!release.changelog.isBlank() && release.changelog.length > 400) {
        Spacer(Modifier.height(4.dp))
        Text(
            "（更新说明较长，完整内容见发布页）",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }

    Spacer(Modifier.height(10.dp))

    if (active != null) {
        val done = active.downloadedBytes
        val total = active.totalBytes
        val fraction = if (total != null && total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (total != null && total > 0) "下载中 ${formatBytes(done)} / ${formatBytes(total)}"
            else "下载中 ${formatBytes(done)}",
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("取消下载", fontSize = 12.sp)
        }
        return
    }

    when (downloadState) {
        is UpdateDownloadState.Failed -> {
            Text(
                "上次失败：${downloadState.message}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(6.dp))
        }

        is UpdateDownloadState.PermissionRequired -> {
            Text(
                "已下载完成，需要你在系统设置里允许「安装未知应用」，回来后再次点击下载即可安装。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(6.dp))
        }

        is UpdateDownloadState.Completed -> {
            Text(
                "已交给系统安装器，按提示完成安装。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
        }

        else -> Unit
    }

    if (isIgnored) {
        Text(
            "该版本已被忽略，自动检查不会再提示。",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(6.dp))
    }

    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (release.asset == null) "该版本无可安装包，去发布页" else "下载并安装",
            fontSize = 12.sp,
        )
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onOpenPage, modifier = Modifier.weight(1f)) {
            Text("打开发布页", fontSize = 12.sp)
        }
        if (!isIgnored) {
            OutlinedButton(onClick = onIgnore, modifier = Modifier.weight(1f)) {
                Text("忽略此版本", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DownloadStatusOnly(downloadState: UpdateDownloadState) {
    val running = downloadState as? UpdateDownloadState.Running ?: return
    val total = running.totalBytes
    val fraction = if (total != null && total > 0) {
        (running.downloadedBytes.toFloat() / total).coerceIn(0f, 1f)
    } else 0f

    Spacer(Modifier.height(10.dp))
    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    Text("下载中 ${formatBytes(running.downloadedBytes)}", fontSize = 11.sp)
}

@Composable
private fun SettingsSection(settings: UpdateSettings, onChanged: () -> Unit) {
    var threshold by remember { mutableStateOf(settings.launchThreshold) }
    var mode by remember { mutableStateOf(settings.downloadMode) }

    Text("启动时自动检查", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    OptionRow(
        options = LaunchCheckThreshold.entries.toList(),
        selected = threshold,
        labelOf = { it.label },
        onSelect = {
            threshold = it
            settings.launchThreshold = it
            onChanged()
        },
    )

    Spacer(Modifier.height(12.dp))
    Text("APK 下载方式", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    OptionRow(
        options = DownloadMode.entries.toList(),
        selected = mode,
        labelOf = { it.label },
        onSelect = {
            mode = it
            settings.downloadMode = it
            onChanged()
        },
    )

    Spacer(Modifier.height(10.dp))
    Text(
        "代理优先会先尝试第三方 GitHub 加速节点，失败再回落直连；直连模式下只走 GitHub。",
        fontSize = 10.sp,
        lineHeight = 14.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
    )
}

@Composable
private fun <T> OptionRow(
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { option ->
                    val isSelected = option == selected
                    if (isSelected) {
                        Button(onClick = { onSelect(option) }, modifier = Modifier.weight(1f)) {
                            Text(labelOf(option), fontSize = 11.sp)
                        }
                    } else {
                        OutlinedButton(onClick = { onSelect(option) }, modifier = Modifier.weight(1f)) {
                            Text(labelOf(option), fontSize = 11.sp)
                        }
                    }
                }
                // 补齐最后一行，避免宽度被拉伸
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun statusText(result: UpdateCheckResult?): String = when (result) {
    null -> "尚未检查。"
    is UpdateCheckResult.UpdateAvailable -> "发现新版本 v${result.release.versionName}。"
    is UpdateCheckResult.UpToDate -> "已是最新版本（v${result.currentVersionName}）。"
    is UpdateCheckResult.Ignored -> "v${result.release.versionName} 已被忽略。"
    is UpdateCheckResult.Preparing -> "v${result.versionName} 正在灰度发布中，暂不可安装。"
    is UpdateCheckResult.Failed -> "检查失败，所有更新源均不可用。"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
