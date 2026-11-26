package io.github.terlingua.build

import io.github.terlingua.build.content.ContentCompiler
import io.github.terlingua.build.content.ContentValidationException
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.time.LocalDate

@CacheableTask
abstract class CompileContentTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contentDirectory: DirectoryProperty

    /** UTC build date (YYYY-MM-DD); books dated after it are skipped. */
    @get:Input
    abstract val buildDate: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun compile() {
        val result = try {
            ContentCompiler.compile(
                contentDir = contentDirectory.get().asFile,
                outputDir = outputDirectory.get().asFile,
                buildDate = LocalDate.parse(buildDate.get()),
            )
        } catch (e: ContentValidationException) {
            throw GradleException(e.message ?: "Content validation failed", e)
        }
        result.warnings.forEach { logger.warn("w: $it") }
        logger.lifecycle("Terlingua content: ${result.bookCount} book(s), ${result.assetCount} image(s)")
    }
}
