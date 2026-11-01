package com.fktm.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.fktm.app.update.UpdateCheckResult
import com.fktm.app.ui.UpdatePromptDialog
import com.fktm.app.ui.AboutScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.fktm.app.ble.BleLockClient
import com.fktm.app.ui.MainScreen
import com.fktm.app.ui.WaterScreen
import com.fktm.app.ui.theme.FktmTheme
import com.fktm.app.util.NotificationPermission
import com.fktm.app.vm.UnlockViewModel
import com.fktm.app.vm.WaterViewModel

/**
 * 主界面。也是「一键开锁」的落点：
 * 桌面快捷方式 / QS 磁贴 都会带 `fktm://unlock` 拉起本 Activity，进而自动开锁。
 */
class MainActivity : ComponentActivity() {

    private val vm: UnlockViewModel by viewModels()
    private val waterVm: WaterViewModel by viewModels()
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

        val app = application as FktmApp
        val repo = app.updateRepository

        setContent {
            FktmTheme {
                // 底部导航：门锁 / 饮水 / 关于。功能各自独立成一栏。
                var tab by rememberSaveable { mutableIntStateOf(0) }
                var release by remember { mutableStateOf<com.fktm.app.update.UpdateRelease?>(null) }
                var promptDismissed by rememberSaveable { mutableStateOf(false) }
                val downloadState by repo.downloadState.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()

                // 每次启动都查一次更新（不看待机阈值）：有新版就弹窗，
                // 用户点「稍后」本次进程内不再打扰。失败静默 —— 检查更新不该挡住开锁。
                // 放在 Activity 这一层而不是「关于」页，是为了不进关于页也能收到提示。
                LaunchedEffect(Unit) {
                    val r = withContext(Dispatchers.IO) {
                        runCatching { repo.checkForUpdate(force = true) }.getOrNull()
                    }
                    if (r is UpdateCheckResult.UpdateAvailable) release = r.release
                }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == 0,
                                onClick = { tab = 0 },
                                icon = { Text("🔒", fontSize = 18.sp) },
                                label = { Text("门锁") },
                            )
                            NavigationBarItem(
                                selected = tab == 1,
                                onClick = { tab = 1 },
                                icon = { Text("💧", fontSize = 18.sp) },
                                label = { Text("饮水") },
                            )
                            NavigationBarItem(
                                selected = tab == 2,
                                onClick = { tab = 2 },
                                icon = { Text("ℹ️", fontSize = 18.sp) },
                                label = { Text("关于") },
                            )
                        }
                    },
                ) { inner ->
                    Box(Modifier.padding(inner)) {
                        when (tab) {
                            0 -> MainScreen(
                                vm = vm,
                                onRequestPermissions = { ensurePermissions() },
                                onEnsureNotificationPermission = { ensureNotificationPermission() },
                                onOpenAbout = { tab = 2 },
                            )
                            1 -> WaterScreen(vm = waterVm)
                            else -> AboutScreen(repo = repo, settings = app.updateSettings)
                        }
                    }
                }

                val pending = release
                if (pending != null && !promptDismissed) {
                    UpdatePromptDialog(
                        release = pending,
                        downloadState = downloadState,
                        onDismiss = { promptDismissed = true },
                        onUpdate = { scope.launch { repo.downloadAndInstall(pending) } },
                        onCancelDownload = { repo.cancelDownload() },
                    )
                }
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
        // 有多把锁时优先 VOC（离线、秒开）；只有 MV 就开 MV（在线取帧）
        val all = (application as FktmApp).credentialStore.load()
        val cred = all.firstOrNull { !it.isMv } ?: all.firstOrNull() ?: return
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
