package space.davids_digital.grateki.exec

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.test.*
import space.davids_digital.grateki.exec.event.TestEvent
import space.davids_digital.grateki.exec.event.TestEventHandler
import space.davids_digital.grateki.gradle.GradleEventUtils
import space.davids_digital.grateki.model.*
import java.io.PrintStream
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

class ToolingApiGradleTestExecutor : GradleTestExecutor {
    override fun run(request: GradleWorkerRequest, eventHandler: TestEventHandler?): GradleWorkerResult {
        val buildId = UUID.randomUUID().toString()
        val collectedTests = CopyOnWriteArrayList<TestRunInfo>()

        return try {
            // Connect to the Gradle project
            val connection = GradleConnector.newConnector()
                .forProjectDirectory(request.projectPath.toFile())
                .useBuildDistribution()
                .connect()

            connection.use { connection ->
                val build = connection.newBuild()
                var logStream: PrintStream? = null
                try {
                    build.forTasks(*request.tasks.toTypedArray())
                    build.withArguments(
                        buildList {
                            request.initScriptPath?.let {
                                addAll(listOf("--init-script", it.toString()))
                            }
                            addAll(request.gradleArgs)
                        }
                    )

                    if (request.gradleLogPath != null) {
                        logStream = PrintStream(request.gradleLogPath.toFile())
                        build.setStandardOutput(logStream)
                        build.setStandardError(logStream)
                    }

                    build.addProgressListener({ event ->
                        onBuildStatusChanged(event, collectedTests, buildId, eventHandler)
                    }, OperationType.TEST)

                    if (request.systemProperties.isNotEmpty()) {
                        build.setJvmArguments(request.jvmArgs + request.systemProperties.map { (k, v) -> "-D$k=$v" })
                    } else if (request.jvmArgs.isNotEmpty()) {
                        build.setJvmArguments(request.jvmArgs)
                    }

                    build.run()
                } finally {
                    logStream?.close()
                }
            }

            GradleWorkerResult(
                workerId = request.id,
                tests = collectedTests.toList(),
                success = true,
            )
        } catch (t: Throwable) {
            GradleWorkerResult(
                workerId = request.id,
                tests = collectedTests.toList(),
                success = false,
                throwable = t,
            )
        }
    }

    private fun onBuildStatusChanged(
        event: ProgressEvent,
        collectedTests: MutableList<TestRunInfo>,
        buildId: String,
        eventHandler: TestEventHandler?
    ) {
        val descriptor = event.descriptor
        val jvmDescriptor = descriptor as? JvmTestOperationDescriptor ?: return
        if (jvmDescriptor.methodName == null) {
            return
        }
        if (jvmDescriptor.jvmTestKind != JvmTestKind.ATOMIC) return
        val testKey = TestKey(
            gradlePath = GradleEventUtils.resolveGradleModulePath(jvmDescriptor),
            className = jvmDescriptor.className ?: "<unknown>",
            testName = jvmDescriptor.methodName ?: jvmDescriptor.displayName ?: "<unknown>"
        )

        when (event) {
            is TestStartEvent -> {
                val event = TestEvent.TestStartEvent(testKey)
                eventHandler?.handle(event)
            }
            is TestFinishEvent -> {
                val result = event.result
                val status = when (result) {
                    is TestSuccessResult -> TestStatus.PASSED
                    is TestFailureResult -> TestStatus.FAILED
                    is TestSkippedResult -> TestStatus.SKIPPED
                    else -> TestStatus.FAILED
                }

                val durationMs = result.endTime - result.startTime

                val runInfo = TestRunInfo(
                    testKey = testKey,
                    status = status,
                    durationMs = durationMs,
                    buildId = buildId,
                    finishedAt = result.endTime
                )

                collectedTests += runInfo

                val event = TestEvent.TestFinishEvent(testKey, runInfo)
                eventHandler?.handle(event)
            }
            else -> {
                // Cricket sounds...
            }
        }
    }
}