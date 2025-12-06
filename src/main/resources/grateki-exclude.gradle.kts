import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.Test
import java.io.File

/**
 * This Gradle script configures test tasks to exclude the tests specified
 * in a file whose path is provided via the project property "grateki.testClassesFile".
 * Each line in the file should contain a fully qualified class name of a test to exclude.
 *
 * NOTE: We use Gradle project properties (-P) instead of JVM system properties (-D)
 * because Gradle daemon reuse ignores JVM args changes, causing workers to share
 * the same properties. Project properties are per-build, not per-daemon.
 */

val workerId = gradle.startParameter.projectProperties["grateki.workerId"]

if (workerId != null) {
    allprojects {
        buildDir = File(buildDir, "build-worker$workerId")
    }
}

val propertyName = "grateki.testClassesFile"
val testsToExcludeFileName = gradle.startParameter.projectProperties[propertyName] ?:
    throw GradleException("grateki: missing required project property: $propertyName")

val testsToExcludeFile = File(testsToExcludeFileName)
if (!testsToExcludeFile.exists()) {
    throw GradleException("grateki: tests to exclude file does not exist: $testsToExcludeFileName")
}

val testsToExclude = testsToExcludeFile.readLines()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .toSet()

allprojects {
    tasks.withType<Test>().configureEach {
        filter {
            testsToExclude.forEach { fqcn ->
                excludeTestsMatching(fqcn)
            }
        }
    }
}

logger.lifecycle("grateki: loaded ${testsToExclude.size} test classes to exclude from $testsToExcludeFileName")