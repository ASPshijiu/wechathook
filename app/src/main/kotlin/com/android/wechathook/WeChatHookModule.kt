package com.android.wechathook

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

class WeChatHookModule : XposedModule() {
    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "Module loaded")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName != WECHAT_PACKAGE_NAME) return

        log(Log.INFO, TAG, "WeChat package loaded")
    }

    companion object {
        private const val TAG = "WeChatHook"
        private const val WECHAT_PACKAGE_NAME = "com.tencent.mm"
    }
}
