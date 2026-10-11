package com.fktm.app

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * 下拉快捷磁贴：点一下直接开锁。
 *
 * 磁贴本身不跑 BLE（生命周期太短），而是把 MainActivity 以 `fktm://unlock` 拉起，
 * 由 MainActivity 侧走完整的连接 + 开锁流程（也顺带授权/报错可见）。
 */
class QuickUnlockTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { t ->
            t.state = Tile.STATE_INACTIVE
            t.label = "一键开锁"
            t.icon = Icon.createWithResource(this, R.drawable.ic_tile_unlock)
            t.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("fktm://unlock")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
