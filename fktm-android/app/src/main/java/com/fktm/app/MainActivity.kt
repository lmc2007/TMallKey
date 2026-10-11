package com.fktm.app

import android.Manifest
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
import com.fktm.app.util.NotificationPermission
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

    /**
     * 通知权限申请回调。
     *
     * 结果这里不做处理：授权与否都不该挡住开锁主流程。
     * 拒绝之后每次进 App 会自动再申请一次（系统只在首次/未被永久拒绝时弹框），
     * 用户之后也可以在「设置 → 通知」里随时打开。
     */
    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FktmTheme {
                MainScreen(
                    vm = vm,
                    onRequestPermissions = { ensurePermissions() },
                    onEnsureNotificationPermission = { ensureNotificationPermission() },
                )
            }
        }

        // 常驻后台（抓取用的前台服务）依赖通知权限，Android 13+ 没有它会弹框要一次。
        ensureNotificationPermission()

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

    /**
     * 没有通知权限就自动申请。
     *
     * 为什么挂在常驻后台这条线上：前台服务（抓取期间保命用）在 Android 13+ 必须有
     * 通知权限才能把常驻通知显示出来。没有它，进程虽然照样被顶到前台优先级、
     * 但用户看不到任何进度提示 —— 体验上是「点了没反应」，所以缺了就主动要。
     */
    private fun ensureNotificationPermission() {
        if (NotificationPermission.isGranted(this)) return
        val perm = NotificationPermission.permission
        if (perm.isNotEmpty()) notifPermLauncher.launch(perm)
    }

    private fun hasBluetoothPermissions(): Boolean = BleLockClient(this).hasPermissions()

    @Suppress("unused")
    private fun apiLevel(): Int = Build.VERSION.SDK_INT
}
