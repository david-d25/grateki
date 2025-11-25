package space.davids_digital.grateki.command

import picocli.CommandLine.Command
import picocli.CommandLine.Option
import space.davids_digital.grateki.service.GratekiRunner
import space.davids_digital.grateki.batching.GreedyTimeWeightedClassLevelBatchingStrategy
import space.davids_digital.grateki.exec.InitScriptProvider
import space.davids_digital.grateki.exec.ToolingApiGradleTestExecutor
import space.davids_digital.grateki.exec.event.TestEvent
import space.davids_digital.grateki.history.JsonFileHistoryStore
import space.davids_digital.grateki.model.RunConfig
import space.davids_digital.grateki.model.RunResult
import space.davids_digital.grateki.model.TestRunInfo
import space.davids_digital.grateki.model.TestStatus
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Callable
import kotlin.io.path.isRegularFile
import kotlin.io.path.notExists

@Command(
    name = "grateki",
    mixinStandardHelpOptions = true,
    description = ["Framework-agnostic parallel test execution for Gradle projects"]
)
class GratekiCommand : Callable<Int> {
    @Option(names = ["--project", "-p"], description = ["The path to the project to run"])
    var projectPath: Path = Path.of(".")

    @Option(
        names = ["--tasks", "-T"],
        split = ",",
        description = ["The list of Gradle test tasks to run (comma-separated)"]
    )
    var tasks : List<String> = emptyList()

    @Option(names = ["--workers", "-w"], description = ["The number of parallel workers to use"])
    var workers: Int = Runtime.getRuntime().availableProcessors()

    @Option(
        names = ["--home", "-H"],
        description = ["The path to the folder to use for Grateki home (logs, history, etc.)"]
    )
    var homePath: Path? = null

    @Option(names = ["--timeout", "-t"], description = ["Workers timeout, disabled by default"])
    var timeout: Duration? = null

    @Option(names = ["--disable-foolproofness"], description = ["Disable foolproofness checks (not recommended)"])
    var disableFoolproofness: Boolean = false

    override fun call(): Int {
        val effectiveProjectPath = projectPath.toAbsolutePath().normalize()
        if (effectiveProjectPath.notExists()) {
            println("Couldn't find project path $effectiveProjectPath")
            return 1
        }
        println("Working on project at $effectiveProjectPath")

        val defaultHomePath = effectiveProjectPath.resolve(".gradle/grateki")
        val effectiveHomePath = (homePath ?: defaultHomePath).toAbsolutePath().normalize()
        println("Using Grateki home at $effectiveHomePath")

        if (effectiveHomePath.isRegularFile()) {
            println("Grateki home path $effectiveHomePath is a file, " +
                    "please remove it or specify a different home path using --home")
            return 1
        }

        if (effectiveHomePath.notExists()) {
            println("Creating Grateki home directory at $effectiveHomePath")
            effectiveHomePath.toFile().mkdirs()
        }

        val historyPath = effectiveHomePath.resolve("history.json")
        println("Using test history file at $historyPath")

        // Load history
        val historyStore = JsonFileHistoryStore(historyPath)
        try {
            val entriesLoaded = historyStore.load()
            println("Loaded $entriesLoaded test history entries")
        } catch (_: Exception) {
            println("Couldn't load history, will start fresh")
        }

        // Determine how many workers to use
        val maxWorkers = Runtime.getRuntime().availableProcessors() * 16 // Arbitrary limit to prevent insanity
        if (!disableFoolproofness && workers > maxWorkers) {
            println("Requested workers ($workers) exceeds maximum allowed ($maxWorkers). " +
                    "Use --disable-foolproofness to override.")
            return 1
        }
        val workersSafe = workers.coerceIn(1..maxWorkers)
        val workersEffective = if (disableFoolproofness) workers else workersSafe

        printIntro()

        // The guy who does the actual work
        val runner = GratekiRunner(
            historyStore = historyStore,
            batching = GreedyTimeWeightedClassLevelBatchingStrategy(),
            gradleExecutor = ToolingApiGradleTestExecutor(),
            initScriptProvider = InitScriptProvider()
        )
        val config = RunConfig(
            projectPath = effectiveProjectPath,
            tasks = tasks,
            workers = workersEffective,
            gratekiHome = effectiveHomePath,
            timeout = timeout
        )
        val result = runner.run(config, ::handleTestEvent)
        printResult(result)
        return if (result.success) 0 else 1
    }

    private fun handleTestEvent(event: TestEvent) {
        if (event is TestEvent.TestFinishEvent) {
             printTestFinishLine(event.runInfo)
        }
    }

    private fun printTestFinishLine(testRunInfo: TestRunInfo) {
        val statusText = when (testRunInfo.status) {
            TestStatus.PASSED ->  "PASSED "
            TestStatus.FAILED ->  "FAILED "
            TestStatus.SKIPPED -> "SKIPPED"
        }
        val className = testRunInfo.testKey.className
        val testName = testRunInfo.testKey.testName
        println("$statusText $className#$testName in ${testRunInfo.durationMs} ms")
    }

    private fun printResult(result: RunResult) {
        val totalTests = result.workerResults.sumOf { it.tests.size }
        val testCountByStatus = result.workerResults
            .flatMap { it.tests }
            .groupingBy { it.status }
            .eachCount()
        val totalFailures = testCountByStatus.getOrDefault(TestStatus.FAILED, 0)
        val totalSkipped = testCountByStatus.getOrDefault(TestStatus.SKIPPED, 0)
        val totalPassed = testCountByStatus.getOrDefault(TestStatus.PASSED, 0)
        println("Test run complete: $totalTests tests, " +
                "$totalPassed passed, " +
                "$totalFailures failed, " +
                "$totalSkipped skipped.")
    }

    private fun printIntro() {
        println("Gradle Test Kittens ready! =^._.^= ")
    }
}