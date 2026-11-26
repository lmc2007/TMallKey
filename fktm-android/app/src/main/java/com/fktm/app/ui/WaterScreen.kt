package com.fktm.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fktm.app.vm.WaterViewModel
import com.fktm.app.water.WaterDeviceSortMode
import com.fktm.app.water.WaterDevices

private enum class WaterPanel { ADD, SCANNER, MANUAL, SETTINGS, MANAGE, PHONE_LOGIN }

@Composable
fun WaterScreen(vm: WaterViewModel) {
    val context = LocalContext.current
    val state = vm.state
    val active = state.activeDevice
    val canEdit = !state.busy && active == null
    val canUse = canEdit && state.loggedIn
    var panel by rememberSaveable { mutableStateOf<WaterPanel?>(null) }
    var manual by rememberSaveable { mutableStateOf("") }
    var confirmPhysicalEnd by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { vm.prepare(context) }
    LaunchedEffect(active?.resNo) { if (active != null) listState.scrollToItem(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.syncIfImported(context)
                vm.resumeAfterActivation(context)
                vm.syncFromTmall(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (panel) {
        WaterPanel.PHONE_LOGIN -> {
            BackHandler { panel = null }
            PhoneLoginScreen(
                onResult = { jar -> panel = null; vm.onLoggedIn(context, jar) },
                onCancel = { panel = null },
            )
            return
        }
        WaterPanel.SCANNER -> {
            BackHandler { panel = null }
            QrScannerScreen(
                onDetected = { text -> panel = null; vm.addDevice(context, text) },
                onManualInput = { panel = WaterPanel.MANUAL },
                onClose = { panel = null },
            )
            return
        }
        WaterPanel.ADD -> WaterAddDialog(
            onScan = { panel = WaterPanel.SCANNER },
            onManualInput = { panel = WaterPanel.MANUAL },
            onClose = { panel = null },
        )
        WaterPanel.MANUAL -> WaterManualDialog(
            value = manual,
            onValueChange = { manual = it },
            enabled = canUse,
            onAdd = {
                vm.addDevice(context, manual)
                panel = null
            },
            onClose = { panel = null },
        )
        WaterPanel.SETTINGS -> WaterSettingsDialog(
            mode = state.sortMode,
            account = state.account,
            loggedIn = state.loggedIn,
            onSort = { vm.setSortMode(context, it) },
            onLogout = { vm.logout(context); panel = null },
            onClose = { panel = null },
        )
        WaterPanel.MANAGE -> WaterManageDialog(
            devices = state.devices,
            custom = state.sortMode == WaterDeviceSortMode.CUSTOM,
            onSave = { kept ->
                val keptIds = kept.map { it.resNo }
                state.devices.filterNot { it.resNo in keptIds }.forEach { vm.removeDevice(context, it.resNo) }
                if (state.sortMode == WaterDeviceSortMode.CUSTOM) vm.saveCustomOrder(context, keptIds)
                panel = null
            },
            onClose = { panel = null },
        )
        null -> Unit
    }

    if (confirmPhysicalEnd && active != null && state.error != null) {
        AlertDialog(
            onDismissRequest = { confirmPhysicalEnd = false },
            title = { Text("确认机器已结束接水？") },
            text = { Text("请先在饮水机上确认已结束。这会关闭 App 内的接水卡片。") },
            confirmButton = {
                TextButton(onClick = { vm.confirmPhysicalEnd(); confirmPhysicalEnd = false }) {
                    Text("确认已结束")
                }
            },
            dismissButton = { TextButton(onClick = { confirmPhysicalEnd = false }) { Text("取消") } },
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("饮水", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                if (active == null) {
                    if (state.devices.isNotEmpty()) {
                        TextButton(onClick = { panel = WaterPanel.ADD }, enabled = canUse) { Text("＋ 添加") }
                    }
                    TextButton(onClick = { panel = WaterPanel.SETTINGS }, enabled = canEdit) { Text("饮水设置") }
                }
            }
        }
        if (active != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(active.name, style = MaterialTheme.typography.titleLarge)
                        DeviceLocation(active.location)
                        Text("已解锁，请按饮水机上的出水键。")
                        Text("接完后，也可以按机器上的「暂停/结束」。", style = MaterialTheme.typography.bodySmall)
                        state.error?.let { WaterError(it) }
                        if (state.needsLogin) {
                            TextButton(onClick = { vm.importFromDevice(context) }, enabled = !state.busy) {
                                Text("从天猫校园恢复登录")
                            }
                            TextButton(onClick = { panel = WaterPanel.PHONE_LOGIN }, enabled = !state.busy) {
                                Text("手机号重新登录（实验）")
                            }
                        }
                        Button(onClick = { vm.stop(context) }, enabled = !state.busy && !state.needsLogin, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.busy) "结束中…" else if (state.error != null) "重试结束接水" else "结束接水")
                        }
                        if (state.error != null && !state.busy) {
                            TextButton(onClick = { confirmPhysicalEnd = true }) { Text("我已在机器上结束") }
                        }
                    }
                }
            }
        } else {
            if (state.needsLogin) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("登录后可接水", style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { vm.importFromDevice(context) }, enabled = canEdit) { Text("从天猫校园导入") }
                            TextButton(onClick = { panel = WaterPanel.PHONE_LOGIN }, enabled = canEdit) {
                                Text("手机号登录（实验）")
                            }
                        }
                    }
                }
            }
            if (state.processingResNo == null && state.status.isNotBlank() &&
                (state.busy || state.status.startsWith("已添加") || state.status == "已结束接水" || state.needsLogin)
            ) {
                item { Text(state.status, style = MaterialTheme.typography.bodyMedium) }
            }
            state.error?.let { error -> item { WaterError(error, onDismiss = vm::dismissError) } }
            if (state.devices.isEmpty()) {
                item {
                    if (state.loggedIn) {
                        Button(onClick = { panel = WaterPanel.ADD }, enabled = canUse, modifier = Modifier.fillMaxWidth()) {
                            Text("添加饮水机")
                        }
                    }
                }
            } else {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("我的饮水机", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { panel = WaterPanel.MANAGE }, enabled = canEdit) { Text("管理设备") }
                    }
                }
                items(state.devices, key = { it.resNo }) { device ->
                    val progress = state.status.takeIf { state.processingResNo == device.resNo }
                    val error = state.deviceError?.takeIf { it.resNo == device.resNo }?.message
                    WaterDeviceRow(device, progress, error, canUse) { vm.quickUse(context, device.resNo) }
                }
            }
        }
    }
}

@Composable
private fun WaterDeviceRow(
    device: WaterDevices.Saved,
    progress: String?,
    error: String?,
    canUse: Boolean,
    onUse: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                    DeviceLocation(device.location)
                }
                Button(
                    onClick = onUse,
                    enabled = canUse,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "解锁接水，${device.name} ${device.location}" },
                ) { Text(progress ?: "解锁接水") }
            }
            if (error != null) WaterError(error)
        }
    }
}

@Composable
internal fun DeviceLocation(location: String) {
    if (location.isNotBlank()) {
        Text(location, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun WaterError(text: String, onDismiss: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        if (onDismiss != null) TextButton(onClick = onDismiss) { Text("关闭") }
    }
}
