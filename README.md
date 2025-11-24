# Grateki - smarter parallel test runner for Gradle 🐾

Grateki (Gradle Test Kittens) - Framework-agnostic parallel test execution for Gradle projects

## Quick start
```bash
grateki --project /path/to/project --workers 4
# or
grateki --project . --tasks ":app:test,:lib:test" --workers 4
```

## CLI Options
```
Usage: grateki [options]
Framework-agnostic parallel test execution for Gradle projects
  -h, --help                Show this help message and exit.
  -H, --home=<homePath>     The path to the folder to use for Grateki home
                              (logs, history, etc.)
  -p, --project=<projectPath>
                            The path to the project to run
  -t, --timeout=<timeout>   Workers timeout, disabled by default
  -T, --tasks=<tasks>[,<tasks>...]
                            The list of Gradle test tasks to run
                              (comma-separated)
  -V, --version             Print version information and exit.
  -w, --workers=<workers>   The number of parallel workers to use
```

# [Original Task](https://docs.google.com/document/d/1kX0pZt8obTAesNABcsLFm5w4gZorDqjlLv8KBzXtzII/edit?tab=t.0)
Gradle Parallel Test Runner
TeamCity has a powerful
[Gradle integration](https://www.jetbrains.com/teamcity/tutorials/tests/gradle-build-configure-test/) used to collect
information about tests executed during a Gradle build. Based on test history TeamCity is able to
[run gradle tests in parallel](https://www.jetbrains.com/help/teamcity/parallel-tests.html).

Your task is related to these two existing TeamCity features (Gradle test reporting and parallel test execution).
Build an “intelligent” test runner for Gradle projects that is given a path to a Gradle project and a list of Gradle
tasks, executes the tasks and collects test execution results.

Functional requirements
* Runs tests
* Captures final test results (optional live test progress)
* Persists historical metrics (per-test duration, success/failure)
* Uses history to split tests on the test class level into N balanced batches
* Automatically enables parallel execution in later runs when sufficient history exists
* Emits a single merged test summary at the end
* Supports multi-module Gradle projects

Robustness and Compatibility
* Must work on arbitrary user Gradle projects, including multi-module setups where you do not control the build script.
* Should avoid or mitigate assumptions about specific Gradle versions or test frameworks to remain compatible with a
wide range of real-world builds.
* A failure in test reporting or batching must not break the build itself (fail-open behavior).
* The solution should degrade gracefully when history is missing, incomplete, or out of date.

Open Design Choices
* How you launch Gradle (command line, Tooling API, etc.).
* How you observe test events (init script, plugin, Tooling API events, XML tailing, etc.).
* How you filter/assign tests to batches (include patterns, different test framework filtersplugin/init hooks, etc.).
* History store and the metrics you keep.
* Batching algorithm
* Auto-parallelization policy (what “sufficient history” means; thresholds; safeguards).

# Test run history

Grateki keeps track of previously ran tests and their metrics to balance future test runs between parallel workers.
Each project has its own history file located at `.gradle/grateki/history.json`.

The history file format is as follows:

```json
{
  "version": 1,
  "tests": [
    {
      "key": {
        "gradlePath": ":app:test",
        "className": "com.example.MyTest",
        "testName": "testFeatureA",
        "parameters": null
      },
      "runs": [
        {
          "buildId": "b7dc0a3f-21e1-4e22-93c2-07d9b4961f44",
          "durationMs": 120,
          "status": "PASSED",
          "finishedAt": 1712345678901
        },
        {
          "buildId": "c3f1e2d4-5b6a-7c8d-9e0f-1a2b3c4d5e6f",
          "durationMs": 150,
          "status": "FAILED",
          "finishedAt": 1712345689012
        }
      ]
    }
  ]
}
```

Version 1 is for future compatibility.
Each test is uniquely identified by its `key`, which includes the Gradle task path, class name, test name, and any
parameters (for parameterized tests).
The `runs` array contains historical execution data for that test, including a unique build ID,
duration in milliseconds, status (PASSED, FAILED, SKIPPED), and a timestamp of when the test was **finished**.

# Test balancing

Consider we have $W$ workers.
We want to split tests into $W$ batches such that the total historical duration of tests in each batch is as balanced
as possible.
We have historical duration data for each test from previous runs stored in the history file, but there
may be tests without any history (new tests).
We use the following algorithm to balance tests:

1. Divide known tests (with history) into $W$ batches $B_1, B_2, \ldots, B_W$ using a greedy algorithm:
   - Sort known tests in descending order of historical duration.
   - For each test in this order, assign it to the batch with the currently smallest total duration.
2. Find the least loaded batch $B_{min}$ among $B_1, B_2, \ldots, B_W$.
3. Run workers as follows:
   - For each batch $B_i$ where $B_i \neq B_{min}$, run a Gradle instance executing only tests in $B_i$ using **include
     filters**.
   - For batch $B_{min}$, run a Gradle instance executing all tests **excluding** tests in other batches using **exclude
     filters**.

This approach is a good balance between simplicity and effectiveness without requiring complex Gradle build script
modifications.

# Communication with Gradle

Grateki uses Gradle Tooling API to launch Gradle builds with test filters.
For each batch of tests, Grateki constructs include/exclude filters based on the test balancing algorithm.
Because of potential OS command line length limits, Grateki writes filters to temporary files and passes them to Gradle
through an environment variable that is read by an init script.

Test results are captures by listening to Gradle Test Progress events.