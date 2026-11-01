package com.fktm.app.ui

import android.app.Activity
import android.os.Bundle
import com.fktm.app.water.SgEngine
import com.fktm.app.water.WaterDiag

/**
 * 逆向自检页：验证「在 fkTM 进程里跑天猫校园自带 SecurityGuard」这条路是否成立。
 *
 * 不进任何导航，只能用 adb 拉起：
 * ```
 * adb shell am start -n com.fktm.app/.ui.SgProbeActivity
 * ```
 * 结果写进 `files/water_diag.txt`（这台 ROM 屏蔽了 logcat）。
 */
class SgProbeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread {
            val api = intent.getStringExtra("api") ?: "mtop.common.gettimestamp"
            val data = intent.getStringExtra("data") ?: """{"a":1}"""
            WaterDiag.log(applicationContext, "sgProbe", "start api=$api")
            val r = SgEngine.produce(applicationContext, api, data, "34270422")
            WaterDiag.log(
                applicationContext, "sgProbe",
                "ok=${r.ok} stage=${r.stage} ${r.detail.take(600)}",
            )
            runOnUiThread { finish() }
        }.apply { name = "sg-probe" }.start()
    }
}
