package com.fktm.app.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.fktm.app.vm.UnlockState
import com.fktm.app.vm.UnlockViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.fktm.app.credential.RootGrabber
import org.json.JSONObject

@Composable
fun MainScreen(
    vm: UnlockViewModel,
    onRequestPermissions: () -> Unit,
    onEnsureNotificationPermission: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val locks = vm.locks
    var showImport by remember { mutableStateOf(!vm.locks.any()) }
    val app = LocalContext.current.applicationContext as FktmApp

    // 从饮水页抓到新门锁凭证后，切回本页时刷新列表
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                vm.refresh()
                // 从天猫校园模块拉门锁与会话：用户在那边开过门锁页，这里就自动入库
                vm.syncFromModule()
                vm.mvPrewarm()   // MV 锁预连接：连上 20s 没开锁自动断开
            }
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
            EmptyState(onImport = { showImport = true }, onOpenAbout = onOpenAbout)
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
                onMvDetected = { mac ->
                    vm.importCredential(
                        LockCredential(
                            name = "MV 锁 ${mac.takeLast(5)}",
                            address = mac,
                            activationKey = "",
                            type = LockCredential.TYPE_MV,
                        ),
                    )
                },
                showCancel = locks.isNotEmpty(),
                onCancel = { showImport = false },
                onEnsureNotificationPermission = onEnsureNotificationPermission,
                onOpenAbout = onOpenAbout,
            )
        }


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
private fun EmptyState(onImport: () -> Unit, onOpenAbout: () -> Unit) {
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
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                Text("免 root 怎么拿钥匙？看补丁版教程", fontSize = 12.sp)
            }
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
 * root 一键抓取：一次按钮同时拿「门锁钥匙 + 饮水登录态」。
 *
 * ① `su -M` 读天猫校园 WebView cookie 库 → 存为本机登录态（Keystore 加密）
 * ② `su -M` 流式监听 logcat → 用户去开一次门 → activationKey 填入表单
 *
 * 无 root 时不阻塞使用：天猫校园那边打开一次门锁页，模块会推给 fkTM 自动入库。
 */
@Composable
private fun RootGrabSection(
    onHit: (LogcatParser.Hit) -> Unit,
    onMvDetected: (String) -> Unit,
    onEnsureNotificationPermission: () -> Unit,
) {
    var state by remember { mutableStateOf<GrabState>(GrabState.Idle) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun start() {
        scope.launch {
            if (!RootGrabber.hasRoot()) {
                state = GrabState.Error(
                    "root 不可用（su 未授权或不存在）。无 root 也能用：" +
                        "在天猫校园里打开一次门锁页，本 App 会自动把钥匙收进来；" +
                        "或在下面手工填地址与钥匙。",
                )
                return@launch
            }
            onEnsureNotificationPermission()
            GrabForegroundService.start(context)
            state = GrabState.Running
            try {
                // ① 先等开锁：用户此刻手里可能还没打开天猫校园页面，
                //    这时读到的 cookie2 大概率是未激活的 —— 等开锁日志出现
                //    （说明用户刚用过天猫校园），再抓 cookie 才是新鲜有效的。
                //    VOC / MV 谁先出现算谁（RootGrabber.Outcome）。
                // ② 开锁已发生 → 抓 cookie（此刻的 cookie2 刚被 native sync 过，有效）
                val cookieMsg = try {
                    val jar = com.fktm.app.water.WaterSession.importFromDevice(context)
                    com.fktm.app.water.WaterSession.adopt(context, jar)
                    val cmOk = withContext(Dispatchers.IO) {
                        com.fktm.app.water.WaterSession.campusProbeOk(jar)
                    }
                    com.fktm.app.water.WaterDiag.log(
                        context, "rootGrab",
                        com.fktm.app.water.WaterDiag.cookieSummary(jar) + " cm=$cmOk",
                    )
                    val acc = com.fktm.app.water.WaterSession
                        .maskAccount(com.fktm.app.water.WaterSession.accountId(jar))
                    "登录态已导入（$acc）" + if (!cmOk) {
                        "；会话未激活：去天猫校园打开一次「智能宿舍」页，再到饮水页重新导入"
                    } else ""
                } catch (e: Throwable) {
                    com.fktm.app.water.WaterDiag.log(context, "rootGrab", "cookie fail ${e.message}")
                    "登录态导入失败（${e.message}）"
                }

                state = when (val outcome = RootGrabber.capture(timeoutMs = 240_000L)) {
                    is RootGrabber.Outcome.VocHit -> {
                        onHit(outcome.hit)
                        val keyMsg = if (outcome.hit.isComplete) {
                            "钥匙已抓到并填入下方表单"
                        } else {
                            "已抓到钥匙，但日志里没有门锁地址，请手动填写"
                        }
                        GrabState.Info("$keyMsg。$cookieMsg")
                    }
                    is RootGrabber.Outcome.MvHit -> {
                        // MV 锁（掌门神通）：没有静态钥匙，添加为在线门锁，开锁时联网取帧
                        onMvDetected(outcome.mac)
                        com.fktm.app.water.WaterDiag.log(context, "rootGrab", "MV lock ${outcome.mac}")
                        GrabState.Info(
                            "检测到 MV 锁（掌门神通），已加入门锁列表。$cookieMsg" +
                                "—— 每次开锁需联网，在门锁卡片上点「开锁」即可",
                        )
                    }
                    RootGrabber.Outcome.None -> GrabState.Error("4 分钟内没等到开锁日志，已取消。$cookieMsg")
                }
            } finally {
                GrabForegroundService.stop(context)
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("root 抓取", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "支持快速抓取登录态与宿舍钥匙；MV 锁的唯一抓取方式。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { start() },
                enabled = state !is GrabState.Running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state is GrabState.Running) "抓取中…（去天猫校园开一次门）" else "root 抓取",
                    fontSize = 14.sp,
                )
            }
            when (val s = state) {
                is GrabState.Info -> {
                    Spacer(Modifier.height(8.dp))
                    Text(s.msg, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
                is GrabState.Error -> {
                    Spacer(Modifier.height(8.dp))
                    Text(s.msg, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                else -> {}
            }
        }
    }
}

/**
 * 「钥匙怎么来」提示卡。
 *
 * 放在获取凭证面板的最上方：第一次进来的人手里什么都没有，
 * 这段是唯一能把他引到正确路径（补丁版点一次开锁 → 自动导入）的地方。
 */
@Composable
private fun KeyHintCard(onOpenAbout: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("钥匙怎么来？", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "免 root：装一次补丁版天猫校园 → 打开它的「门锁」页 → 点一次开锁，" +
                    "钥匙会自动出现在这里，之后开锁完全不用它。\n" +
                    "有 root：用下面的「root 抓取」，一次拿齐钥匙和饮水登录态。",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
            TextButton(onClick = onOpenAbout) {
                Text("补丁版教程与下载 →", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ImportPanel(
    onImport: (LockCredential) -> Unit,
    onMvDetected: (String) -> Unit,
    showCancel: Boolean,
    onCancel: () -> Unit,
    onEnsureNotificationPermission: () -> Unit,
    onOpenAbout: () -> Unit,
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

            KeyHintCard(onOpenAbout = onOpenAbout)

            Spacer(Modifier.height(12.dp))

            RootGrabSection(
                onHit = { hit ->
                    key = hit.activationKey
                    hit.macAddress?.let { address = it }
                    error = null
                },
                onMvDetected = onMvDetected,
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
