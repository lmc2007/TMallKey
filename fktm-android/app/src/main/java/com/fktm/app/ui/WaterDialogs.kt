package com.fktm.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fktm.app.water.WaterDeviceSortMode
import com.fktm.app.water.WaterDevices

@Composable
internal fun WaterAddDialog(onScan: () -> Unit, onManualInput: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("添加饮水机") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text("扫描设备二维码") }
                OutlinedButton(onClick = onManualInput, modifier = Modifier.fillMaxWidth()) { Text("手动输入设备号") }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("取消") } },
    )
}

@Composable
internal fun WaterManualDialog(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onAdd: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("手动输入设备号") },
        text = {
            OutlinedTextField(
                value = value, onValueChange = onValueChange, singleLine = true,
                label = { Text("设备编号") }, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = onAdd, enabled = enabled && value.isNotBlank()) { Text("添加") } },
        dismissButton = { TextButton(onClick = onClose) { Text("取消") } },
    )
}

@Composable
internal fun WaterSettingsDialog(
    mode: WaterDeviceSortMode,
    account: String,
    loggedIn: Boolean,
    onSort: (WaterDeviceSortMode) -> Unit,
    onLogout: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("饮水设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("设备排序")
                WaterSortOption("最近使用", mode == WaterDeviceSortMode.RECENT) { onSort(WaterDeviceSortMode.RECENT) }
                WaterSortOption("自定义排序", mode == WaterDeviceSortMode.CUSTOM) { onSort(WaterDeviceSortMode.CUSTOM) }
                if (mode == WaterDeviceSortMode.CUSTOM) Text("在「管理设备」中调整顺序。")
                if (loggedIn) {
                    if (account.isNotBlank()) Text("当前账号 $account")
                    TextButton(onClick = onLogout) { Text("退出登录") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("完成") } },
    )
}

@Composable
private fun WaterSortOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, onClick = onClick, role = Role.RadioButton),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label)
    }
}

@Composable
internal fun WaterManageDialog(
    devices: List<WaterDevices.Saved>,
    custom: Boolean,
    onSave: (List<WaterDevices.Saved>) -> Unit,
    onClose: () -> Unit,
) {
    var draftIds by rememberSaveable { mutableStateOf(ArrayList(devices.map { it.resNo })) }
    val byId = devices.associateBy { it.resNo }
    val draft = draftIds.mapNotNull(byId::get)

    fun move(from: Int, to: Int) {
        draftIds = ArrayList(draft.map { it.resNo }).apply { add(to, removeAt(from)) }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("管理设备") },
        text = {
            if (draft.isEmpty()) {
                Text("设备列表为空")
            } else {
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    itemsIndexed(draft, key = { _, device -> device.resNo }) { index, device ->
                        Column {
                            Text(device.name)
                            DeviceLocation(device.location)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                if (custom) {
                                    IconButton(
                                        onClick = { move(index, index - 1) }, enabled = index > 0,
                                        modifier = Modifier.semantics { contentDescription = "上移 ${device.name} ${device.location}" },
                                    ) { Text("↑") }
                                    IconButton(
                                        onClick = { move(index, index + 1) }, enabled = index < draft.lastIndex,
                                        modifier = Modifier.semantics { contentDescription = "下移 ${device.name} ${device.location}" },
                                    ) { Text("↓") }
                                }
                                TextButton(
                                    onClick = { draftIds = ArrayList(draftIds.filterNot { it == device.resNo }) },
                                    modifier = Modifier.semantics { contentDescription = "移除 ${device.name} ${device.location}" },
                                ) { Text("移除") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onClose) { Text("取消") } },
    )
}
