package com.fktm.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fktm.app.vm.WaterViewModel
import com.fktm.app.water.WaterDevice
import com.fktm.app.water.WaterDevices

/**
 * 校园饮水页：登录态 → 选中设备 → 解锁。
 *
 * 三条必须让用户看见的事实：
 *  1. **解锁 ≠ 出水**。服务端只是把机器开锁，出水要按机器上的物理按键。
 *  2. **不按就超时自动结单**，0 元（本校接水免费）。
 *  3. 登录态是从本机天猫校园导入的，不需要 root 常驻。
 */
@Composable
fun WaterScreen(
    vm: WaterViewModel,
) {
    val context = LocalContext.current
    val state = vm.state
    var showScanner by remember { mutableStateOf(false) }
    var showPhoneLogin by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.prepare(context) }

    // 从别处回来（比如去天猫校园走了一趟，模块把 cookie 广播过来了）→ 自动刷新
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                vm.syncIfImported(context)
                vm.syncFromTmall(context)      // 免 root：从天猫校园进程拉登录态
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showPhoneLogin) {
        PhoneLoginScreen(
            onResult = { jar ->
                showPhoneLogin = false
                vm.onLoggedIn(context, jar)
            },
            onCancel = { showPhoneLogin = false },
        )
        return
    }

    if (showScanner) {
        QrScannerScreen(
            onDetected = { text ->
                showScanner = false
                vm.resolve(context, text)
            },
            onClose = { showScanner = false },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text("校园饮水", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "扫码接水 · 免费用水",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SessionCard(
            busy = state.busy,
            loggedIn = state.loggedIn,
            needsLogin = state.needsLogin,
            status = state.status,
            account = state.account,
            onImport = { vm.importFromDevice(context) },
            onLogout = { vm.logout(context) },
        )

        if (state.devices.isNotEmpty()) {
            DevicesCard(
                devices = state.devices,
                currentResNo = state.device?.resNo,
                busy = state.busy,
                onPick = { vm.resolve(context, it) },
                onRemove = { vm.removeDevice(context, it) },
            )
        }

        if (!state.loggedIn) {
            ImportCard(
                busy = state.busy,
                onImport = { vm.importFromDevice(context) },
                onPhoneLogin = { showPhoneLogin = true },
            )
        }

        state.error?.let { msg ->
            ErrorCard(text = msg, onDismiss = { vm.dismissError() })
        }

        // ---- 已选中的设备 + 解锁（放在扫码之前，主操作更靠上）----
        state.device?.let { d ->
            DeviceCard(device = d)
            if (state.orderId.isNotBlank()) {
                UnlockedCard(
                    orderId = state.orderId,
                    busy = state.busy,
                    onStop = { vm.stop() },
                )
            } else {
                Button(
                    onClick = { vm.unlock(context) },
                    enabled = !state.busy && d.unlockable,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(18.dp).height(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(if (state.busy) "处理中…" else "解锁接水", fontSize = 17.sp)
                }
                if (!d.unlockable) {
                    Text(
                        "这台机器现在不能解锁：" +
                            when {
                                !d.online -> "设备离线"
                                !d.inBusinessTime -> "不在营业时段"
                                !d.canUse -> "设备停用"
                                else -> d.faultText.ifBlank { "状态异常" }
                            },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        ScanCard(
            enabled = !state.busy && state.loggedIn,
            manual = manual,
            onManualChange = { manual = it },
            onScan = { showScanner = true },
            onSubmit = { vm.resolve(context, manual) },
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SessionCard(
    busy: Boolean,
    loggedIn: Boolean,
    needsLogin: Boolean,
    status: String,
    account: String,
    onImport: () -> Unit,
    onLogout: () -> Unit,
) {
    val dot = when {
        loggedIn -> Color(0xFF3FBF7F)
        busy -> Color(0xFFF2B33D)
        else -> Color(0xFFE06C6C)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("●", color = dot, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    loggedIn && account.isNotBlank() -> "淘宝账号 $account · $status"
                    loggedIn -> "淘宝登录态就绪 · $status"
                    busy -> status.ifBlank { "处理中…" }
                    needsLogin -> "未登录（饮水接口必须有登录态）"
                    else -> "尚未登录"
                },
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            if (loggedIn) {
                TextButton(onClick = onLogout, enabled = !busy) { Text("清除", fontSize = 12.sp) }
            } else {
                TextButton(onClick = onImport, enabled = !busy) { Text("导入", fontSize = 12.sp) }
            }
        }
    }
}

/**
 * 最近使用的饮水机。
 *
 * 扫一次就记住一台，按「最近使用」倒序 —— 宿舍楼下那台永远在最上面。
 * 点一下就重新识别（顺带刷新在线状态），右边的 × 只删本地记录。
 */
@Composable
private fun DevicesCard(
    devices: List<WaterDevices.Saved>,
    currentResNo: String?,
    busy: Boolean,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Text(
                "最近使用",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            devices.forEach { d ->
                val isCurrent = d.resNo == currentResNo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy) { onPick(d.resNo) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            d.name,
                            fontSize = 15.sp,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isCurrent) Color(0xFF2E9963)
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            listOf(d.location, WaterDevices.relativeTime(d.lastUsedAt))
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                                .ifBlank { d.resNo },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onRemove(d.resNo) }, enabled = !busy) {
                        Text("×", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

/**
 * 导入登录态。
 *
 * 不自己登录（试过四条路都被 app 身份挡住），而是把天猫校园已有的登录态搬过来：
 * WebView 的 cookie 库是明文 SQLite，root 读一次（`su -M`），之后加密存本机，**不再需要 root**。
 */
@Composable
private fun ImportCard(
    busy: Boolean,
    onImport: () -> Unit,
    onPhoneLogin: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("① 导入天猫校园的登录态", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "本应用不自己登录（登录要 appkey + 阿里安全签名，搬不过来），" +
                    "而是直接把手机上天猫校园已登录的 cookie 读过来。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
            )
            Text(
                "只会申请一次 root：读完就加密存本机，之后完全不需要 root，" +
                    "直到 cookie 过期（约 24 天）。全程只读，不改天猫校园任何数据。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
            )
            Button(
                onClick = onImport,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(18.dp).height(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (busy) "读取中…" else "导入登录态（需 root 一次）", fontSize = 16.sp)
            }
            OutlinedButton(
                onClick = onPhoneLogin,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("免 root：用手机号 + 短信登录（实验）", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ScanCard(
    enabled: Boolean,
    manual: String,
    onManualChange: (String) -> Unit,
    onScan: () -> Unit,
    onSubmit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onScan,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("扫描设备二维码", fontSize = 16.sp)
            }
            Text(
                "或者手动填设备号（机器上的编号，等价的 tmxy… 也可以）",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = manual,
                onValueChange = onManualChange,
                singleLine = true,
                label = { Text("设备编号") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = onSubmit,
                enabled = enabled && manual.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("识别设备")
            }
        }
    }
}

@Composable
private fun DeviceCard(device: WaterDevice) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(device.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(
                    device.building.ifBlank { null },
                    device.address.ifBlank { null },
                ).joinToString(" · ").ifBlank { device.campus },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(if (device.online) "在线" else "离线", device.online)
                Chip(if (device.inBusinessTime) "营业中" else "非营业", device.inBusinessTime)
                Chip("免费", true)
            }
            Text(
                "资源号 ${device.resNo}  ·  设备 ID ${device.deviceId}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Chip(text: String, ok: Boolean) {
    val bg = if (ok) Color(0xFF3FBF7F).copy(alpha = 0.15f) else Color(0xFFE06C6C).copy(alpha = 0.15f)
    val fg = if (ok) Color(0xFF2E9963) else Color(0xFFC05050)
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = bg)) {
        Text(
            text,
            fontSize = 11.sp,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun UnlockedCard(orderId: String, busy: Boolean, onStop: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3FBF7F).copy(alpha = 0.12f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("已解锁", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E9963))
            Text(
                "请在饮水机上按「出水」键接水；接完按「暂停/结束」。\n" +
                    "不操作的话，机器超时后会按 0 元自动结单。",
                fontSize = 12.sp,
            )
            if (orderId.isNotBlank()) {
                Text(
                    "订单 $orderId",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onStop,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("结束接水")
            }
        }
    }
}

@Composable
private fun ErrorCard(text: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE06C6C).copy(alpha = 0.12f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text, fontSize = 12.sp, color = Color(0xFFC05050))
            TextButton(onClick = onDismiss) { Text("知道了", fontSize = 12.sp) }
        }
    }
}
