package com.android.wechathook.antiupdate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookCandidateTest {
    @Test
    fun candidateScoreIsSumOfMatchedFeatures() {
        val candidate = HookCandidate(
            target = ResolvedHookTarget.Method(
                className = "com.tencent.mm.SomeClass",
                methodName = "a",
                parameterTypeNames = listOf("java.lang.String", "int"),
                returnTypeName = "boolean",
            ),
            features = listOf(
                HookFeatureScore("parameter_count", 30, true),
                HookFeatureScore("return_type", 20, true),
                HookFeatureScore("string_constant", 40, false),
            ),
        )

        assertEquals(50, candidate.score)
        assertEquals(listOf("parameter_count", "return_type"), candidate.matchedFeatureNames)
    }

    @Test
    fun thresholdRequiresScoreAtLeastMinimum() {
        val candidate = HookCandidate(
            target = ResolvedHookTarget.Method(
                className = "com.tencent.mm.SomeClass",
                methodName = "a",
                parameterTypeNames = emptyList(),
                returnTypeName = "void",
            ),
            features = listOf(HookFeatureScore("class_shape", 40, true)),
        )

        assertTrue(candidate.passes(minimumScore = 40))
        assertFalse(candidate.passes(minimumScore = 41))
    }
}
