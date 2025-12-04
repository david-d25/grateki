package com.davids_digital.grateki.service

import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import space.davids_digital.grateki.batching.BatchingStrategy
import space.davids_digital.grateki.exec.GradleTestExecutor
import space.davids_digital.grateki.exec.InitScriptProvider
import space.davids_digital.grateki.history.HistoryStore
import space.davids_digital.grateki.model.*
import space.davids_digital.grateki.service.GratekiRunner
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createDirectories

class GratekiRunnerTest {

    @Test
    fun `run uses default task, passes history to batching and updates history`(@TempDir tempDir: Path) {
        val historyStore = mockk<HistoryStore>()
        val batching = mockk<BatchingStrategy>()
        val gradleExecutor = mockk<GradleTestExecutor>()
        val initScriptProvider = mockk<InitScriptProvider>()

        val runner = GratekiRunner(historyStore, batching, gradleExecutor, initScriptProvider)

        val existingKey = TestKey(":app", "com.ExistingTest", "testOld")
        val existingRun = TestRunInfo(
            testKey = existingKey,
            buildId = "old",
            durationMs = 100,
            status = TestStatus.PASSED,
            finishedAt = 1L
        )

        val existingHistory = mapOf(existingKey to listOf(existingRun))

        every { historyStore.getAll() } returns existingHistory

        val newTestKey = TestKey(":app", "com.NewTest", "testNew")
        val batch = TestBatch(tests = listOf(newTestKey), totalEstimatedDurationMillis = 123L)
        every { batching.createBatches(existingHistory, 2) } returns listOf(batch)

        val includeScript = tempDir.resolve("include.gradle.kts")
        val excludeScript = tempDir.resolve("exclude.gradle.kts")
        every { initScriptProvider.getInclude() } returns includeScript
        every { initScriptProvider.getExclude() } returns excludeScript

        val newRun = TestRunInfo(
            testKey = newTestKey,
            buildId = "new",
            durationMs = 200,
            status = TestStatus.PASSED,
            finishedAt = 2L
        )
        every { gradleExecutor.run(any(), any()) } returns GradleWorkerResult(
            workerId = 0,
            tests = listOf(newRun),
            success = true,
            throwable = null
        )

        val replacedHistorySlot: CapturingSlot<Map<TestKey, List<TestRunInfo>>> = slot()
        every { historyStore.replace(capture(replacedHistorySlot)) } returns Unit

        val gratekiHome = tempDir.resolve("grateki-home").also { it.createDirectories() }
        val config = RunConfig(
            projectPath = tempDir,
            gratekiHome = gratekiHome,
            tasks = emptyList(),
            workers = 2,
            timeout = null
        )

        val result = runner.run(config, eventHandler = null)

        verify(exactly = 1) { batching.createBatches(existingHistory, 2) }
        verify(atLeast = 1) { gradleExecutor.run(any(), any()) }
        assertEquals(1, result.workerResults.size)
        assertEquals(listOf(newRun), result.workerResults[0].tests)
        verify(exactly = 1) { historyStore.replace(any()) }

        val mergedHistory = replacedHistorySlot.captured
        assertEquals(setOf(existingKey, newTestKey), mergedHistory.keys)
        assertEquals(listOf(existingRun), mergedHistory[existingKey])
        assertEquals(listOf(newRun), mergedHistory[newTestKey])
    }

    @Test
    fun `updateHistory skips SKIPPED and respects history size limit`(@TempDir tempDir: Path) {
        val historyStore = mockk<HistoryStore>()
        val batching = mockk<BatchingStrategy>()
        val gradleExecutor = mockk<GradleTestExecutor>()
        val initScriptProvider = mockk<InitScriptProvider>()

        val runner = GratekiRunner(historyStore, batching, gradleExecutor, initScriptProvider)

        val key = TestKey(":app", "com.ExampleTest", "testMethod")

        val existingRuns = listOf(
            TestRunInfo(key, "b1", 10, TestStatus.PASSED, 1),
            TestRunInfo(key, "b2", 20, TestStatus.FAILED, 2),
            TestRunInfo(key, "b3", 30, TestStatus.SKIPPED, 3), // this should be skipped (like, literally)
        )
        every { historyStore.getAll() } returns mapOf(key to existingRuns)

        every { batching.createBatches(any(), any()) } returns emptyList()

        val newRuns = (1..6).map { idx ->
            TestRunInfo(
                testKey = key,
                buildId = "new$idx",
                durationMs = 100L + idx,
                status = if (idx == 3) TestStatus.SKIPPED else TestStatus.PASSED,
                finishedAt = 1000L + idx
            )
        }
        every { gradleExecutor.run(any(), any()) } returns GradleWorkerResult(
            workerId = 0,
            tests = newRuns,
            success = true,
            throwable = null
        )

        val replacedHistorySlot = slot<Map<TestKey, List<TestRunInfo>>>()
        every { historyStore.replace(capture(replacedHistorySlot)) } returns Unit

        val gratekiHome = tempDir.resolve("grateki-home").also { it.createDirectories() }
        val config = RunConfig(
            projectPath = tempDir,
            gratekiHome = gratekiHome,
            tasks = listOf("test"),
            workers = 1,
            timeout = null
        )

        val includeScript = tempDir.resolve("include.gradle.kts")
        val excludeScript = tempDir.resolve("exclude.gradle.kts")
        every { initScriptProvider.getInclude() } returns includeScript
        every { initScriptProvider.getExclude() } returns excludeScript

        // when
        runner.run(config)

        // then
        verify(exactly = 1) { historyStore.replace(any()) }

        val merged = replacedHistorySlot.captured
        val mergedRuns = merged[key]!!

        mergedRuns.forEach { run ->
            assert(run.status != TestStatus.SKIPPED)
        }

        assertEquals(GratekiRunner.MAX_TEST_HISTORY_ENTRIES, mergedRuns.size)

        // and this should be last N non-skipped runs from existing + new
        val expectedNonSkipped = (existingRuns.filter { it.status != TestStatus.SKIPPED } +
                newRuns.filter { it.status != TestStatus.SKIPPED })
            .takeLast(GratekiRunner.MAX_TEST_HISTORY_ENTRIES)

        assertEquals(expectedNonSkipped, mergedRuns)
    }

    @Test
    fun `executeRequests runs fallback when worker fails before tests`(@TempDir tempDir: Path) {
        val historyStore = mockk<HistoryStore>()
        val batching = mockk<BatchingStrategy>()
        val gradleExecutor = mockk<GradleTestExecutor>()
        val initScriptProvider = mockk<InitScriptProvider>()

        val runner = GratekiRunner(historyStore, batching, gradleExecutor, initScriptProvider)

        every { historyStore.getAll() } returns emptyMap()
        every { historyStore.replace(any()) } returns Unit

        // Two batches so we have two workers
        val key1 = TestKey(":app", "com.A", "t1")
        val key2 = TestKey(":app", "com.B", "t2")
        val batch1 = TestBatch(listOf(key1), 100)
        val batch2 = TestBatch(listOf(key2), 200)
        every { batching.createBatches(any(), 2) } returns listOf(batch1, batch2)

        val includeScript = tempDir.resolve("include.gradle.kts")
        val excludeScript = tempDir.resolve("exclude.gradle.kts")
        every { initScriptProvider.getInclude() } returns includeScript
        every { initScriptProvider.getExclude() } returns excludeScript

        // Executor behavior:
        // - two "normal" workers (id 0 and 1), one is pure infra failure (no tests), second is normal (with tests)
        // - fallback (id 2) successful result
        every { gradleExecutor.run(any(), any()) } answers { call ->
            val req = call.invocation.args[0] as GradleWorkerRequest
            when (req.id) {
                0 -> GradleWorkerResult(
                    workerId = 0,
                    tests = emptyList(),
                    success = false,
                    throwable = RuntimeException("infra failure")
                )
                1 -> GradleWorkerResult(
                    workerId = 1,
                    tests = listOf(
                        TestRunInfo(key2, "b1", 50, TestStatus.PASSED, 10)
                    ),
                    success = true,
                    throwable = null
                )
                2 -> GradleWorkerResult(
                    workerId = 2,
                    tests = listOf(
                        TestRunInfo(key1, "b2", 70, TestStatus.PASSED, 20)
                    ),
                    success = true,
                    throwable = null
                )
                else -> error("Unexpected worker id ${req.id}")
            }
        }

        val gratekiHome = tempDir.resolve("grateki-home").also { it.createDirectories() }
        val config = RunConfig(
            projectPath = tempDir,
            gratekiHome = gratekiHome,
            tasks = listOf("testA", "testB"),
            workers = 2,
            timeout = Duration.ofSeconds(30)
        )

        val result = runner.run(config)

        // Expecting 3 results (2 batches + 1 fallback)
        assertEquals(3, result.workerResults.size)

        // verify fallback call
        verify { gradleExecutor.run(match { it.id == 2 && "gradle-fallback.log" in it.gradleLogPath.toString() }, any()) }

        // Check that fallback call tasks is a union of source tasks
        verify {
            gradleExecutor.run(match {
                it.id == 2 &&
                        it.tasks.toSet() == setOf("testA", "testB")
            }, any())
        }
    }
}
