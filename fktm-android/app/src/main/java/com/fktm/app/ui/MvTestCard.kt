package com.fktm.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fktm.app.mv.MvBleClient
import com.fktm.app.mv.MvLockApi
import com.fktm.app.water.WaterSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MV 锁（掌门神通）在线开锁 —— 测试卡。
 *
 * 链路：root 导入的登录态 → guide.zmst.unlock.data.get → BLE 写 AFF1。
 * 与 VOC 的「抓一次钥匙永久离线」不同，MV 每次开锁都要联网取新帧。
 */
@Composable
fun MvTestCard(onRequestPermissions: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mac by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("MV 锁测试（掌门神通 · 在线模式）", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "输入锁的 MAC（天猫校园门锁页能看到，或形如 MVLxxxx 的锁）。" +
                    "每次开锁需要联网，登录态来自「饮水」页导入的那份。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = mac,
                onValueChange = { mac = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("锁 MAC（如 D2:01:7C:A1:D8:A6）") },
                supportingText = { Text("不填分隔符也行") },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        onRequestPermissions()
                        val normalized = MvLockApi.normalizeMac(mac)
                        if (normalized == null) {
                            status = "MAC 格式不对：需要 12 位十六进制"
                            return@Button
                        }
                        busy = true
                        scope.launch {
                            try {
                                val jar = WaterSession.cached()
                                if (jar == null || WaterSession.accountId(jar).isBlank()) {
                                    error("没有登录态：先到「饮水」页点一次「导入登录态」")
                                }
                                com.fktm.app.water.WaterDiag.log(
                                    context, "mv",
                                    "mac=$normalized cookies=${jar.size} un=${WaterSession.accountId(jar)}",
                                )
                                status = "① 拉取开锁数据…"
                                val api = withContext(Dispatchers.IO) { MvLockApi(jar) }
                                val d = withContext(Dispatchers.IO) { api.fetchUnlockData(normalized) }
                                com.fktm.app.water.WaterDiag.log(
                                    context, "mv", "data ok dev=${d.deviceName} enc=${d.encryptData.take(24)}…",
                                )
                                status = "② BLE 连接 ${d.deviceName.ifBlank { normalized }}…"
                                val resp = MvBleClient.unlock(context, d.macId, d.encryptData)
                                status = "✅ 指令已写入，锁回包: $resp"
                            } catch (e: Throwable) {
                                // 完整响应体落诊断（CE003 这类服务端码要看原始 payload 才能定位）
                                val detail = (e as? com.fktm.app.water.MtopError)?.payload?.take(400) ?: e.message
                                com.fktm.app.water.WaterDiag.log(context, "mv", "FAIL ${e.javaClass.simpleName} $detail")
                                status = "❌ ${e.message}"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (busy) "开锁中…" else "MV 开锁（测试）")
                }
                if (busy) {
                    Spacer(Modifier.width(10.dp))
                    CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                }
            }
            if (status.isNotBlank()) {
                Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
