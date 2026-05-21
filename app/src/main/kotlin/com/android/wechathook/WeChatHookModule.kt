package com.android.wechathook

import android.util.Log
import com.android.wechathook.antiupdate.HookDefinition
import com.android.wechathook.antiupdate.HookDiagnosticEvent
import com.android.wechathook.antiupdate.HookResolveResult
import com.android.wechathook.antiupdate.HookResolver
import com.android.wechathook.antiupdate.MemoryHookCache
import com.android.wechathook.antiupdate.VersionFingerprintProvider
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

class WeChatHookModule : XposedModule() {
    private val hookCache = MemoryHookCache()

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "Module loaded")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName != WECHAT_PACKAGE_NAME) return

        log(Log.INFO, TAG, "WeChat package loaded")
        runAntiUpdateResolution(param)
    }

    private fun runAntiUpdateResolution(param: PackageLoadedParam) {
        val fingerprint = VersionFingerprintProvider.fromPackageInfo(
            packageName = param.packageName,
            versionName = param.applicationInfo.metaData?.getString("versionName").orEmpty(),
            versionCode = 0L,
            apkPath = param.applicationInfo.sourceDir.orEmpty(),
            apkLastModified = param.applicationInfo.sourceDir?.let { java.io.File(it).lastModified() } ?: 0L,
        )
        val resolver = HookResolver(
            cache = hookCache,
            targetValidator = { false },
            candidateProvider = { emptyList() },
        )

        FIRST_STAGE_HOOKS.forEach { definition ->
            val result = resolver.resolve(fingerprint, definition)
            val diagnostic = HookDiagnosticEvent(
                fingerprint = fingerprint,
                hookId = definition.id,
                cacheHit = false,
                candidateCount = 0,
                topCandidates = emptyList(),
                failure = (result as? HookResolveResult.Failed)?.failure,
            )
            log(Log.INFO, TAG, diagnostic.toLogMessage())
        }
    }

    companion object {
        private const val TAG = "WeChatHook"
        private const val WECHAT_PACKAGE_NAME = "com.tencent.mm"
        private val FIRST_STAGE_HOOKS = emptyList<HookDefinition>()
    }
}
