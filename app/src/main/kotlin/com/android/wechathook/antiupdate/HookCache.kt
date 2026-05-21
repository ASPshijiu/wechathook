package com.android.wechathook.antiupdate

interface HookCache {
    fun get(fingerprint: VersionFingerprint, hookId: HookTargetId): HookCacheEntry?

    fun put(fingerprint: VersionFingerprint, hookId: HookTargetId, entry: HookCacheEntry)
}

data class HookCacheEntry(
    val target: ResolvedHookTarget,
    val confidence: Int,
    val frameworkVersion: Int,
)

class MemoryHookCache : HookCache {
    private val entries = linkedMapOf<String, HookCacheEntry>()

    override fun get(fingerprint: VersionFingerprint, hookId: HookTargetId): HookCacheEntry? {
        return entries[key(fingerprint, hookId)]
    }

    override fun put(fingerprint: VersionFingerprint, hookId: HookTargetId, entry: HookCacheEntry) {
        entries[key(fingerprint, hookId)] = entry
    }

    private fun key(fingerprint: VersionFingerprint, hookId: HookTargetId): String {
        return "${fingerprint.cacheKey}::${hookId.value}"
    }
}
