package com.android.wechathook

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.MenuItem
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode
import java.lang.reflect.Method

internal const val WECHAT_SETTINGS_ENTRY_KEY = "wechathook_diagnostics_entry"
internal const val WECHAT_SETTINGS_ENTRY_TITLE = "WeChat Hook 诊断"

internal data class ModuleDiagnosticsIntentSpec(
    val packageName: String,
    val className: String,
    val source: String,
    val targetPackage: String,
)

internal fun buildModuleDiagnosticsIntentSpec(): ModuleDiagnosticsIntentSpec {
    return ModuleDiagnosticsIntentSpec(
        packageName = "com.android.wechathook",
        className = "com.android.wechathook.MainActivity",
        source = "wechat_settings",
        targetPackage = "com.tencent.mm",
    )
}

internal fun buildModuleDiagnosticsIntent(): Intent {
    val spec = buildModuleDiagnosticsIntentSpec()
    return Intent().apply {
        setClassName(spec.packageName, spec.className)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra("source", spec.source)
        putExtra("targetPackage", spec.targetPackage)
    }
}

internal const val MODERN_WECHAT_SETTINGS_CLASS = "com.tencent.mm.plugin.setting.ui.setting_new.MainSettingsUI"

internal fun isModuleSettingsPreferenceKey(key: String?): Boolean = key == WECHAT_SETTINGS_ENTRY_KEY

internal fun isModernWeChatSettingsClass(className: String?): Boolean = className == MODERN_WECHAT_SETTINGS_CLASS

internal fun openModuleDiagnostics(context: Context) {
    context.startActivity(buildModuleDiagnosticsIntent())
}

internal fun buildWeChatDiagnosticsDialogReport(): String {
    return buildLocalDiagnosticReport(
        source = "wechat_settings",
        targetPackage = "com.tencent.mm",
    )
}

internal fun showModuleDiagnosticsDialog(activity: Activity) {
    val report = buildWeChatDiagnosticsDialogReport()
    AlertDialog.Builder(activity)
        .setTitle(WECHAT_SETTINGS_ENTRY_TITLE)
        .setMessage(report)
        .setPositiveButton("复制") { _, _ ->
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("WeChat Hook Diagnostics", report))
        }
        .setNegativeButton("关闭", null)
        .show()
}

internal fun installModernWeChatSettingsEntry(
    module: XposedInterface,
    classLoader: ClassLoader,
    log: (priority: Int, tag: String, message: String) -> Unit,
) {
    val settingsClass = runCatching { classLoader.loadClass(MODERN_WECHAT_SETTINGS_CLASS) }.getOrNull()
    if (settingsClass == null) {
        log(Log.WARN, WECHAT_SETTINGS_ENTRY_TAG, "Modern WeChat settings class not found")
    }
    val onResume = Activity::class.java.getDeclaredMethod("onResume")

    module.hook(onResume)
        .setExceptionMode(ExceptionMode.PROTECTIVE)
        .intercept { chain ->
            val result = chain.proceed()
            val activity = chain.getThisObject() as? Activity ?: return@intercept result
            if (!isModernWeChatSettingsClass(activity.javaClass.name)) {
                return@intercept result
            }
            log(Log.INFO, WECHAT_SETTINGS_ENTRY_TAG, "Modern WeChat settings activity resumed: ${activity.javaClass.name}")
            if (addModernSettingsMenuEntry(activity)) {
                log(Log.INFO, WECHAT_SETTINGS_ENTRY_TAG, "Modern WeChat settings menu entry added")
            } else {
                log(Log.WARN, WECHAT_SETTINGS_ENTRY_TAG, "Modern WeChat settings menu entry method not found")
            }
            result
        }

    log(Log.INFO, WECHAT_SETTINGS_ENTRY_TAG, "Modern WeChat settings entry installed")
}

private fun addModernSettingsMenuEntry(activity: Activity): Boolean {
    val listener = MenuItem.OnMenuItemClickListener {
        showModuleDiagnosticsDialog(activity)
        true
    }
    val title = WECHAT_SETTINGS_ENTRY_TITLE
    val method = findTextOptionMenuMethod(activity.javaClass) ?: return false
    method.isAccessible = true
    method.invoke(activity, 0, title, listener)
    return true
}

private fun findTextOptionMenuMethod(type: Class<*>): Method? {
    return generateSequence(type) { it.superclass }
        .flatMap { it.declaredMethods.asSequence() }
        .firstOrNull { it.isTextOptionMenuMethod() }
}

private fun Method.isTextOptionMenuMethod(): Boolean {
    if (name != "addTextOptionMenu") return false
    val types = parameterTypes
    return types.size == 3 &&
        types[0] == Int::class.javaPrimitiveType &&
        CharSequence::class.java.isAssignableFrom(types[1]) &&
        types[2] == MenuItem.OnMenuItemClickListener::class.java
}

internal fun installLegacyWeChatSettingsEntry(
    module: XposedInterface,
    classLoader: ClassLoader,
    log: (priority: Int, tag: String, message: String) -> Unit,
) {
    val settingsClass = try {
        classLoader.loadClass("com.tencent.mm.plugin.setting.ui.setting.SettingsUI")
    } catch (exception: ClassNotFoundException) {
        log(Log.WARN, WECHAT_SETTINGS_ENTRY_TAG, "Legacy WeChat settings class not found")
        return
    }

    val preferenceClass = try {
        classLoader.loadClass("com.tencent.mm.ui.base.preference.Preference")
    } catch (exception: ClassNotFoundException) {
        log(Log.WARN, WECHAT_SETTINGS_ENTRY_TAG, "WeChat preference class not found")
        return
    }

    val preferenceScreenClass = try {
        classLoader.loadClass("com.tencent.mm.ui.base.preference.r")
    } catch (exception: ClassNotFoundException) {
        log(Log.WARN, WECHAT_SETTINGS_ENTRY_TAG, "WeChat preference screen interface not found")
        return
    }

    val initView = try {
        settingsClass.getDeclaredMethod("initView")
    } catch (exception: NoSuchMethodException) {
        log(Log.WARN, WECHAT_SETTINGS_ENTRY_TAG, "WeChat settings initView method not found")
        return
    }
    val preferenceTreeClick = try {
        settingsClass.getDeclaredMethod("onPreferenceTreeClick", preferenceScreenClass, preferenceClass)
    } catch (exception: NoSuchMethodException) {
        log(Log.WARN, WECHAT_SETTINGS_ENTRY_TAG, "WeChat settings click method not found")
        return
    }
    val getPreferenceScreen = try {
        settingsClass.getMethod("getPreferenceScreen")
    } catch (exception: NoSuchMethodException) {
        log(Log.WARN, WECHAT_SETTINGS_ENTRY_TAG, "WeChat preference screen accessor not found")
        return
    }
    val preferenceConstructor = try {
        preferenceClass.getConstructor(Context::class.java)
    } catch (exception: NoSuchMethodException) {
        log(Log.WARN, WECHAT_SETTINGS_ENTRY_TAG, "WeChat preference constructor not found")
        return
    }
    val setKey = try {
        preferenceClass.getMethod("H", String::class.java)
    } catch (exception: NoSuchMethodException) {
        log(Log.WARN, WECHAT_SETTINGS_ENTRY_TAG, "WeChat preference key setter not found")
        return
    }
    val setTitle = try {
        preferenceClass.getMethod("R", CharSequence::class.java)
    } catch (exception: NoSuchMethodException) {
        log(Log.WARN, WECHAT_SETTINGS_ENTRY_TAG, "WeChat preference title setter not found")
        return
    }
    val getKey = try {
        preferenceClass.getMethod("k")
    } catch (exception: NoSuchMethodException) {
        log(Log.WARN, WECHAT_SETTINGS_ENTRY_TAG, "WeChat preference key getter not found")
        return
    }
    val addPreference = try {
        classLoader
            .loadClass("com.tencent.mm.ui.base.preference.h0")
            .getMethod("d", preferenceClass, Int::class.javaPrimitiveType)
    } catch (exception: ClassNotFoundException) {
        log(Log.WARN, WECHAT_SETTINGS_ENTRY_TAG, "WeChat preference screen implementation not found")
        return
    } catch (exception: NoSuchMethodException) {
        log(Log.WARN, WECHAT_SETTINGS_ENTRY_TAG, "WeChat preference add method not found")
        return
    }

    module.hook(initView)
        .setExceptionMode(ExceptionMode.PROTECTIVE)
        .intercept { chain ->
            val result = chain.proceed()
            val activity = chain.getThisObject() as? Activity ?: return@intercept result
            val preferenceScreen = getPreferenceScreen.invoke(activity) ?: return@intercept result
            val preference = preferenceConstructor.newInstance(activity)
            setKey.invoke(preference, WECHAT_SETTINGS_ENTRY_KEY)
            setTitle.invoke(preference, WECHAT_SETTINGS_ENTRY_TITLE)
            addPreference.invoke(preferenceScreen, preference, 0)
            result
        }

    module.hook(preferenceTreeClick)
        .setExceptionMode(ExceptionMode.PROTECTIVE)
        .intercept { chain ->
            val preference = chain.getArg(1)
            val key = getKey.invoke(preference) as? String
            if (!isModuleSettingsPreferenceKey(key)) {
                return@intercept chain.proceed()
            }
            val activity = chain.getThisObject() as? Activity ?: return@intercept true
            showModuleDiagnosticsDialog(activity)
            true
        }

    log(Log.INFO, WECHAT_SETTINGS_ENTRY_TAG, "Legacy WeChat settings entry installed")
}

private const val WECHAT_SETTINGS_ENTRY_TAG = "WeChatSettingsEntry"
