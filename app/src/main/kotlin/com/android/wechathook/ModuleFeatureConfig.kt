package com.android.wechathook

import android.content.Context

internal data class ModuleFeatureConfig(
    val diagnosticsEnabled: Boolean = true,
    val settingsEntryEnabled: Boolean = true,
    val antiUpdateResolutionEnabled: Boolean = false,
    val userDataReadEnabled: Boolean = false,
)

internal object ModuleFeatureConfigStore {
    private const val PREFERENCES_NAME = "wechathook_module_config"
    private const val DIAGNOSTICS_ENABLED = "diagnostics_enabled"
    private const val SETTINGS_ENTRY_ENABLED = "settings_entry_enabled"
    private const val ANTI_UPDATE_RESOLUTION_ENABLED = "anti_update_resolution_enabled"
    private const val USER_DATA_READ_ENABLED = "user_data_read_enabled"

    fun load(context: Context): ModuleFeatureConfig {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return ModuleFeatureConfig(
            diagnosticsEnabled = preferences.getBoolean(DIAGNOSTICS_ENABLED, true),
            settingsEntryEnabled = preferences.getBoolean(SETTINGS_ENTRY_ENABLED, true),
            antiUpdateResolutionEnabled = preferences.getBoolean(ANTI_UPDATE_RESOLUTION_ENABLED, false),
            userDataReadEnabled = preferences.getBoolean(USER_DATA_READ_ENABLED, false),
        )
    }
}
