package space.davids_digital.grateki

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

object FixtureUtil {
    fun prepareFixtureProject(fixtureName: String, destinationRoot: Path): Path {
        val sourceRoot = Path.of("src", "test", "resources", "fixtures", fixtureName)
        val destination = destinationRoot.resolve(fixtureName)
        copyRecursively(sourceRoot, destination)
        return destination
    }

    private fun copyRecursively(source: Path, destination: Path) {
        Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val targetDir = destination.resolve(source.relativize(dir).toString())
                Files.createDirectories(targetDir)
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val targetFile = destination.resolve(source.relativize(file).toString())
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING)
                return FileVisitResult.CONTINUE
            }
        })
    }
}