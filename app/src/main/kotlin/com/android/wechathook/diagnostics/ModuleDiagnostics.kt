package com.android.wechathook.diagnostics

data class ModuleDiagnostics(
    val moduleName: String,
    val moduleVersion: String,
    val packageName: String,
    val processName: String,
    val isMainProcess: Boolean,
    val wechatVersionName: String,
    val wechatVersionCode: Long,
    val startupHookInstalled: Boolean,
    val wechatApplicationHookTarget: String?,
    val wechatApplicationHookStatus: HookStatus,
) {
    fun toReport(): String {
        return buildString {
            appendLine("ModuleDiagnostics")
            appendLine("moduleName=$moduleName")
            appendLine("moduleVersion=$moduleVersion")
            appendLine("packageName=$packageName")
            appendLine("processName=$processName")
            appendLine("mainProcess=$isMainProcess")
            appendLine("wechatVersionName=$wechatVersionName")
            appendLine("wechatVersionCode=$wechatVersionCode")
            appendLine("startupHookInstalled=$startupHookInstalled")
            appendLine("wechatApplicationHookTarget=${wechatApplicationHookTarget.orEmpty()}")
            appendLine("wechatApplicationHookStatus=${wechatApplicationHookStatus.name}")
        }.trimEnd()
    }
}

enum class HookStatus {
    INSTALLED,
    SKIPPED,
    FAILED,
}
