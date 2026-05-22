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

        val report = buildLocalDiagnosticReport()
        val content = TextView(this).apply {
            text = report
            textSize = 14f
            setTextIsSelectable(true)
            setPadding(32, 32, 32, 32)
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

internal fun buildLocalDiagnosticReport(): String {
    return buildString {
        appendLine("WeChat Hook Diagnostics")
        appendLine("modulePackage=com.android.wechathook")
        appendLine("moduleVersion=1.0")
        appendLine("targetPackage=com.tencent.mm")
        appendLine("scope=com.tencent.mm")
        appendLine("status=Open WeChat and check LSPosed logs for ModuleDiagnostics")
        appendLine("expectedMainProcess=com.tencent.mm")
        appendLine("expectedInternalHook=com.tencent.mm.app.Application#attachBaseContext(android.content.Context)")
    }.trimEnd()
}
