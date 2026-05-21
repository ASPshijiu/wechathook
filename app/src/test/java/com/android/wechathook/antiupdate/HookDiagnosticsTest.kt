package com.android.wechathook.antiupdate

import org.junit.Assert.assertEquals
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

    @Test
    fun diagnosticEventUsesReportStatisticsAndTopCandidates() {
        val fingerprint = VersionFingerprint(
            packageName = "com.tencent.mm",
            versionName = "8.0.49",
            versionCode = 2460L,
            apkPath = "/data/app/com.tencent.mm/base.apk",
            apkLastModified = 1710000000000L,
        )
        val candidates = listOf(
            HookCandidate(
                target = ResolvedHookTarget.Method(
                    className = "com.tencent.mm.HighScore",
                    methodName = "a",
                    parameterTypeNames = emptyList(),
                    returnTypeName = "void",
                ),
                features = listOf(HookFeatureScore("strong_feature", 90, true)),
            ),
            HookCandidate(
                target = ResolvedHookTarget.Constructor(
                    className = "com.tencent.mm.MiddleScore",
                    parameterTypeNames = emptyList(),
                ),
                features = listOf(HookFeatureScore("constructor_shape", 80, true)),
            ),
            HookCandidate(
                target = ResolvedHookTarget.Method(
                    className = "com.tencent.mm.LowScore",
                    methodName = "b",
                    parameterTypeNames = emptyList(),
                    returnTypeName = "void",
                ),
                features = listOf(HookFeatureScore("weak_feature", 20, true)),
            ),
            HookCandidate(
                target = ResolvedHookTarget.Method(
                    className = "com.tencent.mm.Hidden",
                    methodName = "c",
                    parameterTypeNames = emptyList(),
                    returnTypeName = "void",
                ),
                features = listOf(HookFeatureScore("hidden_feature", 10, true)),
            ),
        )
        val report = HookResolveReport(
            result = HookResolveResult.Failed(HookResolveFailure.ExceptionThrown("IllegalStateException")),
            cacheHit = false,
            candidates = candidates,
        )

        val event = HookDiagnosticEvent.fromReport(
            fingerprint = fingerprint,
            hookId = HookTargetId("send_message"),
            report = report,
        )

        assertEquals(4, event.candidateCount)
        assertEquals(3, event.topCandidates.size)
        assertEquals("com.tencent.mm.HighScore", event.topCandidates[0].className)
        assertEquals("a", event.topCandidates[0].memberName)
        assertEquals("<init>", event.topCandidates[1].memberName)
        assertEquals(HookResolveFailure.ExceptionThrown("IllegalStateException"), event.failure)
    }

    @Test
    fun exceptionFailureLogContainsOnlyExceptionType() {
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
            candidateCount = 0,
            topCandidates = emptyList(),
            failure = HookResolveFailure.ExceptionThrown("IllegalStateException"),
        )

        val message = event.toLogMessage()

        assertTrue(message.contains("failure=IllegalStateException"))
        assertFalse(message.contains("ExceptionThrown"))
        assertFalse(message.contains("package manager unavailable"))
    }
}
