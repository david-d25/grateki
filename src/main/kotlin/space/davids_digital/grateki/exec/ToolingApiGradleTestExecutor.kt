package space.davids_digital.grateki.exec

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.test.TestFinishEvent
import org.gradle.tooling.events.test.TestStartEvent
import space.davids_digital.grateki.model.GradleWorkerRequest
import space.davids_digital.grateki.model.GradleWorkerResult
import space.davids_digital.grateki.model.TestRunInfo
import java.io.PrintStream

class ToolingApiGradleTestExecutor : GradleTestExecutor {
    override fun run(request: GradleWorkerRequest): GradleWorkerResult {
        val collectedTests = mutableListOf<TestRunInfo>()

        return try {
            // Connect to the Gradle project
            val connection = GradleConnector.newConnector()
                .forProjectDirectory(request.projectPath.toFile())
                .useBuildDistribution()
                .connect()

            connection.use { connection ->
                val build = connection.newBuild()
                build.forTasks(*request.tasks.toTypedArray())
                build.withArguments(
                    buildList {
                        request.initScriptPath?.let {
                            addAll(listOf("--init-script", it.toString()))
                        }
                        addAll(request.gradleArgs)
                    }
                )

                if (request.logPath != null) {
                    val printStream = PrintStream(request.logPath.toFile())
                    build.setStandardOutput(printStream)
                    build.setStandardError(printStream)
                }

                build.addProgressListener(::onBuildStatusChanged, OperationType.TEST)

                if (request.systemProperties.isNotEmpty()) {
                    build.setJvmArguments(request.jvmArgs + request.systemProperties.map { (k, v) -> "-D$k=$v" })
                } else if (request.jvmArgs.isNotEmpty()) {
                    build.setJvmArguments(request.jvmArgs)
                }

                build.run()
            }

            GradleWorkerResult(
                workerId = request.id,
                tests = collectedTests,
                success = true,
            )
        } catch (t: Throwable) {
            GradleWorkerResult(
                workerId = request.id,
                tests = collectedTests,
                success = false,
                throwable = t,
            )
        }
    }

    private fun onBuildStatusChanged(event: ProgressEvent) {
        when (event) {
            is TestStartEvent -> {} // TODO
            is TestFinishEvent -> {} // TODO
            else -> {} // TODO
        }
    }
}