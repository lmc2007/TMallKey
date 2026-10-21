package com.fktm.app.water

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 饮水模块的诊断落盘（**只写名字和状态，绝不写 cookie 值**）。
 *
 * 为什么需要它：`GET_CAMPUS_USER_ERROR` 这类错要判断到底是「账号不对」
 * 还是「服务端抽风」，光看界面上一行字不够，得知道当时手里有哪些 cookie、
 * 账号 ID 是什么、服务端 ret 原文是什么。
 *
 * 文件名 `water_diag.txt`，写满 16KB 就轮换，只保留最近一轮，避免越积越大。
 */
object WaterDiag {

    private const val FILE = "water_diag.txt"
    private const val MAX_BYTES = 16 * 1024

    fun file(context: Context): File = File(context.filesDir, FILE)

    @Synchronized
    fun log(context: Context, event: String, detail: String = "") {
        val f = file(context)
        runCatching {
            if (f.length() > MAX_BYTES) f.delete()
            val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            f.appendText("$ts $event${if (detail.isBlank()) "" else " | $detail"}\n")
        }
    }

    /** cookie 只描述「有哪些名字、几个」，值一律不落盘。 */
    fun cookieSummary(jar: Map<String, String>): String {
        val names = jar.keys.sorted()
        val account = WaterSession.accountId(jar)
        return "account=${WaterSession.maskAccount(account)}(len=${account.length}) " +
            "count=${jar.size} names=$names"
    }
}
