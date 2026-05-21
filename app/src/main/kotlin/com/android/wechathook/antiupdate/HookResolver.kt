package com.android.wechathook.antiupdate

class HookResolver(
    private val cache: HookCache,
    private val targetValidator: (ResolvedHookTarget) -> Boolean,
    private val candidateProvider: (HookDefinition) -> List<HookCandidate>,
) {
    fun resolve(fingerprint: VersionFingerprint, definition: HookDefinition): HookResolveResult {
        val cached = cache.get(fingerprint, definition.id)
        if (cached != null && targetValidator(cached.target)) {
            return HookResolveResult.CacheHit(cached.target)
        }

        val bestCandidate = candidateProvider(definition).maxByOrNull(HookCandidate::score)
            ?: return HookResolveResult.Failed(HookResolveFailure.NoCandidates)

        if (!bestCandidate.passes(definition.minimumScore)) {
            return HookResolveResult.Failed(
                HookResolveFailure.ScoreBelowThreshold(
                    bestScore = bestCandidate.score,
                    minimumScore = definition.minimumScore,
                ),
            )
        }

        cache.put(
            fingerprint = fingerprint,
            hookId = definition.id,
            entry = HookCacheEntry(
                target = bestCandidate.target,
                confidence = bestCandidate.score,
                frameworkVersion = FRAMEWORK_VERSION,
            ),
        )

        return HookResolveResult.Scanned(bestCandidate.target, bestCandidate.score)
    }

    private companion object {
        const val FRAMEWORK_VERSION = 1
    }
}

sealed interface HookResolveResult {
    data class CacheHit(val target: ResolvedHookTarget) : HookResolveResult

    data class Scanned(val target: ResolvedHookTarget, val score: Int) : HookResolveResult

    data class Failed(val failure: HookResolveFailure) : HookResolveResult
}

sealed interface HookResolveFailure {
    data object NoCandidates : HookResolveFailure

    data class ScoreBelowThreshold(
        val bestScore: Int,
        val minimumScore: Int,
    ) : HookResolveFailure
}
