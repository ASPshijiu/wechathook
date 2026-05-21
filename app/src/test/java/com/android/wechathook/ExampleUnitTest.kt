package com.android.wechathook

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
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
