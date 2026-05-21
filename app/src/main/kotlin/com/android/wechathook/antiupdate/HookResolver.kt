package com.android.wechathook.antiupdate

class HookResolver(
    private val cache: HookCache,
    private val targetValidator: (ResolvedHookTarget) -> Boolean,
    private val candidateProvider: (HookDefinition) -> List<HookCandidate>,
) {
    fun resolve(fingerprint: VersionFingerprint, definition: HookDefinition): HookResolveResult {
        return resolveWithReport(fingerprint, definition).result
    }

    fun resolveWithReport(fingerprint: VersionFingerprint, definition: HookDefinition): HookResolveReport {
        return try {
            resolveSafely(fingerprint, definition)
        } catch (exception: Exception) {
            HookResolveReport(
                result = HookResolveResult.Failed(HookResolveFailure.ExceptionThrown(exception.javaClass.simpleName)),
                cacheHit = false,
                candidates = emptyList(),
            )
        }
    }

    private fun resolveSafely(fingerprint: VersionFingerprint, definition: HookDefinition): HookResolveReport {
        val cached = cache.get(fingerprint, definition.id)
        if (cached != null && targetValidator(cached.target)) {
            return HookResolveReport(
                result = HookResolveResult.CacheHit(cached.target),
                cacheHit = true,
                candidates = emptyList(),
            )
        }

        val candidates = candidateProvider(definition).sortedByDescending(HookCandidate::score)
        val bestCandidate = candidates.firstOrNull()
            ?: return HookResolveReport(
                result = HookResolveResult.Failed(HookResolveFailure.NoCandidates),
                cacheHit = false,
                candidates = emptyList(),
            )

        if (!bestCandidate.passes(definition.minimumScore)) {
            return HookResolveReport(
                result = HookResolveResult.Failed(
                    HookResolveFailure.ScoreBelowThreshold(
                        bestScore = bestCandidate.score,
                        minimumScore = definition.minimumScore,
                    ),
                ),
                cacheHit = false,
                candidates = candidates,
            )
        }

        try {
            cache.put(
                fingerprint = fingerprint,
                hookId = definition.id,
                entry = HookCacheEntry(
                    target = bestCandidate.target,
                    confidence = bestCandidate.score,
                    frameworkVersion = FRAMEWORK_VERSION,
                ),
            )
        } catch (exception: Exception) {
            return HookResolveReport(
                result = HookResolveResult.Failed(HookResolveFailure.ExceptionThrown(exception.javaClass.simpleName)),
                cacheHit = false,
                candidates = candidates,
            )
        }

        return HookResolveReport(
            result = HookResolveResult.Scanned(bestCandidate.target, bestCandidate.score),
            cacheHit = false,
            candidates = candidates,
        )
    }

    private companion object {
        const val FRAMEWORK_VERSION = 1
    }
}

data class HookResolveReport(
    val result: HookResolveResult,
    val cacheHit: Boolean,
    val candidates: List<HookCandidate>,
)

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

    data class ExceptionThrown(val exceptionType: String) : HookResolveFailure
}
