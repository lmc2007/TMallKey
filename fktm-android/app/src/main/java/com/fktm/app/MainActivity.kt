package com.fktm.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.fktm.app.ble.BleLockClient
import com.fktm.app.ui.MainScreen
import com.fktm.app.ui.theme.FktmTheme
import com.fktm.app.vm.UnlockViewModel

/**
 * 主界面。也是「一键开锁」的落点：
 * 桌面快捷方式 / QS 磁贴 都会带 `fktm://unlock` 拉起本 Activity，进而自动开锁。
 */
class MainActivity : ComponentActivity() {

    private val vm: UnlockViewModel by viewModels()
    private var pendingAutoUnlock = false

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it } && pendingAutoUnlock) {
                pendingAutoUnlock = false
                autoUnlock()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FktmTheme {
                MainScreen(
                    vm = vm,
                    onRequestPermissions = { ensurePermissions() },
                )
            }
        }

        if (isUnlockIntent(intent)) {
            requestAutoUnlock()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isUnlockIntent(intent)) requestAutoUnlock()
    }

    /**
     * 前台驻留（方案 A）：
     * 可见期间保持门锁连接，点开锁直接复用（秒开）；一离开前台立刻交还连接。
     * 息屏也会走到 onStop，所以不会出现「人不在了还占着门锁」。
     */
    override fun onStart() {
        super.onStart()
        ble().setForeground(true)
        vm.prewarm()
    }

    override fun onStop() {
        super.onStop()
        ble().setForeground(false)
    }

    private fun ble(): BleLockClient = (application as FktmApp).bleClient

    private fun isUnlockIntent(intent: Intent?): Boolean {
        val i = intent ?: return false
        return i.data?.toString()?.contains("unlock") == true
    }

    private fun requestAutoUnlock() {
        if (hasBluetoothPermissions()) {
            autoUnlock()
        } else {
            pendingAutoUnlock = true
            ensurePermissions()
        }
    }

    private fun autoUnlock() {
        val cred = (application as FktmApp).credentialStore.primary() ?: return
        vm.unlock(cred)
    }

    private fun ensurePermissions() {
        if (!hasBluetoothPermissions()) {
            permLauncher.launch(BleLockClient(this).requiredPermissions())
        }
    }

    private fun hasBluetoothPermissions(): Boolean = BleLockClient(this).hasPermissions()

    @Suppress("unused")
    private fun apiLevel(): Int = Build.VERSION.SDK_INT
}
