package com.android.wechathook.antiupdate

data class HookFeatureScore(
    val name: String,
    val weight: Int,
    val matched: Boolean,
)

data class HookCandidate(
    val target: ResolvedHookTarget,
    val features: List<HookFeatureScore>,
) {
    val score: Int = features.filter(HookFeatureScore::matched).sumOf(HookFeatureScore::weight)

    val matchedFeatureNames: List<String> = features
        .filter(HookFeatureScore::matched)
        .map(HookFeatureScore::name)

    fun passes(minimumScore: Int): Boolean = score >= minimumScore
}
