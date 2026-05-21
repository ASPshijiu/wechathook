package com.android.wechathook.antiupdate

data class HookCandidateSummary(
    val className: String,
    val memberName: String,
    val score: Int,
    val matchedFeatures: List<String>,
)

data class HookDiagnosticEvent(
    val fingerprint: VersionFingerprint,
    val hookId: HookTargetId,
    val cacheHit: Boolean,
    val candidateCount: Int,
    val topCandidates: List<HookCandidateSummary>,
    val failure: HookResolveFailure?,
) {
    fun toLogMessage(): String {
        val candidates = topCandidates.joinToString(separator = ";") { candidate ->
            "${candidate.className}#${candidate.memberName}:score=${candidate.score}:features=${candidate.matchedFeatures.joinToString(separator = ",")}"
        }
        val failureName = failure?.javaClass?.simpleName ?: "none"
        return "HookDiagnostic(fingerprint=${fingerprint.displayName}, hookId=${hookId.value}, cacheHit=$cacheHit, candidateCount=$candidateCount, topCandidates=[$candidates], failure=$failureName)"
    }
}
