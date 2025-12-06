package space.davids_digital.grateki.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import space.davids_digital.grateki.FixtureUtil.prepareFixtureProject
import space.davids_digital.grateki.batching.GreedyTimeWeightedClassLevelBatchingStrategy
import space.davids_digital.grateki.exec.InitScriptProvider
import space.davids_digital.grateki.exec.ToolingApiGradleTestExecutor
import space.davids_digital.grateki.history.JsonFileHistoryStore
import space.davids_digital.grateki.model.RunConfig
import space.davids_digital.grateki.model.TestKey
import space.davids_digital.grateki.model.TestRunInfo
import space.davids_digital.grateki.model.TestStatus
import java.nio.file.Path
import kotlin.io.path.createDirectories

class GratekiRunnerIntegrationTest {
    @Test
    fun `executes batches end-to-end and updates history`(@TempDir tempDir: Path) {
        val projectDir = prepareFixtureProject("f01", tempDir)

        val historyPath = tempDir.resolve("history.json")
        val historyStore = JsonFileHistoryStore(historyPath)
        val testKey1 = TestKey(":", "com.example.f01.AlphaTest", "alphaFirst")
        val testKey2 = TestKey(":", "com.example.f01.BetaTest", "betaFirst")
        val seedHistory = mapOf(
            testKey1 to listOf(
                TestRunInfo(
                    testKey = testKey1,
                    buildId = "seed",
                    durationMs = 15,
                    status = TestStatus.PASSED,
                    finishedAt = 1
                )
            ),
            testKey2 to listOf(
                TestRunInfo(
                    testKey = testKey2,
                    buildId = "seed",
                    durationMs = 25,
                    status = TestStatus.PASSED,
                    finishedAt = 2
                )
            )
        )
        historyStore.replace(seedHistory)

        val runner = GratekiRunner(
            historyStore = historyStore,
            batching = GreedyTimeWeightedClassLevelBatchingStrategy(),
            gradleExecutor = ToolingApiGradleTestExecutor(),
            initScriptProvider = InitScriptProvider()
        )

        val gratekiHome = tempDir.resolve("grateki-home").also { it.createDirectories() }
        val config = RunConfig(
            projectPath = projectDir,
            gratekiHome = gratekiHome,
            tasks = emptyList(),
            workers = 2,
            timeout = null
        )

        val result = runner.run(config)

        assert(result.success)
        assertEquals(2, result.workerResults.size)
        val allRuns = result.workerResults.flatMap { it.tests }
        assertEquals(5, allRuns.size)

        val executedKeys = allRuns.map { it.testKey }.toSet()
        val nonSkippedKeys = allRuns.filter { it.status != TestStatus.SKIPPED }.map { it.testKey }.toSet()

        val reloadedEntries = run {
            historyStore.load()
            historyStore.getAll()
        }

        nonSkippedKeys.forEach { key ->
            assert(reloadedEntries.containsKey(key))
            val history = reloadedEntries[key]!!
            assert(history.isNotEmpty())
            assertEquals(TestStatus.PASSED, history.last().status)
        }

        executedKeys.filter { it !in nonSkippedKeys }.forEach { key ->
            assert(reloadedEntries[key].isNullOrEmpty())
        }
    }
}