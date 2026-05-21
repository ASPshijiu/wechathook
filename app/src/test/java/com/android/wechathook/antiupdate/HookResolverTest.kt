package com.android.wechathook.antiupdate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

        val result = resolver.resolve(
            fingerprint = fingerprint,
            definition = HookDefinition(hookId, minimumScore = 70),
        )

        assertEquals(HookResolveResult.CacheHit(cachedTarget), result)
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

        val result = resolver.resolve(
            fingerprint = fingerprint,
            definition = HookDefinition(hookId, minimumScore = 70),
        )

        assertEquals(HookResolveResult.Scanned(scannedTarget, score = 80), result)
        assertEquals(scannedTarget, cache.get(fingerprint, hookId)?.target)
    }

    @Test
    fun resolverReturnsNoCandidatesFailure() {
        val resolver = HookResolver(
            cache = MemoryHookCache(),
            targetValidator = { true },
            candidateProvider = { emptyList() },
        )

        val result = resolver.resolve(
            fingerprint = fingerprint,
            definition = HookDefinition(HookTargetId("send_message"), minimumScore = 70),
        )

        assertEquals(HookResolveResult.Failed(HookResolveFailure.NoCandidates), result)
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

        val result = resolver.resolve(
            fingerprint = fingerprint,
            definition = HookDefinition(HookTargetId("send_message"), minimumScore = 70),
        )

        assertEquals(
            HookResolveResult.Failed(HookResolveFailure.ScoreBelowThreshold(bestScore = 20, minimumScore = 70)),
            result,
        )
    }
}
