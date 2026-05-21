package com.android.wechathook.antiupdate

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionFingerprintTest {
    @Test
    fun cacheKeyIncludesPackageVersionCodeApkPathAndModifiedTime() {
        val fingerprint = VersionFingerprint(
            packageName = "com.tencent.mm",
            versionName = "8.0.49",
            versionCode = 2460L,
            apkPath = "/data/app/com.tencent.mm/base.apk",
            apkLastModified = 1710000000000L,
        )

        assertEquals(
            "com.tencent.mm|8.0.49|2460|/data/app/com.tencent.mm/base.apk|1710000000000",
            fingerprint.cacheKey,
        )
    }

    @Test
    fun displayNameIsCompactAndReadableForLogs() {
        val fingerprint = VersionFingerprint(
            packageName = "com.tencent.mm",
            versionName = "8.0.49",
            versionCode = 2460L,
            apkPath = "/data/app/com.tencent.mm/base.apk",
            apkLastModified = 1710000000000L,
        )

        assertEquals("com.tencent.mm 8.0.49(2460)", fingerprint.displayName)
    }
}
