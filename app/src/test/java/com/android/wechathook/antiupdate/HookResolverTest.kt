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
}
