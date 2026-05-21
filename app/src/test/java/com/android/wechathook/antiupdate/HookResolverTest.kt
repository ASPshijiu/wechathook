package com.android.wechathook.antiupdate

import com.android.wechathook.shouldRunAntiUpdateResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HookResolverTest {
    private val fingerprint = VersionFingerprint(
        packageName = "com.tencent.mm",
        versionName = "8.0.49",
        versionCode = 2460L,
        apkPath = "/data/app/com.tencent.mm/base.apk",
        apkLastModified = 1710000000000L,
    )

    @Test
    fun memoryCacheStoresEntriesByFingerprintAndHookId() {
        val cache = MemoryHookCache()
        val hookId = HookTargetId("send_message")
        val target = ResolvedHookTarget.Method(
            className = "com.tencent.mm.SendMessage",
            methodName = "a",
            parameterTypeNames = listOf("java.lang.String"),
            returnTypeName = "void",
        )

        cache.put(
            fingerprint = fingerprint,
            hookId = hookId,
            entry = HookCacheEntry(target = target, confidence = 90, frameworkVersion = 1),
        )

        assertEquals(target, cache.get(fingerprint, hookId)?.target)
        assertNull(cache.get(fingerprint.copy(versionCode = 2461L), hookId))
    }

    @Test
    fun resolverReturnsValidCachedTargetWithoutScanning() {
        var providerCalled = false
        val cache = MemoryHookCache()
        val hookId = HookTargetId("send_message")
        val cachedTarget = ResolvedHookTarget.Method(
            className = "com.tencent.mm.Cached",
            methodName = "a",
            parameterTypeNames = emptyList(),
            returnTypeName = "void",
        )
        cache.put(
            fingerprint = fingerprint,
            hookId = hookId,
            entry = HookCacheEntry(cachedTarget, confidence = 95, frameworkVersion = 1),
        )
        val resolver = HookResolver(
            cache = cache,
            targetValidator = { true },
            candidateProvider = {
                providerCalled = true
                emptyList()
            },
        )

        val report = resolver.resolveWithReport(
            fingerprint = fingerprint,
            definition = HookDefinition(hookId, minimumScore = 70),
        )

        assertEquals(HookResolveResult.CacheHit(cachedTarget), report.result)
        assertEquals(true, report.cacheHit)
        assertEquals(0, report.candidates.size)
        assertEquals(false, providerCalled)
    }

    @Test
    fun resolverScansWhenCachedTargetFailsValidation() {
        val cache = MemoryHookCache()
        val hookId = HookTargetId("send_message")
        cache.put(
            fingerprint = fingerprint,
            hookId = hookId,
            entry = HookCacheEntry(
                target = ResolvedHookTarget.Method(
                    className = "com.tencent.mm.Stale",
                    methodName = "a",
                    parameterTypeNames = emptyList(),
                    returnTypeName = "void",
                ),
                confidence = 90,
                frameworkVersion = 1,
            ),
        )
        val scannedTarget = ResolvedHookTarget.Method(
            className = "com.tencent.mm.Fresh",
            methodName = "b",
            parameterTypeNames = emptyList(),
            returnTypeName = "void",
        )
        val resolver = HookResolver(
            cache = cache,
            targetValidator = { false },
            candidateProvider = {
                listOf(
                    HookCandidate(
                        target = scannedTarget,
                        features = listOf(HookFeatureScore("class_shape", 80, true)),
                    ),
                )
            },
        )

        val report = resolver.resolveWithReport(
            fingerprint = fingerprint,
            definition = HookDefinition(hookId, minimumScore = 70),
        )

        assertEquals(HookResolveResult.Scanned(scannedTarget, score = 80), report.result)
        assertEquals(false, report.cacheHit)
        assertEquals(1, report.candidates.size)
        assertEquals(scannedTarget, cache.get(fingerprint, hookId)?.target)
    }

    @Test
    fun resolverReturnsNoCandidatesFailure() {
        val resolver = HookResolver(
            cache = MemoryHookCache(),
            targetValidator = { true },
            candidateProvider = { emptyList() },
        )

        val report = resolver.resolveWithReport(
            fingerprint = fingerprint,
            definition = HookDefinition(HookTargetId("send_message"), minimumScore = 70),
        )

        assertEquals(HookResolveResult.Failed(HookResolveFailure.NoCandidates), report.result)
        assertEquals(false, report.cacheHit)
        assertEquals(0, report.candidates.size)
    }

    @Test
    fun resolverReturnsBelowThresholdFailure() {
        val resolver = HookResolver(
            cache = MemoryHookCache(),
            targetValidator = { true },
            candidateProvider = {
                listOf(
                    HookCandidate(
                        target = ResolvedHookTarget.Method(
                            className = "com.tencent.mm.LowScore",
                            methodName = "a",
                            parameterTypeNames = emptyList(),
                            returnTypeName = "void",
                        ),
                        features = listOf(HookFeatureScore("weak_feature", 20, true)),
                    ),
                )
            },
        )

        val report = resolver.resolveWithReport(
            fingerprint = fingerprint,
            definition = HookDefinition(HookTargetId("send_message"), minimumScore = 70),
        )

        assertEquals(
            HookResolveResult.Failed(HookResolveFailure.ScoreBelowThreshold(bestScore = 20, minimumScore = 70)),
            report.result,
        )
        assertEquals(false, report.cacheHit)
        assertEquals(1, report.candidates.size)
    }

    @Test
    fun resolverReportSortsCandidatesByScoreDescending() {
        val highTarget = ResolvedHookTarget.Method(
            className = "com.tencent.mm.HighScore",
            methodName = "a",
            parameterTypeNames = emptyList(),
            returnTypeName = "void",
        )
        val lowTarget = ResolvedHookTarget.Method(
            className = "com.tencent.mm.LowScore",
            methodName = "b",
            parameterTypeNames = emptyList(),
            returnTypeName = "void",
        )
        val resolver = HookResolver(
            cache = MemoryHookCache(),
            targetValidator = { true },
            candidateProvider = {
                listOf(
                    HookCandidate(
                        target = lowTarget,
                        features = listOf(HookFeatureScore("weak_feature", 20, true)),
                    ),
                    HookCandidate(
                        target = highTarget,
                        features = listOf(HookFeatureScore("strong_feature", 90, true)),
                    ),
                )
            },
        )

        val report = resolver.resolveWithReport(
            fingerprint = fingerprint,
            definition = HookDefinition(HookTargetId("send_message"), minimumScore = 70),
        )

        assertEquals(HookResolveResult.Scanned(highTarget, score = 90), report.result)
        assertEquals(listOf(90, 20), report.candidates.map(HookCandidate::score))
    }

    @Test
    fun resolverConvertsProviderExceptionToFailure() {
        val resolver = HookResolver(
            cache = MemoryHookCache(),
            targetValidator = { true },
            candidateProvider = { throw IllegalStateException("scan failed") },
        )

        val report = resolver.resolveWithReport(
            fingerprint = fingerprint,
            definition = HookDefinition(HookTargetId("send_message"), minimumScore = 70),
        )

        assertEquals(
            HookResolveResult.Failed(HookResolveFailure.ExceptionThrown("IllegalStateException")),
            report.result,
        )
        assertEquals(false, report.cacheHit)
        assertEquals(0, report.candidates.size)
    }

    @Test
    fun resolverConvertsCacheWriteExceptionToFailureWithCandidates() {
        val scannedTarget = ResolvedHookTarget.Method(
            className = "com.tencent.mm.Fresh",
            methodName = "b",
            parameterTypeNames = emptyList(),
            returnTypeName = "void",
        )
        val cache = object : HookCache {
            override fun get(fingerprint: VersionFingerprint, hookId: HookTargetId): HookCacheEntry? = null

            override fun put(fingerprint: VersionFingerprint, hookId: HookTargetId, entry: HookCacheEntry) {
                throw IllegalStateException("cache unavailable")
            }
        }
        val resolver = HookResolver(
            cache = cache,
            targetValidator = { true },
            candidateProvider = {
                listOf(
                    HookCandidate(
                        target = scannedTarget,
                        features = listOf(HookFeatureScore("class_shape", 80, true)),
                    ),
                )
            },
        )

        val report = resolver.resolveWithReport(
            fingerprint = fingerprint,
            definition = HookDefinition(HookTargetId("send_message"), minimumScore = 70),
        )

        assertEquals(
            HookResolveResult.Failed(HookResolveFailure.ExceptionThrown("IllegalStateException")),
            report.result,
        )
        assertEquals(1, report.candidates.size)
    }

    @Test
    fun emptyFirstStageDefinitionsDoNotFailModuleStartup() {
        assertFalse(shouldRunAntiUpdateResolution(emptyList()))
    }

    @Test
    fun nonEmptyFirstStageDefinitionsRunModuleStartupResolution() {
        assertTrue(shouldRunAntiUpdateResolution(listOf(HookDefinition(HookTargetId("send_message"), minimumScore = 70))))
    }
}
