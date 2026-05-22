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
            setPadding(32, 24, 32, 32)
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
        appendLine("WeChat Hook Diagnostics")
        appendLine()
        appendLine("[Module]")
        appendLine("modulePackage=com.android.wechathook")
        appendLine("moduleVersion=1.0")
        appendLine("entrySource=$normalizedSource")
        appendLine()
        appendLine("[Target]")
        appendLine("targetPackage=$normalizedTargetPackage")
        appendLine("scope=com.tencent.mm")
        appendLine("expectedMainProcess=com.tencent.mm")
        appendLine()
        appendLine("[Hooks]")
        appendLine("startupHook=android.app.Application#onCreate")
        appendLine("internalHook=com.tencent.mm.app.Application#attachBaseContext(android.content.Context)")
        appendLine("settingsEntry=MainSettingsUI top option menu")
        appendLine()
        appendLine("[Verification]")
        appendLine("moduleEnabled=Check LSPosed scope for com.tencent.mm")
        appendLine("runtimeStatus=Check LSPosed logs for ModuleDiagnostics")
        appendLine("settingsEntryStatus=${buildSettingsEntryStatus(normalizedSource)}")
    }.trimEnd()
}

private fun buildSettingsEntryStatus(source: String): String {
    return if (source == "wechat_settings") {
        "openedFromWeChatSettings"
    } else {
        "open WeChat > Me > Settings > WeChat Hook Diagnostics"
    }
}
