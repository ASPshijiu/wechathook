package com.android.wechathook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatSettingsEntryTest {
    @Test
    fun moduleSettingsPreferenceKeyMatchesOnlyInjectedEntry() {
        assertTrue(isModuleSettingsPreferenceKey("wechathook_diagnostics_entry"))
        assertFalse(isModuleSettingsPreferenceKey("settings_about_micromsg"))
        assertFalse(isModuleSettingsPreferenceKey(null))
    }

    @Test
    fun diagnosticsIntentTargetsModuleActivityFromWeChatSettings() {
        val spec = buildModuleDiagnosticsIntentSpec()

        assertEquals("com.android.wechathook", spec.packageName)
        assertEquals("com.android.wechathook.MainActivity", spec.className)
        assertEquals("wechat_settings", spec.source)
        assertEquals("com.tencent.mm", spec.targetPackage)
    }

    @Test
    fun modernSettingsClassMatchesOnlyNewMainSettingsPage() {
        assertTrue(isModernWeChatSettingsClass("com.tencent.mm.plugin.setting.ui.setting_new.MainSettingsUI"))
        assertFalse(isModernWeChatSettingsClass("com.tencent.mm.plugin.setting.ui.setting.SettingsUI"))
        assertFalse(isModernWeChatSettingsClass(null))
    }

    @Test
    fun weChatDiagnosticsDialogReportUsesChineseEntryState() {
        val report = buildWeChatDiagnosticsDialogReport()

        assertTrue(report.contains("WeChat Hook 诊断"))
        assertTrue(report.contains("打开来源：微信设置"))
        assertTrue(report.contains("设置入口：已从微信设置打开"))
    }

    @Test
    fun settingsEntryTitleContainsNoSensitiveContent() {
        assertFalse(WECHAT_SETTINGS_ENTRY_TITLE.contains("聊天"))
        assertFalse(WECHAT_SETTINGS_ENTRY_TITLE.contains("联系人"))
        assertFalse(WECHAT_SETTINGS_ENTRY_TITLE.contains("消息正文"))
    }
}
