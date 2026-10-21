package com.fktm.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fktm.app.FktmApp
import com.fktm.app.R
import com.fktm.app.credential.LockCredential
import com.fktm.app.credential.LogcatParser
import com.fktm.app.credential.GrabForegroundService
import com.fktm.app.credential.ShizukuLogcatGrabber
import com.fktm.app.update.UpdateSources
import com.fktm.app.vm.UnlockState
import com.fktm.app.vm.UnlockViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import rikka.shizuku.Shizuku

@Composable
fun MainScreen(
    vm: UnlockViewModel,
    onRequestPermissions: () -> Unit,
    onEnsureNotificationPermission: () -> Unit,
) {
    val locks = vm.locks
    var showImport by remember { mutableStateOf(!vm.locks.any()) }
    val app = LocalContext.current.applicationContext as FktmApp

    // 从饮水页抓到新门锁凭证后，切回本页时刷新列表
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 连接状态角标的数据源。连接本身由 BleLockClient 自己按超时回收
    // （冷启动 30s 窗口 / 开锁后保活 60s / 退后台立即让出），
    // 这里只负责把「现在是不是已经连上」画出来，让用户知道点了能不能秒开。
    var bleReady by remember { mutableStateOf(false) }
    var bleConnecting by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            bleReady = app.bleClient.isReady
            bleConnecting = app.bleClient.isConnecting
            delay(500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            stringResource(R.string.app_name),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "一点即开 · 不依赖天猫校园",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )

        if (locks.isNotEmpty() && vm.state !is UnlockState.Working) {
            Spacer(Modifier.height(10.dp))
            ConnectionBadge(
                ready = bleReady,
                connecting = bleConnecting,
                target = vm.locks.firstOrNull()?.address,
            )
        }

        Spacer(Modifier.height(18.dp))

        if (locks.isEmpty() && !showImport) {
            EmptyState(onImport = { showImport = true })
        } else {
            locks.forEach { cred ->
                LockCard(
                    cred = cred,
                    state = vm.state,
                    batteryText = vm.batteryText,
                    onUnlock = {
                        onRequestPermissions()
                        vm.unlock(cred)
                    },
                    onBattery = {
                        onRequestPermissions()
                        vm.readBattery(cred)
                    },
                    onRemove = { vm.removeCredential(cred.address) },
                )
                Spacer(Modifier.height(14.dp))
            }

            if (!showImport) {
                OutlinedButton(onClick = { showImport = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("+ 添加门锁凭证")
                }
            }

        }

        if (showImport) {
            Spacer(Modifier.height(14.dp))
            ImportPanel(
                onImport = { cred ->
                    vm.importCredential(cred)
                    showImport = false
                },
                showCancel = locks.isNotEmpty(),
                onCancel = { showImport = false },
                onEnsureNotificationPermission = onEnsureNotificationPermission,
            )
        }

        Spacer(Modifier.height(18.dp))
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))
        Text("关于", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        AboutCard()
        Spacer(Modifier.height(10.dp))
        UpdateCard(repo = app.updateRepository, settings = app.updateSettings)

        if (vm.logText.isNotBlank()) {
            Spacer(Modifier.height(18.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))
            Text("收发时序", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    vm.logText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }

        Spacer(Modifier.height(30.dp))
    }
}

/**
 * 「关于」卡片：应用名 + 作者 + 落地页 / 开源仓库两个入口。
 *
 * 两个地址都统一从 [UpdateSources] 取，避免和「检查更新」的源各写一份、改的时候漏掉。
 */
@Composable
private fun AboutCard() {
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                stringResource(R.string.app_name),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "作者：牧冷",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { openInBrowser(context, UpdateSources.PAGES_URL) },
                    modifier = Modifier.weight(1f),
                ) { Text("打开落地页", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = { openInBrowser(context, UpdateSources.REPO_URL) },
                    modifier = Modifier.weight(1f),
                ) { Text("GitHub 仓库", fontSize = 13.sp) }
            }
        }
    }
}

/** 用系统浏览器打开链接。失败（没有浏览器等）就静默忽略，不打断主界面。 */
private fun openInBrowser(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * 连接状态角标。
 *
 * 背后的规则（见 [com.fktm.app.ble.BleLockClient]）：
 *  · App 一进入前台就**只向已添加的首把门锁**（[target] 的 MAC）预连接，不走扫描；
 *  · **30s 内没有开锁/读电量**就自动断开（冷启动窗口）；
 *  · 一旦开锁，改成**开锁后保活 60s**，期间再开锁重新计时；
 *  · 退到后台立即让出连接（息屏也算）。
 */
@Composable
private fun ConnectionBadge(ready: Boolean, connecting: Boolean, target: String?) {
    // 目标锁的 MAC 尾两段，让用户一眼确认「连的是我这把」而不是楼里其它 ~vTmallLock
    val tail = target?.takeLast(5)?.let { " · 本锁 $it" } ?: ""
    val (mark, text, color) = when {
        ready -> Triple("●", "已预连接$tail · 开锁免建连", MaterialTheme.colorScheme.primary)
        connecting -> Triple("◌", "预连接中$tail", MaterialTheme.colorScheme.primary)
        else -> Triple(
            "○",
            "未连接 · 开锁需先建连",
            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(mark, fontSize = 11.sp, color = color)
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 11.sp, color = color)
    }
}

@Composable
private fun EmptyState(onImport: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("还没有凭证", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "activationKey 就是这把门的钥匙。导入一次，之后永久离线开锁。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("导入凭证") }
        }
    }
}

@Composable
private fun LockCard(
    cred: LockCredential,
    state: UnlockState,
    batteryText: String?,
    onUnlock: () -> Unit,
    onBattery: () -> Unit,
    onRemove: () -> Unit,
) {
    val working = state is UnlockState.Working

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(cred.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "${cred.address}   钥匙 ${cred.maskedKey()}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = onUnlock,
                enabled = !working,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
            ) {
                if (working) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 3.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("正在开锁…", fontSize = 18.sp)
                } else {
                    Text("开 锁", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(12.dp))

            when (state) {
                is UnlockState.Success -> StatusText(state.message, success = true)
                is UnlockState.Failure -> StatusText(state.message, success = false)
                UnlockState.Working -> StatusText("连接门锁中（BLE 建连通常需 1~8 秒）", success = true, dim = true)
                UnlockState.Idle -> StatusText("待命", success = true, dim = true)
            }

            if (batteryText != null) {
                Spacer(Modifier.height(4.dp))
                Text(batteryText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            }

            Spacer(Modifier.height(14.dp))
            Row {
                OutlinedButton(onClick = onBattery, enabled = !working, modifier = Modifier.weight(1f)) {
                    Text("查电量", fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = onRemove, enabled = !working, modifier = Modifier.weight(1f)) {
                    Text("删除", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun StatusText(text: String, success: Boolean, dim: Boolean = false) {
    val color = when {
        dim -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        success -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    Box(Modifier.fillMaxWidth()) {
        Text(
            text,
            color = color,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent),
        )
    }
}

private sealed interface GrabState {
    data object Idle : GrabState
    data object Running : GrabState
    data class Info(val msg: String) : GrabState
    data class Error(val msg: String) : GrabState
}

/**
 * 自动抓取凭证 —— 走 Shizuku，在本机以 shell 身份读 logcat。
 *
 * 等价于在电脑上跑 `fktm/p0_capture.bat`，只是把整套流程搬进了 App：
 * 用户只需要在弹窗里授权一次，然后照着提示去天猫校园点一次开锁。
 */
@Composable
private fun AutoGrabSection(
    onHit: (LogcatParser.Hit) -> Unit,
    onEnsureNotificationPermission: () -> Unit,
) {
    var state by remember { mutableStateOf<GrabState>(GrabState.Idle) }

    /** 是否已经通过结果文件填入过 —— 防止后到的 binder「失败」把已到手的成功盖掉。 */
    var applied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // ★ 兜底取件通道（这是「抓不到」问题的真正解药）
    //
    // ColorOS 会把切到后台的 fkTM 冻结上百秒（do_freezer_trap），冻结期间
    // binder 回复根本送不回来 —— 界面就永远停在「抓取中…」。
    // 服务端因此改成「命中即落盘」，这里只要每秒问一次那个结果文件：
    // 进程一解冻（用户切回 App）就立刻读到，不必等 binder 回复。
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            val hit = ShizukuLogcatGrabber.consumeHit(context) ?: continue
            applied = true
            onHit(hit)
            state = if (hit.isComplete) {
                GrabState.Info("已抓到凭证并填入下方表单，确认后点「保存」")
            } else {
                GrabState.Info("已抓到钥匙，但日志里没有门锁地址，请手动填写地址")
            }
        }
    }

    // Shizuku 的授权回调：允许后自动接上，用户不必再点一次
    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { code, grant ->
            if (code == ShizukuLogcatGrabber.REQUEST_CODE) {
                state = if (grant == PackageManager.PERMISSION_GRANTED) {
                    GrabState.Info("已授权，再点一次「开始抓取」")
                } else {
                    GrabState.Error("授权被拒绝 —— 需要在 Shizuku 里允许 TmallKey")
                }
            }
        }
        val registered = runCatching {
            Shizuku.addRequestPermissionResultListener(listener)
        }.isSuccess
        onDispose {
            if (registered) runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
        }
    }

    fun start() {
        if (!ShizukuLogcatGrabber.isAvailable()) {
            state = GrabState.Error(
                "Shizuku 未运行。请先安装 Shizuku 并用无线调试启动它，" +
                    "然后回到这里重试。"
            )
            return
        }
        if (!ShizukuLogcatGrabber.hasPermission()) {
            ShizukuLogcatGrabber.requestPermission()
            state = GrabState.Info("已弹出授权框 —— 允许后再点一次「开始抓取」")
            return
        }
        applied = false
        state = GrabState.Running
        scope.launch {
            // 抓取要求用户切到天猫校园开锁 → TmallKey 全程在后台，而 ColorOS 会冻结后台进程。
            // 起前台服务提高优先级（能拖住一些时间），但**不能指望它挡住冻结** ——
            // 真正的保命机制是「服务端命中即落盘 + 界面轮询取件」（见上方 LaunchedEffect）。
            //
            // 前台服务在 Android 13+ 要有通知权限才能把常驻通知显示出来，
            // 这里再确认一次：缺权限就顺手弹一次申请，用户拒绝也不影响抓取本身。
            onEnsureNotificationPermission()
            GrabForegroundService.start(context)
            try {
                state = when (val r = ShizukuLogcatGrabber.capture(context)) {
                    is ShizukuLogcatGrabber.Result.Found -> {
                        applied = true
                        onHit(r.hit)
                        if (r.hit.isComplete) {
                            GrabState.Info("已抓到凭证并填入下方表单，确认后点「保存」")
                        } else {
                            GrabState.Info("已抓到钥匙，但日志里没有门锁地址，请手动填写地址")
                        }
                    }
                    // 结果文件可能已被界面轮询取走并填好了 —— 那就别再用 binder 的失败盖掉成功
                    is ShizukuLogcatGrabber.Result.Failed ->
                        if (applied) {
                            GrabState.Info("已抓到凭证并填入下方表单，确认后点「保存」")
                        } else {
                            GrabState.Error(r.reason)
                        }
                    ShizukuLogcatGrabber.Result.PermissionDenied ->
                        GrabState.Error("缺少 Shizuku 授权")
                }
            } finally {
                GrabForegroundService.stop(context)
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("自动抓取钥匙站", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "经 Shizuku 读天猫校园自己的日志，抓取它下发的 activationKey。\n" +
                    "点了「开始抓取」后，切到天猫校园在门口开一次锁即可；\n" +
                    "开完回到本 App，会自动填入下方表单。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )

            when (val s = state) {
                GrabState.Idle -> {}
                GrabState.Running -> {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "正在后台监听日志 —— 现在去天猫校园点一次开锁，" +
                            "开完回到本 App 即会自动填入（最多监听 4 分钟）。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                is GrabState.Info -> {
                    Spacer(Modifier.height(8.dp))
                    Text(s.msg, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
                is GrabState.Error -> {
                    Spacer(Modifier.height(8.dp))
                    Text(s.msg, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { start() },
                enabled = state != GrabState.Running,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state == GrabState.Running) "抓取中…" else "开始抓取") }
        }
    }
}

@Composable
private fun ImportPanel(
    onImport: (LockCredential) -> Unit,
    showCancel: Boolean,
    onCancel: () -> Unit,
    onEnsureNotificationPermission: () -> Unit,
) {
    var name by remember { mutableStateOf("宿舍门") }
    var address by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("导入凭证", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "可从 PC 端 fktm/credentials.json 直接粘贴 JSON；或手工填写。\n" +
                        "钥匙只存本机 Keystore 加密区，不上云、不参与备份。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(12.dp))

            AutoGrabSection(
                onHit = { hit ->
                    key = hit.activationKey
                    hit.macAddress?.let { address = it }
                    error = null
                },
                onEnsureNotificationPermission = onEnsureNotificationPermission,
            )

            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = pasted,
                onValueChange = { pasted = it },
                label = { Text("粘贴 JSON（可选）") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    error = null
                    val parsed = parsePasted(pasted)
                    if (parsed == null) {
                        error = "JSON 解析失败：需要包含 address 与 activationKey"
                    } else {
                        onImport(parsed)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("解析并导入") }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("门锁地址，如 AA:BB:CC:DD:EE:FF") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("activationKey（32 位 hex）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        error = null
                        val a = address.trim()
                        val k = key.trim().uppercase()
                        if (!a.matches(Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$"))) {
                            error = "地址格式不对（应为 AA:BB:CC:DD:EE:FF）"
                        } else if (!k.matches(Regex("^[0-9A-F]{32}$"))) {
                            error = "activationKey 应为 32 位 hex"
                        } else {
                            onImport(LockCredential(name = name.ifBlank { "宿舍门" }, address = a, activationKey = k))
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("保存") }

                if (showCancel) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
                }
            }
        }
    }
}

/** 兼容两种输入：完整的 credentials.json（含 locks 数组），或单个凭证对象。 */
private fun parsePasted(text: String): LockCredential? {
    val t = text.trim()
    if (t.isEmpty()) return null
    return try {
        val obj = JSONObject(t)
        val lockObj = if (obj.has("locks")) obj.getJSONArray("locks").getJSONObject(0) else obj
        LockCredential(
            name = lockObj.optString("name", "宿舍门"),
            address = lockObj.getString("address").trim(),
            activationKey = lockObj.getString("activationKey").trim().uppercase(),
        )
    } catch (_: Exception) {
        null
    }
}
