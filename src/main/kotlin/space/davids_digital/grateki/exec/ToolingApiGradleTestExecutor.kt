package space.davids_digital.grateki.exec

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.test.*
import space.davids_digital.grateki.model.*
import java.io.PrintStream
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

class ToolingApiGradleTestExecutor : GradleTestExecutor {
    override fun run(request: GradleWorkerRequest): GradleWorkerResult {
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
                        onBuildStatusChanged(event, collectedTests, buildId)
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
    ) {
        when (event) {
            is TestStartEvent -> {
                // Make live progress later
            }
            is TestFinishEvent -> {
                val descriptor = event.descriptor

                val jvmDescriptor = descriptor as? JvmTestOperationDescriptor ?: return
                if (jvmDescriptor.methodName == null) {
                    return
                }

                if (jvmDescriptor.jvmTestKind != JvmTestKind.ATOMIC) return

                val testKey = TestKey(
                    gradlePath = resolveGradleModulePath(jvmDescriptor),
                    className = jvmDescriptor.className ?: "<unknown>",
                    testName = jvmDescriptor.methodName ?: jvmDescriptor.displayName ?: "<unknown>"
                )

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
            }
            else -> {
                // Cricket sounds...
            }
        }
    }

    private fun resolveGradleModulePath(descriptor: OperationDescriptor): String {
        var current: OperationDescriptor? = descriptor
        while (current != null) {
            if (current is TaskOperationDescriptor) {
                val taskPath = current.taskPath // ":core:moduleA:test"
                val lastColon = taskPath.lastIndexOf(':')
                return if (lastColon > 0) {
                    taskPath.take(lastColon) // ":core:moduleA"
                } else {
                    taskPath
                }
            }
            current = current.parent
        }
        return ""
    }
}