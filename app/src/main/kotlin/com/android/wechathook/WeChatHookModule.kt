package com.android.wechathook

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.android.wechathook.antiupdate.HookDefinition
import com.android.wechathook.antiupdate.HookDiagnosticEvent
import com.android.wechathook.antiupdate.HookResolver
import com.android.wechathook.antiupdate.MemoryHookCache
import com.android.wechathook.antiupdate.VersionFingerprint
import com.android.wechathook.antiupdate.VersionFingerprintProvider
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.io.File

internal fun shouldRunAntiUpdateResolution(hookDefinitions: List<HookDefinition>): Boolean = hookDefinitions.isNotEmpty()

internal fun buildStartupDiagnosticMessage(
    packageName: String,
    processName: String,
    isMainProcess: Boolean,
    isFirstPackage: Boolean,
    hookCount: Int,
): String {
    return "StartupDiagnostic(packageName=$packageName, processName=$processName, mainProcess=$isMainProcess, isFirstPackage=$isFirstPackage, hookCount=$hookCount, antiUpdateEnabled=${hookCount > 0})"
}

internal fun buildFirstHookDiagnosticMessage(
    target: String,
    packageName: String,
    triggered: Boolean,
): String {
    return "FirstHookDiagnostic(target=$target, packageName=$packageName, triggered=$triggered)"
}

internal fun buildWeChatApplicationHookDiagnosticMessage(
    target: String,
    packageName: String,
    triggered: Boolean,
): String {
    return "WeChatApplicationHookDiagnostic(target=$target, packageName=$packageName, triggered=$triggered)"
}

internal fun buildVersionFingerprint(
    packageName: String,
    applicationInfo: ApplicationInfo,
    packageInfoProvider: () -> PackageInfo?,
): VersionFingerprint {
    val packageInfo = try {
        packageInfoProvider()
    } catch (_: Exception) {
        null
    }
    if (packageInfo != null) {
        return VersionFingerprintProvider.fromAndroidPackageInfo(packageInfo, applicationInfo)
    }

    val apkPath = applicationInfo.sourceDir.orEmpty()
    return VersionFingerprintProvider.fromPackageInfo(
        packageName = packageName,
        versionName = "",
        versionCode = 0L,
        apkPath = apkPath,
        apkLastModified = apkPath.takeIf(String::isNotEmpty)?.let { File(it).lastModified() } ?: 0L,
    )
}

class WeChatHookModule : XposedModule() {
    private val hookCache = MemoryHookCache()

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "Module loaded")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName != WECHAT_PACKAGE_NAME) return

        val processName = currentProcessName().orEmpty()
        val isMainProcess = processName == param.packageName
        log(Log.INFO, TAG, "WeChat package loaded")
        log(
            Log.INFO,
            TAG,
            buildStartupDiagnosticMessage(
                packageName = param.packageName,
                processName = processName,
                isMainProcess = isMainProcess,
                isFirstPackage = param.isFirstPackage,
                hookCount = FIRST_STAGE_HOOKS.size,
            ),
        )
        if (!isMainProcess) return

        installApplicationOnCreateDiagnosticHook(param.packageName)
        installWeChatApplicationOnCreateDiagnosticHook(param)
        if (!shouldRunAntiUpdateResolution(FIRST_STAGE_HOOKS)) return

        runAntiUpdateResolution(param)
    }

    private fun installApplicationOnCreateDiagnosticHook(packageName: String) {
        val onCreate = Application::class.java.getDeclaredMethod("onCreate")
        hook(onCreate)
            .setExceptionMode(ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                log(
                    Log.INFO,
                    TAG,
                    buildFirstHookDiagnosticMessage(
                        target = APPLICATION_ON_CREATE_TARGET,
                        packageName = packageName,
                        triggered = true,
                    ),
                )
                result
            }
        log(Log.INFO, TAG, "First hook installed: $APPLICATION_ON_CREATE_TARGET")
    }

    private fun installWeChatApplicationOnCreateDiagnosticHook(param: PackageLoadedParam) {
        try {
            val applicationClassName = param.applicationInfo.className.orEmpty()
            if (applicationClassName.isEmpty()) {
                log(Log.WARN, TAG, "WeChat application class is empty")
                return
            }
            val applicationClass = param.defaultClassLoader.loadClass(applicationClassName)
            val attachBaseContext = applicationClass.getDeclaredMethod("attachBaseContext", Context::class.java)
            val target = "$applicationClassName#attachBaseContext(android.content.Context)"
            hook(attachBaseContext)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    log(
                        Log.INFO,
                        TAG,
                        buildWeChatApplicationHookDiagnosticMessage(
                            target = target,
                            packageName = param.packageName,
                            triggered = true,
                        ),
                    )
                    result
                }
            log(Log.INFO, TAG, "WeChat application hook installed: $target")
        } catch (exception: Exception) {
            log(Log.WARN, TAG, "WeChat application hook skipped: ${exception.javaClass.simpleName}")
        }
    }

    private fun runAntiUpdateResolution(param: PackageLoadedParam) {
        val fingerprint = buildVersionFingerprint(
            packageName = param.packageName,
            applicationInfo = param.applicationInfo,
            packageInfoProvider = { loadPackageInfo(param.packageName) },
        )
        val resolver = HookResolver(
            cache = hookCache,
            targetValidator = { false },
            candidateProvider = { emptyList() },
        )

        FIRST_STAGE_HOOKS.forEach { definition ->
            val report = resolver.resolveWithReport(fingerprint, definition)
            val diagnostic = HookDiagnosticEvent.fromReport(
                fingerprint = fingerprint,
                hookId = definition.id,
                report = report,
            )
            log(Log.INFO, TAG, diagnostic.toLogMessage())
        }
    }

    private fun loadPackageInfo(packageName: String): PackageInfo? {
        val application = currentApplication() ?: return null
        val packageManager = application.packageManager ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
    }

    private fun currentApplication(): Application? {
        return Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as? Application
    }

    private fun currentProcessName(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentProcessName")
                .invoke(null) as? String
        }
    }

    companion object {
        private const val TAG = "WeChatHook"
        private const val WECHAT_PACKAGE_NAME = "com.tencent.mm"
        private const val APPLICATION_ON_CREATE_TARGET = "android.app.Application#onCreate"
        private val FIRST_STAGE_HOOKS = emptyList<HookDefinition>()
    }
}
