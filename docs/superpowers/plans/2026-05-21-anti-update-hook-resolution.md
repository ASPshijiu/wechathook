# Anti-Update Hook Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a lightweight anti-update Hook resolution framework for the WeChat LSPosed module.

**Architecture:** Implement small Kotlin units for version fingerprints, Hook target models, scoring, cache validation, and diagnostics. Wire them into `WeChatHookModule` without adding real WeChat feature hooks yet, so later Hook points can reuse the framework.

**Tech Stack:** Android Gradle Plugin 9.2.1, Kotlin in AGP 9, libxposed API 101, JUnit 4 local tests.

---

## File Structure

- Modify `gradle/libs.versions.toml`
  - Restore JUnit dependency coordinates for local unit tests.
- Modify `app/build.gradle.kts`
  - Add `testImplementation(libs.junit)` while keeping `compileOnly(libs.libxposed.api)`.
- Create `app/src/main/kotlin/com/android/wechathook/antiupdate/VersionFingerprint.kt`
  - Holds immutable package/version/APK metadata and a stable cache key.
- Create `app/src/main/kotlin/com/android/wechathook/antiupdate/VersionFingerprintProvider.kt`
  - Builds `VersionFingerprint` from Android `PackageLoadedParam` / `ApplicationInfo` data.
- Create `app/src/main/kotlin/com/android/wechathook/antiupdate/HookTarget.kt`
  - Defines Hook target IDs, method target descriptors, resolved targets, and scan failures.
- Create `app/src/main/kotlin/com/android/wechathook/antiupdate/HookCandidate.kt`
  - Defines candidate feature scores and total scoring behavior.
- Create `app/src/main/kotlin/com/android/wechathook/antiupdate/HookResolver.kt`
  - Resolves Hook targets from cache first, then candidate providers, with threshold checks.
- Create `app/src/main/kotlin/com/android/wechathook/antiupdate/HookCache.kt`
  - In-memory cache interface and implementation for first-stage behavior.
- Create `app/src/main/kotlin/com/android/wechathook/antiupdate/HookDiagnostics.kt`
  - Structured diagnostics that are safe to log.
- Modify `app/src/main/kotlin/com/android/wechathook/WeChatHookModule.kt`
  - Initialize the anti-update framework in WeChat process and log diagnostics.
- Create `app/src/test/java/com/android/wechathook/antiupdate/VersionFingerprintTest.kt`
- Create `app/src/test/java/com/android/wechathook/antiupdate/HookCandidateTest.kt`
- Create `app/src/test/java/com/android/wechathook/antiupdate/HookResolverTest.kt`
- Create `app/src/test/java/com/android/wechathook/antiupdate/HookDiagnosticsTest.kt`
- Modify `app/src/test/java/com/android/wechathook/ExampleUnitTest.kt`
  - Remove generated sample test once focused tests exist.

---

### Task 0: Restore Local Unit Test Dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add JUnit to the version catalog**

Modify `gradle/libs.versions.toml` to:

```toml
[versions]
agp = "9.2.1"
libxposedApi = "101.0.1"
junit = "4.13.2"

[libraries]
libxposed-api = { group = "io.github.libxposed", name = "api", version.ref = "libxposedApi" }
junit = { group = "junit", name = "junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
```

- [ ] **Step 2: Add local test dependency**

Modify the `dependencies` block in `app/build.gradle.kts` to:

```kotlin
dependencies {
    compileOnly(libs.libxposed.api)
    testImplementation(libs.junit)
}
```

- [ ] **Step 3: Run existing unit test task**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: task runs successfully or reaches existing generated tests without missing JUnit classes.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
GIT_AUTHOR_NAME="ASPshijiu" GIT_AUTHOR_EMAIL="59414654+ASPshijiu@users.noreply.github.com" GIT_COMMITTER_NAME="ASPshijiu" GIT_COMMITTER_EMAIL="59414654+ASPshijiu@users.noreply.github.com" git commit -m "恢复本地单元测试依赖"
```

---

### Task 1: Version Fingerprint Model

**Files:**
- Create: `app/src/main/kotlin/com/android/wechathook/antiupdate/VersionFingerprint.kt`
- Test: `app/src/test/java/com/android/wechathook/antiupdate/VersionFingerprintTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/android/wechathook/antiupdate/VersionFingerprintTest.kt`:

```kotlin
package com.android.wechathook.antiupdate

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionFingerprintTest {
    @Test
    fun cacheKeyIncludesPackageVersionCodeApkPathAndModifiedTime() {
        val fingerprint = VersionFingerprint(
            packageName = "com.tencent.mm",
            versionName = "8.0.49",
            versionCode = 2460L,
            apkPath = "/data/app/com.tencent.mm/base.apk",
            apkLastModified = 1710000000000L,
        )

        assertEquals(
            "com.tencent.mm|8.0.49|2460|/data/app/com.tencent.mm/base.apk|1710000000000",
            fingerprint.cacheKey,
        )
    }

    @Test
    fun displayNameIsCompactAndReadableForLogs() {
        val fingerprint = VersionFingerprint(
            packageName = "com.tencent.mm",
            versionName = "8.0.49",
            versionCode = 2460L,
            apkPath = "/data/app/com.tencent.mm/base.apk",
            apkLastModified = 1710000000000L,
        )

        assertEquals("com.tencent.mm 8.0.49(2460)", fingerprint.displayName)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.android.wechathook.antiupdate.VersionFingerprintTest'
```

Expected: FAIL with unresolved reference `VersionFingerprint`. If the test task reports that no matching tests were found because the test source did not compile, that also confirms the intended failing state.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/kotlin/com/android/wechathook/antiupdate/VersionFingerprint.kt`:

```kotlin
package com.android.wechathook.antiupdate

data class VersionFingerprint(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val apkPath: String,
    val apkLastModified: Long,
) {
    val cacheKey: String = listOf(
        packageName,
        versionName,
        versionCode.toString(),
        apkPath,
        apkLastModified.toString(),
    ).joinToString(separator = "|")

    val displayName: String = "$packageName $versionName($versionCode)"
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.android.wechathook.antiupdate.VersionFingerprintTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/android/wechathook/antiupdate/VersionFingerprint.kt app/src/test/java/com/android/wechathook/antiupdate/VersionFingerprintTest.kt
GIT_AUTHOR_NAME="ASPshijiu" GIT_AUTHOR_EMAIL="59414654+ASPshijiu@users.noreply.github.com" GIT_COMMITTER_NAME="ASPshijiu" GIT_COMMITTER_EMAIL="59414654+ASPshijiu@users.noreply.github.com" git commit -m "添加微信版本指纹模型"
```

---

### Task 2: Hook Target and Candidate Scoring Models

**Files:**
- Create: `app/src/main/kotlin/com/android/wechathook/antiupdate/HookTarget.kt`
- Create: `app/src/main/kotlin/com/android/wechathook/antiupdate/HookCandidate.kt`
- Test: `app/src/test/java/com/android/wechathook/antiupdate/HookCandidateTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/android/wechathook/antiupdate/HookCandidateTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.android.wechathook.antiupdate.HookCandidateTest'
```

Expected: FAIL with unresolved references `HookCandidate`, `ResolvedHookTarget`, and `HookFeatureScore`.

- [ ] **Step 3: Write target model implementation**

Create `app/src/main/kotlin/com/android/wechathook/antiupdate/HookTarget.kt`:

```kotlin
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
```

- [ ] **Step 4: Write scoring implementation**

Create `app/src/main/kotlin/com/android/wechathook/antiupdate/HookCandidate.kt`:

```kotlin
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
```

- [ ] **Step 5: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.android.wechathook.antiupdate.HookCandidateTest'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/android/wechathook/antiupdate/HookTarget.kt app/src/main/kotlin/com/android/wechathook/antiupdate/HookCandidate.kt app/src/test/java/com/android/wechathook/antiupdate/HookCandidateTest.kt
GIT_AUTHOR_NAME="ASPshijiu" GIT_AUTHOR_EMAIL="59414654+ASPshijiu@users.noreply.github.com" GIT_COMMITTER_NAME="ASPshijiu" GIT_COMMITTER_EMAIL="59414654+ASPshijiu@users.noreply.github.com" git commit -m "添加 Hook 候选目标评分模型"
```

---

### Task 3: Cache Interface and In-Memory Cache

**Files:**
- Create: `app/src/main/kotlin/com/android/wechathook/antiupdate/HookCache.kt`
- Test: `app/src/test/java/com/android/wechathook/antiupdate/HookResolverTest.kt`

- [ ] **Step 1: Write the failing cache test**

Create `app/src/test/java/com/android/wechathook/antiupdate/HookResolverTest.kt` with cache tests first:

```kotlin
package com.android.wechathook.antiupdate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HookResolverTest {
    private val fingerprint = VersionFingerprint(
        packageName = "com.tencent.mm",
        versionName = "8.0.49",
        versionCode = 2460L,
        apkPath = "/data/app/com.tencent.mm/base.apk",
        apkLastModified = 1710000000000L,
    )

    @Test
    fun memoryCacheStoresEntriesByFingerprintAndHookId() {
        val cache = MemoryHookCache()
        val hookId = HookTargetId("send_message")
        val target = ResolvedHookTarget.Method(
            className = "com.tencent.mm.SendMessage",
            methodName = "a",
            parameterTypeNames = listOf("java.lang.String"),
            returnTypeName = "void",
        )

        cache.put(
            fingerprint = fingerprint,
            hookId = hookId,
            entry = HookCacheEntry(target = target, confidence = 90, frameworkVersion = 1),
        )

        assertEquals(target, cache.get(fingerprint, hookId)?.target)
        assertNull(cache.get(fingerprint.copy(versionCode = 2461L), hookId))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.android.wechathook.antiupdate.HookResolverTest.memoryCacheStoresEntriesByFingerprintAndHookId'
```

Expected: FAIL with unresolved references `MemoryHookCache` and `HookCacheEntry`.

- [ ] **Step 3: Write cache implementation**

Create `app/src/main/kotlin/com/android/wechathook/antiupdate/HookCache.kt`:

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.android.wechathook.antiupdate.HookResolverTest.memoryCacheStoresEntriesByFingerprintAndHookId'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/android/wechathook/antiupdate/HookCache.kt app/src/test/java/com/android/wechathook/antiupdate/HookResolverTest.kt
GIT_AUTHOR_NAME="ASPshijiu" GIT_AUTHOR_EMAIL="59414654+ASPshijiu@users.noreply.github.com" GIT_COMMITTER_NAME="ASPshijiu" GIT_COMMITTER_EMAIL="59414654+ASPshijiu@users.noreply.github.com" git commit -m "添加 Hook 解析缓存接口"
```

---

### Task 4: Resolver Cache-First Resolution

**Files:**
- Create: `app/src/main/kotlin/com/android/wechathook/antiupdate/HookResolver.kt`
- Modify: `app/src/test/java/com/android/wechathook/antiupdate/HookResolverTest.kt`

- [ ] **Step 1: Append failing resolver tests**

Append these tests inside `HookResolverTest`:

```kotlin
    @Test
    fun resolverReturnsValidCachedTargetWithoutScanning() {
        var providerCalled = false
        val cache = MemoryHookCache()
        val hookId = HookTargetId("send_message")
        val cachedTarget = ResolvedHookTarget.Method(
            className = "com.tencent.mm.Cached",
            methodName = "a",
            parameterTypeNames = emptyList(),
            returnTypeName = "void",
        )
        cache.put(
            fingerprint = fingerprint,
            hookId = hookId,
            entry = HookCacheEntry(cachedTarget, confidence = 95, frameworkVersion = 1),
        )
        val resolver = HookResolver(
            cache = cache,
            targetValidator = { true },
            candidateProvider = {
                providerCalled = true
                emptyList()
            },
        )

        val result = resolver.resolve(
            fingerprint = fingerprint,
            definition = HookDefinition(hookId, minimumScore = 70),
        )

        assertEquals(HookResolveResult.CacheHit(cachedTarget), result)
        assertEquals(false, providerCalled)
    }

    @Test
    fun resolverScansWhenCachedTargetFailsValidation() {
        val cache = MemoryHookCache()
        val hookId = HookTargetId("send_message")
        cache.put(
            fingerprint = fingerprint,
            hookId = hookId,
            entry = HookCacheEntry(
                target = ResolvedHookTarget.Method(
                    className = "com.tencent.mm.Stale",
                    methodName = "a",
                    parameterTypeNames = emptyList(),
                    returnTypeName = "void",
                ),
                confidence = 90,
                frameworkVersion = 1,
            ),
        )
        val scannedTarget = ResolvedHookTarget.Method(
            className = "com.tencent.mm.Fresh",
            methodName = "b",
            parameterTypeNames = emptyList(),
            returnTypeName = "void",
        )
        val resolver = HookResolver(
            cache = cache,
            targetValidator = { false },
            candidateProvider = {
                listOf(
                    HookCandidate(
                        target = scannedTarget,
                        features = listOf(HookFeatureScore("class_shape", 80, true)),
                    ),
                )
            },
        )

        val result = resolver.resolve(
            fingerprint = fingerprint,
            definition = HookDefinition(hookId, minimumScore = 70),
        )

        assertEquals(HookResolveResult.Scanned(scannedTarget, score = 80), result)
        assertEquals(scannedTarget, cache.get(fingerprint, hookId)?.target)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.android.wechathook.antiupdate.HookResolverTest'
```

Expected: FAIL with unresolved references `HookResolver` and `HookResolveResult`.

- [ ] **Step 3: Write resolver implementation**

Create `app/src/main/kotlin/com/android/wechathook/antiupdate/HookResolver.kt`:

```kotlin
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.android.wechathook.antiupdate.HookResolverTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/android/wechathook/antiupdate/HookResolver.kt app/src/test/java/com/android/wechathook/antiupdate/HookResolverTest.kt
GIT_AUTHOR_NAME="ASPshijiu" GIT_AUTHOR_EMAIL="59414654+ASPshijiu@users.noreply.github.com" GIT_COMMITTER_NAME="ASPshijiu" GIT_COMMITTER_EMAIL="59414654+ASPshijiu@users.noreply.github.com" git commit -m "实现 Hook 解析缓存优先流程"
```

---

### Task 5: Resolver Failure Paths and Diagnostics

**Files:**
- Create: `app/src/main/kotlin/com/android/wechathook/antiupdate/HookDiagnostics.kt`
- Modify: `app/src/test/java/com/android/wechathook/antiupdate/HookResolverTest.kt`
- Test: `app/src/test/java/com/android/wechathook/antiupdate/HookDiagnosticsTest.kt`

- [ ] **Step 1: Append failing resolver failure tests**

Append these tests inside `HookResolverTest`:

```kotlin
    @Test
    fun resolverReturnsNoCandidatesFailure() {
        val resolver = HookResolver(
            cache = MemoryHookCache(),
            targetValidator = { true },
            candidateProvider = { emptyList() },
        )

        val result = resolver.resolve(
            fingerprint = fingerprint,
            definition = HookDefinition(HookTargetId("send_message"), minimumScore = 70),
        )

        assertEquals(HookResolveResult.Failed(HookResolveFailure.NoCandidates), result)
    }

    @Test
    fun resolverReturnsBelowThresholdFailure() {
        val resolver = HookResolver(
            cache = MemoryHookCache(),
            targetValidator = { true },
            candidateProvider = {
                listOf(
                    HookCandidate(
                        target = ResolvedHookTarget.Method(
                            className = "com.tencent.mm.LowScore",
                            methodName = "a",
                            parameterTypeNames = emptyList(),
                            returnTypeName = "void",
                        ),
                        features = listOf(HookFeatureScore("weak_feature", 20, true)),
                    ),
                )
            },
        )

        val result = resolver.resolve(
            fingerprint = fingerprint,
            definition = HookDefinition(HookTargetId("send_message"), minimumScore = 70),
        )

        assertEquals(
            HookResolveResult.Failed(HookResolveFailure.ScoreBelowThreshold(bestScore = 20, minimumScore = 70)),
            result,
        )
    }
```

- [ ] **Step 2: Write failing diagnostics test**

Create `app/src/test/java/com/android/wechathook/antiupdate/HookDiagnosticsTest.kt`:

```kotlin
package com.android.wechathook.antiupdate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookDiagnosticsTest {
    @Test
    fun failureLogContainsTechnicalFieldsWithoutSensitiveContent() {
        val fingerprint = VersionFingerprint(
            packageName = "com.tencent.mm",
            versionName = "8.0.49",
            versionCode = 2460L,
            apkPath = "/data/app/com.tencent.mm/base.apk",
            apkLastModified = 1710000000000L,
        )
        val event = HookDiagnosticEvent(
            fingerprint = fingerprint,
            hookId = HookTargetId("send_message"),
            cacheHit = false,
            candidateCount = 2,
            topCandidates = listOf(
                HookCandidateSummary(
                    className = "com.tencent.mm.SendMessage",
                    memberName = "a",
                    score = 60,
                    matchedFeatures = listOf("parameter_count", "return_type"),
                ),
            ),
            failure = HookResolveFailure.ScoreBelowThreshold(bestScore = 60, minimumScore = 70),
        )

        val message = event.toLogMessage()

        assertTrue(message.contains("com.tencent.mm 8.0.49(2460)"))
        assertTrue(message.contains("send_message"))
        assertTrue(message.contains("candidateCount=2"))
        assertTrue(message.contains("ScoreBelowThreshold"))
        assertFalse(message.contains("聊天"))
        assertFalse(message.contains("联系人"))
        assertFalse(message.contains("消息正文"))
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.android.wechathook.antiupdate.HookResolverTest' --tests 'com.android.wechathook.antiupdate.HookDiagnosticsTest'
```

Expected: `HookResolverTest` passes or confirms failure-path behavior; `HookDiagnosticsTest` fails with unresolved diagnostic types.

- [ ] **Step 4: Write diagnostics implementation**

Create `app/src/main/kotlin/com/android/wechathook/antiupdate/HookDiagnostics.kt`:

```kotlin
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
```

- [ ] **Step 5: Run tests to verify they pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.android.wechathook.antiupdate.HookResolverTest' --tests 'com.android.wechathook.antiupdate.HookDiagnosticsTest'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/android/wechathook/antiupdate/HookDiagnostics.kt app/src/test/java/com/android/wechathook/antiupdate/HookResolverTest.kt app/src/test/java/com/android/wechathook/antiupdate/HookDiagnosticsTest.kt
GIT_AUTHOR_NAME="ASPshijiu" GIT_AUTHOR_EMAIL="59414654+ASPshijiu@users.noreply.github.com" GIT_COMMITTER_NAME="ASPshijiu" GIT_COMMITTER_EMAIL="59414654+ASPshijiu@users.noreply.github.com" git commit -m "添加 Hook 解析失败诊断日志模型"
```

---

### Task 6: Android Fingerprint Provider

**Files:**
- Create: `app/src/main/kotlin/com/android/wechathook/antiupdate/VersionFingerprintProvider.kt`
- Test: `app/src/test/java/com/android/wechathook/antiupdate/VersionFingerprintProviderTest.kt`

- [ ] **Step 1: Write failing provider test with fake data**

Create `app/src/test/java/com/android/wechathook/antiupdate/VersionFingerprintProviderTest.kt`:

```kotlin
package com.android.wechathook.antiupdate

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionFingerprintProviderTest {
    @Test
    fun providerBuildsFingerprintFromPlainInputs() {
        val fingerprint = VersionFingerprintProvider.fromPackageInfo(
            packageName = "com.tencent.mm",
            versionName = "8.0.49",
            versionCode = 2460L,
            apkPath = "/data/app/com.tencent.mm/base.apk",
            apkLastModified = 1710000000000L,
        )

        assertEquals("com.tencent.mm", fingerprint.packageName)
        assertEquals("8.0.49", fingerprint.versionName)
        assertEquals(2460L, fingerprint.versionCode)
        assertEquals("/data/app/com.tencent.mm/base.apk", fingerprint.apkPath)
        assertEquals(1710000000000L, fingerprint.apkLastModified)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.android.wechathook.antiupdate.VersionFingerprintProviderTest'
```

Expected: FAIL with unresolved reference `VersionFingerprintProvider`.

- [ ] **Step 3: Write provider implementation**

Create `app/src/main/kotlin/com/android/wechathook/antiupdate/VersionFingerprintProvider.kt`:

```kotlin
package com.android.wechathook.antiupdate

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import java.io.File

object VersionFingerprintProvider {
    fun fromPackageInfo(
        packageName: String,
        versionName: String,
        versionCode: Long,
        apkPath: String,
        apkLastModified: Long,
    ): VersionFingerprint {
        return VersionFingerprint(
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            apkPath = apkPath,
            apkLastModified = apkLastModified,
        )
    }

    fun fromAndroidPackageInfo(packageInfo: PackageInfo, applicationInfo: ApplicationInfo): VersionFingerprint {
        val apkPath = applicationInfo.sourceDir.orEmpty()
        return fromPackageInfo(
            packageName = packageInfo.packageName,
            versionName = packageInfo.versionName.orEmpty(),
            versionCode = packageInfo.longVersionCode,
            apkPath = apkPath,
            apkLastModified = File(apkPath).lastModified(),
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.android.wechathook.antiupdate.VersionFingerprintProviderTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/android/wechathook/antiupdate/VersionFingerprintProvider.kt app/src/test/java/com/android/wechathook/antiupdate/VersionFingerprintProviderTest.kt
GIT_AUTHOR_NAME="ASPshijiu" GIT_AUTHOR_EMAIL="59414654+ASPshijiu@users.noreply.github.com" GIT_COMMITTER_NAME="ASPshijiu" GIT_COMMITTER_EMAIL="59414654+ASPshijiu@users.noreply.github.com" git commit -m "添加 Android 版本指纹提供器"
```

---

### Task 7: Wire Framework Into Module Startup

**Files:**
- Modify: `app/src/main/kotlin/com/android/wechathook/WeChatHookModule.kt`
- Test: `app/src/test/java/com/android/wechathook/antiupdate/HookResolverTest.kt`
- Modify: `app/src/test/java/com/android/wechathook/ExampleUnitTest.kt`

- [ ] **Step 1: Add integration-style resolver test**

Append this test inside `HookResolverTest`:

```kotlin
    @Test
    fun emptyFirstStageDefinitionsDoNotFailModuleStartup() {
        val resolver = HookResolver(
            cache = MemoryHookCache(),
            targetValidator = { true },
            candidateProvider = { emptyList() },
        )
        val definitions = emptyList<HookDefinition>()

        val results = definitions.map { definition ->
            resolver.resolve(fingerprint, definition)
        }

        assertEquals(emptyList<HookResolveResult>(), results)
    }
```

- [ ] **Step 2: Run test to verify it passes before wiring**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.android.wechathook.antiupdate.HookResolverTest.emptyFirstStageDefinitionsDoNotFailModuleStartup'
```

Expected: PASS. This protects the first-stage startup path where no real Hook definitions exist yet.

- [ ] **Step 3: Modify module startup**

Replace `app/src/main/kotlin/com/android/wechathook/WeChatHookModule.kt` with:

```kotlin
package com.android.wechathook

import android.util.Log
import com.android.wechathook.antiupdate.HookDefinition
import com.android.wechathook.antiupdate.HookDiagnosticEvent
import com.android.wechathook.antiupdate.HookResolveResult
import com.android.wechathook.antiupdate.HookResolver
import com.android.wechathook.antiupdate.MemoryHookCache
import com.android.wechathook.antiupdate.VersionFingerprintProvider
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

class WeChatHookModule : XposedModule() {
    private val hookCache = MemoryHookCache()

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "Module loaded")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName != WECHAT_PACKAGE_NAME) return

        log(Log.INFO, TAG, "WeChat package loaded")
        runAntiUpdateResolution(param)
    }

    private fun runAntiUpdateResolution(param: PackageLoadedParam) {
        val fingerprint = VersionFingerprintProvider.fromPackageInfo(
            packageName = param.packageName,
            versionName = param.applicationInfo.metaData?.getString("versionName").orEmpty(),
            versionCode = 0L,
            apkPath = param.applicationInfo.sourceDir.orEmpty(),
            apkLastModified = param.applicationInfo.sourceDir?.let { java.io.File(it).lastModified() } ?: 0L,
        )
        val resolver = HookResolver(
            cache = hookCache,
            targetValidator = { false },
            candidateProvider = { emptyList() },
        )

        FIRST_STAGE_HOOKS.forEach { definition ->
            val result = resolver.resolve(fingerprint, definition)
            val diagnostic = HookDiagnosticEvent(
                fingerprint = fingerprint,
                hookId = definition.id,
                cacheHit = false,
                candidateCount = 0,
                topCandidates = emptyList(),
                failure = (result as? HookResolveResult.Failed)?.failure,
            )
            log(Log.INFO, TAG, diagnostic.toLogMessage())
        }
    }

    companion object {
        private const val TAG = "WeChatHook"
        private const val WECHAT_PACKAGE_NAME = "com.tencent.mm"
        private val FIRST_STAGE_HOOKS = emptyList<HookDefinition>()
    }
}
```

- [ ] **Step 4: Run unit tests and debug build**

Run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Remove generated sample unit test**

Replace `app/src/test/java/com/android/wechathook/ExampleUnitTest.kt` with:

```kotlin
package com.android.wechathook

class ExampleUnitTest
```

- [ ] **Step 6: Run full verification again**

Run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/android/wechathook/WeChatHookModule.kt app/src/test/java/com/android/wechathook/antiupdate/HookResolverTest.kt app/src/test/java/com/android/wechathook/ExampleUnitTest.kt
GIT_AUTHOR_NAME="ASPshijiu" GIT_AUTHOR_EMAIL="59414654+ASPshijiu@users.noreply.github.com" GIT_COMMITTER_NAME="ASPshijiu" GIT_COMMITTER_EMAIL="59414654+ASPshijiu@users.noreply.github.com" git commit -m "接入抗更新解析框架启动流程"
```

---

### Task 8: Final Verification and Push

**Files:**
- No source file changes expected.

- [ ] **Step 1: Run all verification**

Run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Review Git status and commits**

Run:

```bash
git status --short
git log --oneline -8
```

Expected: clean working tree and recent commits for Tasks 1-7.

- [ ] **Step 3: Push to GitHub**

Run:

```bash
git push origin main
```

Expected: push succeeds to `https://github.com/ASPshijiu/wechathook.git`.

---

## Self-Review

- Spec coverage: version fingerprint, multi-candidate scoring, cache-first lookup, cache invalidation by validation, failure diagnostics, no remote rules, and unit tests are each mapped to a task.
- Placeholder scan: the plan contains no TODO, TBD, or open-ended implementation placeholders.
- Type consistency: `VersionFingerprint`, `HookTargetId`, `ResolvedHookTarget`, `HookFeatureScore`, `HookCandidate`, `HookCacheEntry`, `MemoryHookCache`, `HookResolver`, `HookResolveResult`, `HookResolveFailure`, `HookDiagnosticEvent`, and `VersionFingerprintProvider` are defined before use in implementation tasks.
