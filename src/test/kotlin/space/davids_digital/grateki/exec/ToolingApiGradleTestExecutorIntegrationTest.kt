package space.davids_digital.grateki.exec

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import space.davids_digital.grateki.FixtureUtil.prepareFixtureProject
import space.davids_digital.grateki.exec.event.TestEvent
import space.davids_digital.grateki.exec.event.TestEventHandler
import space.davids_digital.grateki.model.GradleWorkerRequest
import space.davids_digital.grateki.model.TestStatus
import java.nio.file.Path

class ToolingApiGradleTestExecutorIntegrationTest {

    @Test
    fun `executes gradle tests and emits start and finish events`(@TempDir tempDir: Path) {
        val projectDir = prepareFixtureProject("f01", tempDir)
        val executor = ToolingApiGradleTestExecutor()

        val events = mutableListOf<TestEvent>()
        val handler = TestEventHandler { event -> events += event }

        val request = GradleWorkerRequest(
            id = 1,
            projectPath = projectDir,
            tasks = listOf("test"),
            gradleLogPath = projectDir.resolve("gradle.log")
        )

        val result = executor.run(request, handler)

        assert(result.success)
        assertEquals(5, result.tests.size)

        val statuses = result.tests.map { it.status }.toSet()
        assertTrue(TestStatus.PASSED in statuses)

        val classNames = result.tests.map { it.testKey.className }.toSet()
        assertEquals(setOf("com.example.f01.AlphaTest", "com.example.f01.BetaTest"), classNames)

        val starts = events.filterIsInstance<TestEvent.TestStartEvent>().map { it.testKey }.toSet()
        val finishes = events.filterIsInstance<TestEvent.TestFinishEvent>().map { it.testKey }.toSet()
        val keys = result.tests.map { it.testKey }.toSet()

        assertEquals(keys, starts)
        assertEquals(keys, finishes)
    }
}