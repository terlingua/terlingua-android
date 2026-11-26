package io.github.terlingua.build

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider

abstract class TerlinguaContentExtension {
    abstract val contentDirectory: DirectoryProperty
    abstract val buildDate: Property<String>
    abstract val testFixtures: DirectoryProperty
}

// `kotlin-dsl` compiles Gradle `Action` lambdas with an implicit receiver (`this`), not `it`.
class TerlinguaContentPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("terlinguaContent", TerlinguaContentExtension::class.java)
        extension.contentDirectory.convention(project.rootProject.layout.projectDirectory.dir("content"))
        extension.buildDate.convention(
            project.providers.gradleProperty("terlingua.buildDate")
                .orElse(project.providers.of(UtcDateValueSource::class.java) {}),
        )
        extension.testFixtures.convention(project.layout.projectDirectory.dir("src/test/resources/fixtures"))

        project.plugins.withId("com.android.application") {
            val components = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
            components.onVariants(components.selector().all()) { variant ->
                val task = project.tasks.register(
                    "compile${variant.name.replaceFirstChar(Char::uppercase)}TerlinguaContent",
                    CompileContentTask::class.java,
                ) {
                    group = "terlingua"
                    description = "Compiles content/ into the bundled SQLite database for ${variant.name}."
                    contentDirectory.set(extension.contentDirectory)
                    buildDate.set(extension.buildDate)
                }
                variant.sources.assets?.addGeneratedSourceDirectory(task, CompileContentTask::outputDirectory)
            }
        }

        val content = registerFixture(project, extension, "content", "compileTerlinguaFixture")
        val empty = registerFixture(project, extension, "empty-content", "compileTerlinguaFixtureEmpty")
        project.tasks.withType(Test::class.java).configureEach {
            jvmArgumentProviders.add(
                FixtureDatabases(content.flatMap { it.outputDirectory }, empty.flatMap { it.outputDirectory }),
            )
        }
    }

    private fun registerFixture(project: Project, extension: TerlinguaContentExtension, name: String, taskName: String) =
        project.tasks.register(taskName, CompileContentTask::class.java) {
            group = "terlingua"
            contentDirectory.set(extension.testFixtures.dir(name))
            buildDate.set(FIXTURE_BUILD_DATE)
            outputDirectory.set(project.layout.buildDirectory.dir("generated/terlingua/fixtures/$name"))
        }

    internal class FixtureDatabases(
        @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) val content: Provider<Directory>,
        @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) val empty: Provider<Directory>,
    ) : CommandLineArgumentProvider {
        override fun asArguments(): Iterable<String> = listOf(
            "-Dterlingua.fixtureDb=${content.get().file("terlingua/terlingua-books.db").asFile.absolutePath}",
            "-Dterlingua.emptyFixtureDb=${empty.get().file("terlingua/terlingua-books.db").asFile.absolutePath}",
        )
    }

    companion object {
        const val FIXTURE_BUILD_DATE = "2026-01-01"
    }
}
