package com.fktm.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** 静态快捷方式会清空目标任务栈。用独立任务承接，再把开锁请求交给主界面。 */
class UnlockShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val unlock = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(intent.data)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(unlock)
        finish()
    }
}
