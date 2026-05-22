package com.android.wechathook.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleDiagnosticsTest {
    @Test
    fun reportContainsTechnicalFieldsWithoutSensitiveContent() {
        val report = ModuleDiagnostics(
            moduleName = "WeChat Hook",
            moduleVersion = "debug",
            packageName = "com.tencent.mm",
            processName = "com.tencent.mm",
            isMainProcess = true,
            wechatVersionName = "8.0.71",
            wechatVersionCode = 3080L,
            apkPath = "/data/app/com.tencent.mm/base.apk",
            apkLastModified = 1710000000000L,
            fingerprintKey = "com.tencent.mm|8.0.71|3080|/data/app/com.tencent.mm/base.apk|1710000000000",
            startupHookInstalled = true,
            wechatApplicationHookTarget = "com.tencent.mm.app.Application#attachBaseContext(android.content.Context)",
            wechatApplicationHookStatus = HookStatus.INSTALLED,
        ).toReport()

        assertTrue(report.contains("moduleName=WeChat Hook"))
        assertTrue(report.contains("moduleVersion=debug"))
        assertTrue(report.contains("packageName=com.tencent.mm"))
        assertTrue(report.contains("processName=com.tencent.mm"))
        assertTrue(report.contains("mainProcess=true"))
        assertTrue(report.contains("wechatVersionName=8.0.71"))
        assertTrue(report.contains("wechatVersionCode=3080"))
        assertTrue(report.contains("apkPath=/data/app/com.tencent.mm/base.apk"))
        assertTrue(report.contains("apkLastModified=1710000000000"))
        assertTrue(report.contains("fingerprintKey=com.tencent.mm|8.0.71|3080|/data/app/com.tencent.mm/base.apk|1710000000000"))
        assertTrue(report.contains("startupHookInstalled=true"))
        assertTrue(report.contains("wechatApplicationHookTarget=com.tencent.mm.app.Application#attachBaseContext(android.content.Context)"))
        assertTrue(report.contains("wechatApplicationHookStatus=INSTALLED"))
        assertFalse(report.contains("聊天"))
        assertFalse(report.contains("联系人"))
        assertFalse(report.contains("消息正文"))
    }
}
