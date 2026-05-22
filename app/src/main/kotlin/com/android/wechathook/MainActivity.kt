package com.android.wechathook

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val report = buildLocalDiagnosticReport(
            source = intent.getStringExtra("source").orEmpty(),
            targetPackage = intent.getStringExtra("targetPackage").orEmpty(),
        )
        val content = TextView(this).apply {
            text = report
            textSize = 14f
            setTextIsSelectable(true)
            setPadding(32, 96, 32, 32)
        }
        val copyButton = Button(this).apply {
            text = getString(R.string.copy_diagnostics)
            setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.diagnostics_clip_label), report))
                Toast.makeText(this@MainActivity, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show()
            }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(copyButton)
            addView(
                ScrollView(this@MainActivity).apply {
                    addView(content)
                },
            )
        }

        setContentView(layout)
    }
}

internal fun buildLocalDiagnosticReport(
    source: String = "",
    targetPackage: String = "",
): String {
    val normalizedSource = source.ifEmpty { "launcher" }
    val normalizedTargetPackage = targetPackage.ifEmpty { "com.tencent.mm" }
    return buildString {
        appendLine("WeChat Hook 诊断")
        appendLine()
        appendLine("【模块】")
        appendLine("模块包名：com.android.wechathook")
        appendLine("模块版本：1.0")
        appendLine("打开来源：${buildEntrySourceLabel(normalizedSource)}")
        appendLine()
        appendLine("【目标】")
        appendLine("目标包名：$normalizedTargetPackage")
        appendLine("作用域：com.tencent.mm")
        appendLine("预期主进程：com.tencent.mm")
        appendLine()
        appendLine("【Hook】")
        appendLine("启动 Hook：android.app.Application#onCreate")
        appendLine("微信内部 Hook：com.tencent.mm.app.Application#attachBaseContext(android.content.Context)")
        appendLine("设置入口：MainSettingsUI 顶部菜单")
        appendLine()
        appendLine("【功能状态】")
        appendLine("模块诊断：已启用")
        appendLine("微信设置入口：已启用")
        appendLine("抗更新定位：框架已就绪，暂无启用规则")
        appendLine("用户数据读取：未启用")
        appendLine()
        appendLine("【验证】")
        appendLine("模块启用：请在 LSPosed 确认作用域包含微信")
        appendLine("运行状态：请在 LSPosed 日志查看 ModuleDiagnostics")
        appendLine("设置入口：${buildSettingsEntryStatus(normalizedSource)}")
    }.trimEnd()
}

private fun buildEntrySourceLabel(source: String): String {
    return if (source == "wechat_settings") {
        "微信设置"
    } else {
        "模块内部"
    }
}

private fun buildSettingsEntryStatus(source: String): String {
    return if (source == "wechat_settings") {
        "已从微信设置打开"
    } else {
        "请从微信「我 → 设置 → WeChat Hook 诊断」打开"
    }
}
