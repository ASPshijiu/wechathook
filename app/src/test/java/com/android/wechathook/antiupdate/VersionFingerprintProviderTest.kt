package com.android.wechathook.antiupdate

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionFingerprintProviderTest {
    @Test
    fun providerBuildsFingerprintFromPlainInputs() {
        val fingerprint = VersionFingerprintProvider.fromPackageInfo(
            packageName = "com.tencent.mm",
            versionName = "8.0.49",
            versionCode = 2460L,
            apkPath = "/data/app/com.tencent.mm/base.apk",
            apkLastModified = 1710000000000L,
        )

        assertEquals("com.tencent.mm", fingerprint.packageName)
        assertEquals("8.0.49", fingerprint.versionName)
        assertEquals(2460L, fingerprint.versionCode)
        assertEquals("/data/app/com.tencent.mm/base.apk", fingerprint.apkPath)
        assertEquals(1710000000000L, fingerprint.apkLastModified)
    }
}
