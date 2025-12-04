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
import space.davids_digital.grateki.model.TestBatch
import space.davids_digital.grateki.model.TestKey
import space.davids_digital.grateki.model.TestRunInfo
import space.davids_digital.grateki.model.TestStatus
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.collections.flatMap
import kotlin.collections.plus
import kotlin.io.path.copyTo

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
        val currentRunDir = config.gratekiHome.resolve("runs").resolve("run-$runId")
        val logsDir = currentRunDir.resolve("logs")
        val debugDir = currentRunDir.resolve("debug")
        currentRunDir.toFile().mkdirs()
        logsDir.toFile().mkdirs()
        debugDir.toFile().mkdirs()

        val tasks = config.tasks.ifEmpty { listOf(DEFAULT_TEST_TASK) }
        val history = historyStore.getAll()
        val batches = batching.createBatches(history, config.workers)
        val requests = createRequests(batches, config.projectPath, tasks, logsDir, debugDir)
        val result = executeRequests(requests, config, eventHandler, logsDir)
        val allRuns: List<TestRunInfo> = result.workerResults.flatMap { it.tests }
        updateHistory(history, allRuns)
        return result
    }

    private fun createRequests(
        batches: List<TestBatch>,
        projectPath: Path,
        tasks: List<String>,
        logsDir: Path,
        debugDir: Path
    ): List<GradleWorkerRequest> {
        val lastWorkerExclusionList = mutableSetOf<String>()
        val requests = mutableListOf<GradleWorkerRequest>()
        // Take all batches except the last one
        for (index in 0 ..< batches.lastIndex) {
            val batch = batches[index]
            val classes = batch.tests.map { it.className }.distinct()
            requests.add(
                createGradleWorkerRequest(index, projectPath, tasks, classes, logsDir, debugDir)
            )
            lastWorkerExclusionList += classes
        }
        // Last worker will run all test not run by other workers to also cover new tests
        val lastWorkerIndex = batches.lastIndex.coerceAtLeast(0)
        requests.add(
            createGradleWorkerRequest(
                lastWorkerIndex,
                projectPath,
                tasks,
                lastWorkerExclusionList.toList(),
                logsDir,
                debugDir,
                exclusionMode = true
            )
        )
        return requests
    }

    private fun executeRequests(
        requests: List<GradleWorkerRequest>,
        config: RunConfig,
        eventHandler: TestEventHandler? = null,
        logsDir: Path
    ): RunResult {
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
            // Fallback
            val infraFailures = results.filter { !it.success && it.tests.isEmpty() }
            if (infraFailures.isNotEmpty()) {
                println("${infraFailures.size} worker(s) failed before running tests, running unbatched fallback")
                val fallbackRequest = GradleWorkerRequest(
                    id = requests.size,
                    projectPath = config.projectPath,
                    tasks = requests.flatMap { it.tasks }.distinct(),
                    gradleLogPath = logsDir.resolve("gradle-fallback.log")
                )
                val fallbackResult = gradleExecutor.run(fallbackRequest, eventHandler)
                return RunResult(results + fallbackResult)
            }
            return RunResult(results)
        } finally {
            executorService.shutdownNow()
        }
    }

    private fun createGradleWorkerRequest(
        id: Int,
        projectPath: Path,
        tasks: List<String>,
        classes: List<String>,
        logsDir: Path,
        debugDir: Path,
        exclusionMode: Boolean = false,
    ): GradleWorkerRequest {
        val logPath = logsDir.resolve("gradle-$id.log")
        val initScript = if (exclusionMode) initScriptProvider.getExclude() else initScriptProvider.getInclude()
        val fileSpecifier = if (exclusionMode) "excl" else "incl"
        val tempFilePrefix = "grateki-tests-$id-$fileSpecifier-"
        val tempFilePostfix = ".txt"

        val testsFile = Files.createTempFile(tempFilePrefix, tempFilePostfix)
        testsFile.toFile().printWriter().use { out ->
            classes.forEach(out::println)
        }

        val testsDebugFile = debugDir.resolve("tests-$id-$fileSpecifier.txt")
        testsFile.copyTo(testsDebugFile)

        return GradleWorkerRequest(
            id = id,
            projectPath = projectPath,
            tasks = tasks,
            initScriptPath = initScript,
            systemProperties = mapOf(
                "grateki.testClassesFile" to testsFile.toAbsolutePath().toString(),
                "grateki.workerId" to id.toString()
            ),
            gradleLogPath = logPath
        )
    }

    /**
     * Updates the test history by merging old and new test run information while ensuring the history size limit is
     * respected. Implementing this was surprisingly easier than I expected.
     */
    private fun updateHistory(
        existingHistory: Map<TestKey, List<TestRunInfo>>,
        newRuns: List<TestRunInfo>
    ) {
        val newRunsByKey = newRuns.groupBy { it.testKey }
        val allKeys = newRunsByKey.keys + existingHistory.keys

        val merged = allKeys.associateWith { key ->
            val existingRuns = existingHistory[key].orEmpty().filter { it.status != TestStatus.SKIPPED }
            val newRuns = newRunsByKey[key].orEmpty().filter { it.status != TestStatus.SKIPPED }
            (existingRuns + newRuns).takeLast(MAX_TEST_HISTORY_ENTRIES)
        }

        historyStore.replace(merged)
    }
}