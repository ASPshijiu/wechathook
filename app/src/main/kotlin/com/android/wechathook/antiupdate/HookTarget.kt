package com.android.wechathook.antiupdate

@JvmInline
value class HookTargetId(val value: String)

sealed interface ResolvedHookTarget {
    data class Method(
        val className: String,
        val methodName: String,
        val parameterTypeNames: List<String>,
        val returnTypeName: String,
    ) : ResolvedHookTarget

    data class Constructor(
        val className: String,
        val parameterTypeNames: List<String>,
    ) : ResolvedHookTarget
}

data class HookDefinition(
    val id: HookTargetId,
    val minimumScore: Int,
)
