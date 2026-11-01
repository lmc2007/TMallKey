package com.fktm.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fktm.app.R
import com.fktm.app.update.AppUpdateRepository
import com.fktm.app.update.UpdateSettings
import com.fktm.app.update.UpdateSources

/**
 * 「关于」页。
 *
 * 从门锁页整体搬过来的 —— 那里原本把「关于 + 检查更新」塞在列表最底部，
 * 内容越加越多（教程、下载、更新设置），独立成 tab 才不会把开锁主界面挤爆。
 */
@Composable
fun AboutScreen(repo: AppUpdateRepository, settings: UpdateSettings) {
    val context = LocalContext.current
    val version = remember(context) { appVersionName(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            "关于",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(14.dp))

        AboutCard(version = version)

        Spacer(Modifier.height(14.dp))
        PatchGuideCard()

        Spacer(Modifier.height(14.dp))
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))
        Text("更新", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        UpdateCard(repo = repo, settings = settings)

        Spacer(Modifier.height(18.dp))
    }
}

/** 版本 + 作者 + 两个外链。 */
@Composable
private fun AboutCard(version: String) {
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.app_name),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "v$version",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "作者：牧冷 · 一点即开，不依赖天猫校园",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { openUrl(context, UpdateSources.PAGES_URL) },
                    modifier = Modifier.weight(1f),
                ) { Text("打开落地页", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = { openUrl(context, UpdateSources.REPO_URL) },
                    modifier = Modifier.weight(1f),
                ) { Text("GitHub 仓库", fontSize = 13.sp) }
            }
        }
    }
}

/**
 * 补丁版天猫校园的教程 + 下载。
 *
 * 为什么单独一张卡：免 root 用户拿凭证**全靠它** —— 没有补丁版模块，
 * 钥匙只能 root 抓或手工填。放在「关于」里而不是弹窗，是方便用户转发给别人。
 */
@Composable
private fun PatchGuideCard() {
    val context = LocalContext.current
    val mirror = UpdateSources.DOWNLOAD_PROXIES.firstOrNull()

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "补丁版天猫校园（免 root 拿钥匙）",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "它和官方版同包名，只能装一个。装好后在里面点一次开锁，" +
                    "本 App 会把钥匙和登录态一起收下 —— 之后开锁、接水都不再需要它。",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )

            Spacer(Modifier.height(10.dp))
            listOf(
                "下载并安装补丁版（已装官方版的先卸载，会重新登录一次）",
                "用同一个淘宝账号登录，进「门锁」页",
                "在门口点一次「开锁」，等门真的打开",
                "回到本 App —— 钥匙会自动出现在门锁列表",
            ).forEachIndexed { i, line ->
                Text(
                    "${i + 1}. $line",
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val url = mirror?.let { UpdateSources.lspatchMirrorUrl(it) }
                            ?: UpdateSources.LSPATCH_DIRECT_URL
                        openUrl(context, url)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("镜像下载", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = { openUrl(context, UpdateSources.LSPATCH_DIRECT_URL) },
                    modifier = Modifier.weight(1f),
                ) { Text("GitHub 直连", fontSize = 13.sp) }
            }
            Spacer(Modifier.height(2.dp))
            TextButton(
                onClick = { openUrl(context, UpdateSources.RELEASES_LATEST_URL) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("打不开？去 Releases 页面手动下载", fontSize = 11.sp)
            }
        }
    }
}

/** 用系统浏览器打开链接。没有浏览器等异常就静默忽略，不打断界面。 */
private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun appVersionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull().orEmpty().ifBlank { "?" }
