package com.android.wechathook.antiupdate

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import java.io.File
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

    @Test
    fun providerBuildsFingerprintFromAndroidPackageInfo() {
        val apk = File.createTempFile("wechat", ".apk")
        try {
            val lastModified = 1710001234000L
            apk.setLastModified(lastModified)
            val packageInfo = object : PackageInfo() {
                override fun getLongVersionCode(): Long = 8000002460L
            }.apply {
                packageName = "com.tencent.mm"
                versionName = "8.0.49"
            }
            val applicationInfo = ApplicationInfo().apply {
                sourceDir = apk.absolutePath
            }

            val fingerprint = VersionFingerprintProvider.fromAndroidPackageInfo(packageInfo, applicationInfo)

            assertEquals("com.tencent.mm", fingerprint.packageName)
            assertEquals("8.0.49", fingerprint.versionName)
            assertEquals(8000002460L, fingerprint.versionCode)
            assertEquals(apk.absolutePath, fingerprint.apkPath)
            assertEquals(lastModified, fingerprint.apkLastModified)
        } finally {
            apk.delete()
        }
    }
}
