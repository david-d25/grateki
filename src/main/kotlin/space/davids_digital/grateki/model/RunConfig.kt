package space.davids_digital.grateki.model

import java.nio.file.Path
import java.time.Duration

data class RunConfig (
    val projectPath: Path,
    val tasks: List<String>,
    val workers: Int,
    val timeout: Duration? = null,
    val logDirPath: Path,
)