package space.davids_digital.grateki.model

/**
 * A unique identifier for a test.
 */
data class TestKey (
    /**
     * The Gradle path to the test (e.g., ":module:test").
     */
    val gradlePath: String,

    /**
     * The fully qualified class name of the test (e.g., "com.example.MyTest").
     */
    val className: String,

    /**
     * The name of the test method (e.g., "testSomething").
     */
    val testName: String,

    /**
     * An optional parameter discriminator for parameterized tests.
     * Helps distinguish between different instances of the same test method.
     * The content and format of this field is engine-specific.
     */
    val parameters: String? = null
)