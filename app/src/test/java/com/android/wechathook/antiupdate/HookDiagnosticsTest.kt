package com.android.wechathook.antiupdate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookDiagnosticsTest {
    @Test
    fun failureLogContainsTechnicalFieldsWithoutSensitiveContent() {
        val fingerprint = VersionFingerprint(
            packageName = "com.tencent.mm",
            versionName = "8.0.49",
            versionCode = 2460L,
            apkPath = "/data/app/com.tencent.mm/base.apk",
            apkLastModified = 1710000000000L,
        )
        val event = HookDiagnosticEvent(
            fingerprint = fingerprint,
            hookId = HookTargetId("send_message"),
            cacheHit = false,
            candidateCount = 2,
            topCandidates = listOf(
                HookCandidateSummary(
                    className = "com.tencent.mm.SendMessage",
                    memberName = "a",
                    score = 60,
                    matchedFeatures = listOf("parameter_count", "return_type"),
                ),
            ),
            failure = HookResolveFailure.ScoreBelowThreshold(bestScore = 60, minimumScore = 70),
        )

        val message = event.toLogMessage()

        assertTrue(message.contains("com.tencent.mm 8.0.49(2460)"))
        assertTrue(message.contains("send_message"))
        assertTrue(message.contains("candidateCount=2"))
        assertTrue(message.contains("ScoreBelowThreshold"))
        assertFalse(message.contains("聊天"))
        assertFalse(message.contains("联系人"))
        assertFalse(message.contains("消息正文"))
    }
}
