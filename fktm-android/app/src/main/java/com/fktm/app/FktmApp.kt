package com.fktm.app

import android.app.Application
import com.fktm.app.ble.BleLockClient
import com.fktm.app.credential.CredentialStore
import com.fktm.app.update.ApkInstaller
import com.fktm.app.update.AppUpdateRepository
import com.fktm.app.update.UpdateSettings

class FktmApp : Application() {

    val credentialStore: CredentialStore by lazy { CredentialStore(this) }

    /**
     * 进程级共享 BLE 客户端（提速关键）。
     *
     * 主界面与快捷磁贴共用**同一条** GATT 连接 —— 第二次开锁可直接复用，
     * 跳过「连接 + 服务发现」（实测省 1~3s）。连接由客户端自己按空闲超时回收。
     */
    val bleClient: BleLockClient by lazy { BleLockClient(this) }

    /** 更新相关的偏好设置（启动阈值 / 忽略版本 / 首选镜像 / 下载方式）。 */
    val updateSettings: UpdateSettings by lazy { UpdateSettings(this) }

    /** 更新检查与下载安装。全进程单例，保证下载状态只有一份。 */
    val updateRepository: AppUpdateRepository by lazy {
        AppUpdateRepository(this, updateSettings, ApkInstaller(this, updateSettings))
    }

    companion object {
        lateinit var instance: FktmApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
