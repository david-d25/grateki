import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.Test
import java.io.File

/**
 * This Gradle script configures test tasks to include only the tests specified
 * in a file whose path is provided via the project property "grateki.testClassesFile".
 * Each line in the file should contain a fully qualified class name of a test to run.
 */

val workerId = gradle.startParameter.projectProperties["grateki.workerId"]

if (workerId != null) {
    logger.lifecycle("grateki: workerId = $workerId")
    logger.lifecycle("grateki: gradle home = ${gradle.gradleUserHomeDir}")

    gradle.beforeProject {
        layout.buildDirectory.set(
            layout.projectDirectory.dir("build/grateki-worker-$workerId")
        )
    }
}

val propertyName = "grateki.testClassesFile"
val testsToRunFileName = gradle.startParameter.projectProperties[propertyName] ?:
    throw GradleException("grateki: missing required project property: $propertyName")

val testToRunFile = File(testsToRunFileName)
if (!testToRunFile.exists()) {
    throw GradleException("grateki: tests to run file does not exist: $testsToRunFileName")
}

val testsToRun = testToRunFile.readLines()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .toSet()

allprojects {
    tasks.withType<Test>().configureEach {
        filter {
            testsToRun.forEach { fqcn ->
                includeTestsMatching(fqcn)
            }
        }
    }
}

logger.lifecycle("grateki: loaded ${testsToRun.size} test classes from $testsToRunFileName")