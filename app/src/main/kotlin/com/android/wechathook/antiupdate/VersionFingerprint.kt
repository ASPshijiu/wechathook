package com.android.wechathook.antiupdate

data class VersionFingerprint(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val apkPath: String,
    val apkLastModified: Long,
) {
    val cacheKey: String = listOf(
        packageName,
        versionName,
        versionCode.toString(),
        apkPath,
        apkLastModified.toString(),
    ).joinToString(separator = "|")

    val displayName: String = "$packageName $versionName($versionCode)"
}
