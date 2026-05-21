package com.android.wechathook.antiupdate

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import java.io.File

object VersionFingerprintProvider {
    fun fromPackageInfo(
        packageName: String,
        versionName: String,
        versionCode: Long,
        apkPath: String,
        apkLastModified: Long,
    ): VersionFingerprint {
        return VersionFingerprint(
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            apkPath = apkPath,
            apkLastModified = apkLastModified,
        )
    }

    fun fromAndroidPackageInfo(packageInfo: PackageInfo, applicationInfo: ApplicationInfo): VersionFingerprint {
        val apkPath = applicationInfo.sourceDir.orEmpty()
        return fromPackageInfo(
            packageName = packageInfo.packageName,
            versionName = packageInfo.versionName.orEmpty(),
            versionCode = packageInfo.longVersionCode,
            apkPath = apkPath,
            apkLastModified = File(apkPath).lastModified(),
        )
    }
}
