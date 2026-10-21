package com.fktm.tmxydump

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * 模块本身没有界面逻辑，这个 Activity 只用来在 LSPatch 里方便识别/点开看说明。
 */
class ModuleInfoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            text = """
                tmxy 登录态导出（LSPatch 模块）

                作用：在天猫校园进程里读出它自己的 cookie，
                通过广播交给「快开门」(com.fktm.app)。

                用法：
                1. 用 LSPatch 把这个模块打包进天猫校园（本地模式）
                2. 打开天猫校园并保持已登录
                3. 回到快开门 → 饮水 → 会自动提示「已收到登录态」

                注意：重打包会改变天猫校园签名，可能导致它无法登录。
            """.trimIndent()
            textSize = 14f
            setPadding(48, 96, 48, 48)
        }
        setContentView(tv)
    }
}
