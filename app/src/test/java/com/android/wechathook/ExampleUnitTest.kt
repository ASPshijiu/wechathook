package com.android.wechathook

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun startupDiagnosticContainsOnlyTechnicalState() {
        val message = buildStartupDiagnosticMessage(
            packageName = "com.tencent.mm",
            processName = "com.tencent.mm",
            isMainProcess = true,
            isFirstPackage = true,
            hookCount = 0,
        )

        assertTrue(message.contains("packageName=com.tencent.mm"))
        assertTrue(message.contains("processName=com.tencent.mm"))
        assertTrue(message.contains("mainProcess=true"))
        assertTrue(message.contains("isFirstPackage=true"))
        assertTrue(message.contains("hookCount=0"))
        assertTrue(message.contains("antiUpdateEnabled=false"))
        assertFalse(message.contains("聊天"))
        assertFalse(message.contains("联系人"))
        assertFalse(message.contains("消息正文"))
    }

    @Test
    fun startupDiagnosticShowsAntiUpdateEnabledWhenHooksExist() {
        val message = buildStartupDiagnosticMessage(
            packageName = "com.tencent.mm",
            processName = "com.tencent.mm:push",
            isMainProcess = false,
            isFirstPackage = false,
            hookCount = 1,
        )

        assertTrue(message.contains("processName=com.tencent.mm:push"))
        assertTrue(message.contains("mainProcess=false"))
        assertTrue(message.contains("isFirstPackage=false"))
        assertTrue(message.contains("hookCount=1"))
        assertTrue(message.contains("antiUpdateEnabled=true"))
    }

    @Test
    fun firstHookDiagnosticContainsOnlyTechnicalState() {
        val message = buildFirstHookDiagnosticMessage(
            target = "android.app.Application#onCreate",
            packageName = "com.tencent.mm",
            triggered = true,
        )

        assertTrue(message.contains("target=android.app.Application#onCreate"))
        assertTrue(message.contains("packageName=com.tencent.mm"))
        assertTrue(message.contains("triggered=true"))
        assertFalse(message.contains("聊天"))
        assertFalse(message.contains("联系人"))
        assertFalse(message.contains("消息正文"))
    }

    @Test
    fun weChatApplicationHookDiagnosticContainsOnlyTechnicalState() {
        val message = buildWeChatApplicationHookDiagnosticMessage(
            target = "com.tencent.mm.app.Application#attachBaseContext(android.content.Context)",
            packageName = "com.tencent.mm",
            triggered = true,
        )

        assertTrue(message.contains("target=com.tencent.mm.app.Application#attachBaseContext(android.content.Context)"))
        assertTrue(message.contains("packageName=com.tencent.mm"))
        assertTrue(message.contains("triggered=true"))
        assertFalse(message.contains("聊天"))
        assertFalse(message.contains("联系人"))
        assertFalse(message.contains("消息正文"))
    }

    @Test
    fun localDiagnosticReportContainsOnlyTechnicalInstructions() {
        val report = buildLocalDiagnosticReport()

        assertTrue(report.contains("WeChat Hook 诊断"))
        assertTrue(report.contains("【模块】"))
        assertTrue(report.contains("模块包名：com.android.wechathook"))
        assertTrue(report.contains("【目标】"))
        assertTrue(report.contains("目标包名：com.tencent.mm"))
        assertTrue(report.contains("打开来源：模块内部"))
        assertTrue(report.contains("预期主进程：com.tencent.mm"))
        assertTrue(report.contains("【Hook】"))
        assertTrue(report.contains("微信内部 Hook：com.tencent.mm.app.Application#attachBaseContext(android.content.Context)"))
        assertTrue(report.contains("设置入口：MainSettingsUI 顶部菜单"))
        assertTrue(report.contains("【功能状态】"))
        assertTrue(report.contains("模块诊断：已启用"))
        assertTrue(report.contains("微信设置入口：已启用"))
        assertTrue(report.contains("抗更新定位：未启用（框架已就绪，暂无启用规则）"))
        assertTrue(report.contains("用户数据读取：未启用"))
        assertTrue(report.contains("【验证】"))
        assertFalse(report.contains("聊天"))
        assertFalse(report.contains("联系人"))
        assertFalse(report.contains("消息正文"))
    }

    @Test
    fun localDiagnosticReportShowsFeatureConfigState() {
        val report = buildLocalDiagnosticReport(
            featureConfig = ModuleFeatureConfig(
                diagnosticsEnabled = true,
                settingsEntryEnabled = false,
                antiUpdateResolutionEnabled = true,
                userDataReadEnabled = false,
            ),
        )

        assertTrue(report.contains("模块诊断：已启用"))
        assertTrue(report.contains("微信设置入口：未启用"))
        assertTrue(report.contains("抗更新定位：已启用"))
        assertTrue(report.contains("用户数据读取：未启用"))
    }

    @Test
    fun localDiagnosticReportShowsWeChatSettingsEntrySource() {
        val report = buildLocalDiagnosticReport(
            source = "wechat_settings",
            targetPackage = "com.tencent.mm",
        )

        assertTrue(report.contains("目标包名：com.tencent.mm"))
        assertTrue(report.contains("打开来源：微信设置"))
        assertTrue(report.contains("设置入口：已从微信设置打开"))
        assertFalse(report.contains("聊天"))
        assertFalse(report.contains("联系人"))
        assertFalse(report.contains("消息正文"))
    }

    @Test
    fun fingerprintUsesAndroidPackageInfoWhenAvailable() {
        val apk = File.createTempFile("wechat", ".apk")
        try {
            val lastModified = 1710002222000L
            apk.setLastModified(lastModified)
            val applicationInfo = ApplicationInfo().apply {
                sourceDir = apk.absolutePath
            }
            val packageInfo = object : PackageInfo() {
                override fun getLongVersionCode(): Long = 8000002460L
            }.apply {
                packageName = "com.tencent.mm"
                versionName = "8.0.49"
            }

            val fingerprint = buildVersionFingerprint(
                packageName = "com.tencent.mm",
                applicationInfo = applicationInfo,
                packageInfoProvider = { packageInfo },
            )

            assertEquals("com.tencent.mm", fingerprint.packageName)
            assertEquals("8.0.49", fingerprint.versionName)
            assertEquals(8000002460L, fingerprint.versionCode)
            assertEquals(apk.absolutePath, fingerprint.apkPath)
            assertEquals(lastModified, fingerprint.apkLastModified)
        } finally {
            apk.delete()
        }
    }

    @Test
    fun fingerprintFallsBackToApkPathWhenPackageInfoUnavailable() {
        val apk = File.createTempFile("wechat", ".apk")
        try {
            val lastModified = 1710003333000L
            apk.setLastModified(lastModified)
            val applicationInfo = ApplicationInfo().apply {
                sourceDir = apk.absolutePath
            }

            val fingerprint = buildVersionFingerprint(
                packageName = "com.tencent.mm",
                applicationInfo = applicationInfo,
                packageInfoProvider = { null },
            )

            assertEquals("com.tencent.mm", fingerprint.packageName)
            assertEquals("", fingerprint.versionName)
            assertEquals(0L, fingerprint.versionCode)
            assertEquals(apk.absolutePath, fingerprint.apkPath)
            assertEquals(lastModified, fingerprint.apkLastModified)
        } finally {
            apk.delete()
        }
    }

    @Test
    fun fingerprintFallsBackWhenPackageInfoProviderThrows() {
        val apk = File.createTempFile("wechat", ".apk")
        try {
            val lastModified = 1710004444000L
            apk.setLastModified(lastModified)
            val applicationInfo = ApplicationInfo().apply {
                sourceDir = apk.absolutePath
            }

            val fingerprint = buildVersionFingerprint(
                packageName = "com.tencent.mm",
                applicationInfo = applicationInfo,
                packageInfoProvider = { throw IllegalStateException("package manager unavailable") },
            )

            assertEquals("com.tencent.mm", fingerprint.packageName)
            assertEquals("", fingerprint.versionName)
            assertEquals(0L, fingerprint.versionCode)
            assertEquals(apk.absolutePath, fingerprint.apkPath)
            assertEquals(lastModified, fingerprint.apkLastModified)
        } finally {
            apk.delete()
        }
    }
}
