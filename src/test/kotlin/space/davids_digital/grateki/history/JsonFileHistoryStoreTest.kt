package space.davids_digital.grateki.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import space.davids_digital.grateki.model.TestKey
import space.davids_digital.grateki.model.TestRunInfo
import space.davids_digital.grateki.model.TestStatus
import java.nio.file.Path
import kotlin.io.path.writeText

class JsonFileHistoryStoreTest {
    @Test
    fun `file does not exist`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("history.json")
        val store = JsonFileHistoryStore(file)
        val count = store.load()
        assertEquals(0, count)
        assert(store.getAll().isEmpty())
    }

    @Test
    fun load(@TempDir tempDir: Path) {
        val file = tempDir.resolve("history.json")
        file.writeText("""
            {
              "version": 1,
              "tests": [
                {
                  "key": {
                    "gradlePath": ":app",
                    "className": "com.example.MyTest",
                    "testName": "testSomething"
                  },
                  "runs": [
                    {
                      "buildId": "0",
                      "finishedAt": 1700000000000,
                      "status": "SKIPPED",
                      "durationMs": 120
                    },
                    {
                      "buildId": "1",
                      "finishedAt": 1700000001000,
                      "status": "FAILED",
                      "durationMs": 150
                    }
                  ]
                }, {
                  "key": {
                    "gradlePath": ":lib",
                    "className": "com.example.LibTest",
                    "testName": "testLibFunction",
                    "parameters": "param1"
                  },
                  "runs": [
                    {
                      "buildId": "2",
                      "finishedAt": 1700000002000,
                      "status": "PASSED",
                      "durationMs": 90
                    }
                  ]
                }
              ]
            }
        """.trimIndent())
        val expected = mapOf(
            TestKey(
                gradlePath = ":app",
                className = "com.example.MyTest",
                testName = "testSomething"
            ) to listOf(
                TestRunInfo(
                    buildId = "0",
                    finishedAt = 1700000000000,
                    status = TestStatus.SKIPPED,
                    durationMs = 120,
                    testKey = TestKey(
                        gradlePath = ":app",
                        className = "com.example.MyTest",
                        testName = "testSomething"
                    )
                ),
                TestRunInfo(
                    buildId = "1",
                    finishedAt = 1700000001000,
                    status = TestStatus.FAILED,
                    durationMs = 150,
                    testKey = TestKey(
                        gradlePath = ":app",
                        className = "com.example.MyTest",
                        testName = "testSomething"
                    )
                )
            ),
            TestKey(
                gradlePath = ":lib",
                className = "com.example.LibTest",
                testName = "testLibFunction",
                parameters = "param1"
            ) to listOf(
                TestRunInfo(
                    buildId = "2",
                    finishedAt = 1700000002000,
                    status = TestStatus.PASSED,
                    durationMs = 90,
                    testKey = TestKey(
                        gradlePath = ":lib",
                        className = "com.example.LibTest",
                        testName = "testLibFunction",
                        parameters = "param1"
                    )
                )
            )
        )
        val store = JsonFileHistoryStore(file)
        val count = store.load()
        assertEquals(2, count)
        val loaded = store.getAll()
        assertEquals(2, loaded.size)
        assertEquals(expected, loaded)
    }

    @Test
    fun `load but json has extra fields`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("history.json")
        file.writeText("""
            {
              "version": 1,
              "tests": [
                {
                  "key": {
                    "gradlePath": ":app",
                    "className": "com.example.MyTest",
                    "testName": "testSomething",
                    "extraField": "should be ignored"
                  },
                  "runs": [
                    {
                      "buildId": "0",
                      "finishedAt": 1700000000000,
                      "status": "SKIPPED",
                      "durationMs": 120,
                      "anotherExtraField": 12345
                    }
                  ]
                }
              ]
            }
        """.trimIndent())
        val expected = mapOf(
            TestKey(
                gradlePath = ":app",
                className = "com.example.MyTest",
                testName = "testSomething"
            ) to listOf(
                TestRunInfo(
                    buildId = "0",
                    finishedAt = 1700000000000,
                    status = TestStatus.SKIPPED,
                    durationMs = 120,
                    testKey = TestKey(
                        gradlePath = ":app",
                        className = "com.example.MyTest",
                        testName = "testSomething"
                    )
                )
            )
        )
        val store = JsonFileHistoryStore(file)
        val count = store.load()
        assertEquals(1, count)
        val loaded = store.getAll()
        assertEquals(1, loaded.size)
        assertEquals(expected, loaded)
    }

    @Test
    fun `replace but file does not exist`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("a/b/history.json")
        val store = JsonFileHistoryStore(file)
        val entries = mapOf(
            TestKey(
                gradlePath = ":app",
                className = "com.example.MyTest",
                testName = "testSomething"
            ) to listOf(
                TestRunInfo(
                    buildId = "0",
                    finishedAt = 1700000000000,
                    status = TestStatus.PASSED,
                    durationMs = 100,
                    testKey = TestKey(
                        gradlePath = ":app",
                        className = "com.example.MyTest",
                        testName = "testSomething"
                    )
                )
            )
        )
        store.replace(entries)
        val loadedEntries = store.getAll()
        assertEquals(entries, loadedEntries)
    }

    @Test
    fun `replace existing data`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("history.json")
        val store = JsonFileHistoryStore(file)
        val initialEntries = mapOf(
            TestKey(
                gradlePath = ":app",
                className = "com.example.MyTest",
                testName = "testSomething"
            ) to listOf(
                TestRunInfo(
                    buildId = "0",
                    finishedAt = 1700000000000,
                    status = TestStatus.FAILED,
                    durationMs = 150,
                    testKey = TestKey(
                        gradlePath = ":app",
                        className = "com.example.MyTest",
                        testName = "testSomething"
                    )
                )
            )
        )
        store.replace(initialEntries)

        val newEntries = mapOf(
            TestKey(
                gradlePath = ":lib",
                className = "com.example.LibTest",
                testName = "testLibFunction"
            ) to listOf(
                TestRunInfo(
                    buildId = "1",
                    finishedAt = 1700000001000,
                    status = TestStatus.PASSED,
                    durationMs = 80,
                    testKey = TestKey(
                        gradlePath = ":lib",
                        className = "com.example.LibTest",
                        testName = "testLibFunction"
                    )
                )
            )
        )
        store.replace(newEntries)
        val loadedEntries = store.getAll()
        assertEquals(newEntries, loadedEntries)
    }

    @Test
    fun `replace and load`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("history.json")
        val store = JsonFileHistoryStore(file)
        val entries = mapOf(
            TestKey(
                gradlePath = ":app",
                className = "com.example.MyTest",
                testName = "testSomething"
            ) to listOf(
                TestRunInfo(
                    buildId = "0",
                    finishedAt = 1700000000000,
                    status = TestStatus.PASSED,
                    durationMs = 100,
                    testKey = TestKey(
                        gradlePath = ":app",
                        className = "com.example.MyTest",
                        testName = "testSomething"
                    )
                )
            )
        )
        store.replace(entries)

        val newStore = JsonFileHistoryStore(file)
        val count = newStore.load()
        assertEquals(1, count)
        val loadedEntries = newStore.getAll()
        assertEquals(entries, loadedEntries)
    }
}