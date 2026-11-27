package com.fktm.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fktm.app.credential.CredentialBackup
import com.fktm.app.vm.UnlockViewModel
import com.fktm.app.vm.UnlockViewModel.SessionRestore
import com.fktm.app.water.WaterSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun CredentialBackupActions(vm: UnlockViewModel, onRestored: () -> Unit) {
    val scope = rememberCoroutineScope()
    var source by rememberSaveable { mutableStateOf<Uri?>(null) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val saveDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        scope.launch {
            busy = true
            try {
                vm.saveCredentialBackup(uri)
                if (uri != null) status = "备份已导出"
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                status = "无法保存备份，请重试或改选保存位置。"
            } finally {
                busy = false
            }
        }
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        source = uri
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    status = null
                    try {
                        openDocument.launch(arrayOf("*/*"))
                    } catch (_: android.content.ActivityNotFoundException) {
                        status = "无法打开系统文件选择器。"
                    }
                },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text("导入备份") }
            OutlinedButton(
                onClick = {
                    status = null
                    showExport = true
                },
                enabled = !busy && vm.canExportCredentialBackup(),
                modifier = Modifier.weight(1f),
            ) { Text("导出凭证") }
        }
        val message = if (busy) "正在处理备份…" else status
        if (message != null) Text(message, style = MaterialTheme.typography.bodySmall)
    }

    val document = source
    if (showExport || document != null) {
        val exporting = showExport
        var password by remember { mutableStateOf("") }
        var confirmation by remember { mutableStateOf("") }
        var includeCampusSession by rememberSaveable { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val dismiss = {
            if (!busy) {
                showExport = false
                source = null
            }
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(if (exporting) "导出凭证备份" else "导入凭证备份") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (exporting) "给备份设一个至少 ${CredentialBackup.MIN_PASSWORD_LENGTH} 个字符的密码。导入时需要同一个密码。"
                        else "输入备份密码。同一把门锁会保留本机凭证。若备份包含天猫校园登录态，导入时会替换本机登录态。",
                    )
                    BackupPasswordField(password, "备份密码", !busy) {
                        password = it
                        error = null
                    }
                    if (exporting) {
                        BackupPasswordField(confirmation, "确认密码", !busy) {
                            confirmation = it
                            error = null
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().toggleable(
                                value = includeCampusSession,
                                enabled = !busy,
                                role = Role.Checkbox,
                                onValueChange = {
                                    includeCampusSession = it
                                    error = null
                                },
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = includeCampusSession, onCheckedChange = null, enabled = !busy)
                            Text("包含天猫校园登录态")
                        }
                        if (includeCampusSession) {
                            Text(
                                "请注意,将您的登录态分享给别人意味着别人可以使用您的账号接水,这可能产生消费",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    if (busy) Text("正在处理…")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && password.isNotEmpty() && (!exporting || confirmation.isNotEmpty()),
                    onClick = {
                        when {
                            exporting && password.length < CredentialBackup.MIN_PASSWORD_LENGTH ->
                                error = "密码至少需要 ${CredentialBackup.MIN_PASSWORD_LENGTH} 个字符。"
                            exporting && password != confirmation -> error = "两次输入的密码不一致。"
                            exporting && vm.locks.isEmpty() && !includeCampusSession ->
                                error = "本机没有门锁凭证。勾选登录态后可仅导出登录态。"
                            else -> {
                                busy = true
                                error = null
                                scope.launch {
                                    try {
                                        if (exporting) {
                                            vm.prepareCredentialBackup(password, includeCampusSession)
                                            try {
                                                val filename = if (includeCampusSession) "TMallKey-backup.tmkey"
                                                else "TMallKey-locks.tmkey"
                                                saveDocument.launch(filename)
                                                showExport = false
                                            } catch (_: android.content.ActivityNotFoundException) {
                                                vm.saveCredentialBackup(null)
                                                error = "无法打开系统文件选择器。"
                                            }
                                        } else if (document != null) {
                                            val result = vm.restoreCredentialBackup(document, password)
                                            val lockStatus = "已导入 ${result.added} 把门锁，跳过 ${result.skipped} 条重复凭证。"
                                            if (result.session == SessionRestore.FAILED) {
                                                error = lockStatus + "天猫校园登录态保存失败，可以重试。"
                                            } else {
                                                source = null
                                                status = if (result.session == SessionRestore.RESTORED) {
                                                    val sessionStatus = "已导入天猫校园登录态。"
                                                    if (result.added + result.skipped == 0) sessionStatus else lockStatus + sessionStatus
                                                } else lockStatus
                                                onRestored()
                                            }
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        error = when (e) {
                                            is WaterSession.NotLoggedIn -> "本机没有天猫校园登录态。可以取消勾选后仅导出门锁凭证。"
                                            is CredentialBackup.AuthenticationFailed -> "密码错误或备份文件已损坏。"
                                            is CredentialBackup.UnsupportedVersion -> "此备份版本暂不支持，请更新应用。"
                                            is CredentialBackup.InvalidFile -> if (exporting) "备份数据超过 1 MB，无法导出。"
                                                else "文件格式不正确、文件不完整或超过 1 MB。"
                                            is CredentialBackup.InvalidCredentials -> "凭证数据不完整或格式无效。"
                                            else -> if (exporting) "无法生成备份，请重试。" else "无法导入备份，请重试。"
                                        }
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        }
                    },
                ) { Text(if (exporting) "选择保存位置" else "导入") }
            },
            dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text("取消") } },
        )
    }
}

@Composable
private fun BackupPasswordField(value: String, label: String, enabled: Boolean, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        enabled = enabled,
    )
}
