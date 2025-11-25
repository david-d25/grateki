package space.davids_digital.grateki.service

import space.davids_digital.grateki.batching.BatchingStrategy
import space.davids_digital.grateki.exec.GradleTestExecutor
import space.davids_digital.grateki.exec.InitScriptProvider
import space.davids_digital.grateki.exec.event.TestEventHandler
import space.davids_digital.grateki.history.HistoryStore
import space.davids_digital.grateki.model.GradleWorkerRequest
import space.davids_digital.grateki.model.GradleWorkerResult
import space.davids_digital.grateki.model.RunConfig
import space.davids_digital.grateki.model.RunResult
import space.davids_digital.grateki.model.TestKey
import space.davids_digital.grateki.model.TestRunInfo
import space.davids_digital.grateki.model.TestStatus
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * An orchestrator for running Gradle tests in parallel workers.
 */
class GratekiRunner (
    private val historyStore: HistoryStore,
    private val batching: BatchingStrategy,
    private val gradleExecutor: GradleTestExecutor,
    private val initScriptProvider: InitScriptProvider
) {
    companion object {
        /**
         * The maximum number of test history entries to keep per test.
         * This limits the size of the history and ensures that historical data remains relevant.
         * The number 5 came to me in a dream.
         */
        const val MAX_TEST_HISTORY_ENTRIES = 5

        /**
         * The default Gradle test task to run when no tasks are specified.
         */
        const val DEFAULT_TEST_TASK = "test"
    }

    fun run(config: RunConfig, eventHandler: TestEventHandler? = null): RunResult {
        val runId = System.currentTimeMillis()
        val effectiveTasks = config.tasks.ifEmpty { listOf(DEFAULT_TEST_TASK) }
        val history = historyStore.getAll()
        val batches = batching.createBatches(history, config.workers)
        val requests = mutableListOf<GradleWorkerRequest>()
        // Last worker will run all test not run by other workers to also cover new tests
        val lastWorkerExclusionList = mutableSetOf<String>()
        val currentRunDir = config.gratekiHome.toFile().resolve("runs").resolve("run-$runId")
        val logsDir = currentRunDir.resolve("logs")
        val debugDir = currentRunDir.resolve("debug")
        currentRunDir.mkdirs()
        logsDir.mkdirs()
        debugDir.mkdirs()
        // Take all batches except the last one
        for (index in 0 ..< batches.lastIndex) {
            val batch = batches[index]
            // What test classes to run
            val classes = batch.tests.map { it.className }.distinct()
            lastWorkerExclusionList += classes

            val gradleLogPath = logsDir.resolve("gradle-$index.log").toPath()

            // Init script will read this file to know what test classes to run
            val testsFile = Files.createTempFile("grateki-tests-${index}-incl-", ".txt")
            testsFile.toFile().printWriter().use { out ->
                classes.forEach(out::println)
            }

            val testsDebugFile = debugDir.resolve("tests-$index-incl.txt")
            testsFile.toFile().copyTo(testsDebugFile)

            val request = GradleWorkerRequest(
                id = index,
                projectPath = config.projectPath,
                tasks = effectiveTasks,
                initScriptPath = initScriptProvider.getInclude(),
                systemProperties = mapOf("grateki.testsToRunFile" to testsFile.toAbsolutePath().toString()),
                gradleLogPath = gradleLogPath
            )
            requests.add(request)
        }

        val lastWorkerIndex = batches.lastIndex.coerceAtLeast(0)
        val lastWorkerTestsFile = Files.createTempFile("grateki-tests-${lastWorkerIndex}-excl-", ".txt")
        lastWorkerTestsFile.toFile().printWriter().use { out ->
            lastWorkerExclusionList.forEach(out::println)
        }
        val lastTestsDebugFile = debugDir.resolve("tests-$lastWorkerIndex-excl.txt")
        lastWorkerTestsFile.toFile().copyTo(lastTestsDebugFile)
        val lastGradleLogPath = logsDir.resolve("gradle-$lastWorkerIndex.log").toPath()

        val lastWorkerRequest = GradleWorkerRequest(
            id = lastWorkerIndex,
            projectPath = config.projectPath,
            tasks = effectiveTasks,
            initScriptPath = initScriptProvider.getExclude(),
            systemProperties = mapOf("grateki.testsToExcludeFile" to lastWorkerTestsFile.toAbsolutePath().toString()),
            gradleLogPath = lastGradleLogPath
        )
        requests.add(lastWorkerRequest)

        val executorService = Executors.newFixedThreadPool(requests.size)

        try {
            val futures = requests.map { req ->
                executorService.submit<GradleWorkerResult> {
                    gradleExecutor.run(req, eventHandler)
                }
            }
            val results = futures.mapIndexed { index, future ->
                try {
                    if (config.timeout == null) {
                        future.get()
                    } else {
                        future.get(config.timeout.toMillis(), TimeUnit.MILLISECONDS)
                    }
                } catch (e: TimeoutException) {
                    future.cancel(true)
                    GradleWorkerResult(
                        workerId = requests[index].id,
                        tests = emptyList(),
                        success = false,
                        throwable = e
                    )
                }
            }
            val allRuns: List<TestRunInfo> = results.flatMap { it.tests }
            updateHistory(history, allRuns)
            return RunResult(results)
        } finally {
            executorService.shutdownNow()
        }
    }

    /**
     * Updates the test history by merging old and new test run information while ensuring the history size limit is
     * respected. Implementing this was surprisingly easier than I expected.
     */
    private fun updateHistory(oldRunsByKey: Map<TestKey, List<TestRunInfo>>, newRuns: List<TestRunInfo>) {
        val newRunsByKey = newRuns.groupBy { it.testKey }
        val allKeys = newRunsByKey.keys + oldRunsByKey.keys

        val merged = allKeys.associateWith { key ->
            val oldList = oldRunsByKey[key].orEmpty().filter { it.status != TestStatus.SKIPPED }
            val newList = newRunsByKey[key].orEmpty().filter { it.status != TestStatus.SKIPPED }
            (oldList + newList).takeLast(MAX_TEST_HISTORY_ENTRIES)
        }

        historyStore.replace(merged)
    }
}