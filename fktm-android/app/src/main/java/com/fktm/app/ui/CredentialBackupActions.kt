package com.fktm.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fktm.app.credential.CredentialBackup
import com.fktm.app.vm.UnlockViewModel
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
                if (uri != null) status = "门锁凭证已导出"
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
                enabled = !busy && vm.locks.isNotEmpty(),
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
        var error by remember { mutableStateOf<String?>(null) }
        val dismiss = {
            if (!busy) {
                showExport = false
                source = null
            }
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(if (exporting) "导出门锁凭证" else "导入门锁备份") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (exporting) "给备份设一个至少 ${CredentialBackup.MIN_PASSWORD_LENGTH} 个字符的密码。导入时需要同一个密码。"
                        else "输入导出时设置的密码。同一把门锁会保留本机的凭证。",
                    )
                    BackupPasswordField(password, "备份密码", !busy) {
                        password = it
                        error = null
                    }
                    if (exporting) BackupPasswordField(confirmation, "确认密码", !busy) {
                        confirmation = it
                        error = null
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
                            else -> {
                                busy = true
                                error = null
                                scope.launch {
                                    try {
                                        if (exporting) {
                                            vm.prepareCredentialBackup(password)
                                            try {
                                                saveDocument.launch("TMallKey-locks.tmkey")
                                                showExport = false
                                            } catch (_: android.content.ActivityNotFoundException) {
                                                vm.saveCredentialBackup(null)
                                                error = "无法打开系统文件选择器。"
                                            }
                                        } else if (document != null) {
                                            val result = vm.restoreCredentialBackup(document, password)
                                            source = null
                                            status = "已导入 ${result.added} 把门锁，跳过 ${result.skipped} 条重复凭证。"
                                            onRestored()
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        error = when (e) {
                                            is CredentialBackup.AuthenticationFailed -> "密码错误或备份文件已损坏。"
                                            is CredentialBackup.UnsupportedVersion -> "此备份版本暂不支持，请更新应用。"
                                            is CredentialBackup.InvalidFile -> if (exporting) "凭证太多，无法生成备份。"
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
