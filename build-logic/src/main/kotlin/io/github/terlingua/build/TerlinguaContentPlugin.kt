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

class TerlinguaContentPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("terlinguaContent", TerlinguaContentExtension::class.java).apply {
            contentDirectory.convention(project.rootProject.layout.projectDirectory.dir("content"))
            buildDate.convention(
                project.providers.gradleProperty("terlingua.buildDate")
                    .orElse(project.providers.of(UtcDateValueSource::class.java) {}),
            )
            testFixtures.convention(project.layout.projectDirectory.dir("src/test/resources/fixtures"))
        }

        project.plugins.withId("com.android.application") {
            val components = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
            components.onVariants { variant ->
                val task = project.tasks.register(
                    "compile${variant.name.replaceFirstChar(Char::uppercase)}TerlinguaContent",
                    CompileContentTask::class.java,
                ) {
                    it.group = "terlingua"
                    it.description = "Compiles content/ into the bundled SQLite database for ${variant.name}."
                    it.contentDirectory.set(extension.contentDirectory)
                    it.buildDate.set(extension.buildDate)
                }
                variant.sources.assets?.addGeneratedSourceDirectory(task, CompileContentTask::outputDirectory)
            }
        }

        val fixtures = listOf("content", "empty-content").associateWith { name ->
            project.tasks.register("compileTerlinguaFixture${if (name == "content") "" else "Empty"}", CompileContentTask::class.java) {
                it.group = "terlingua"
                it.contentDirectory.set(extension.testFixtures.dir(name))
                it.buildDate.set(FIXTURE_BUILD_DATE)
                it.outputDirectory.set(project.layout.buildDirectory.dir("generated/terlingua/fixtures/$name"))
            }
        }
        project.tasks.withType(Test::class.java).configureEach { test ->
            test.jvmArgumentProviders.add(
                FixtureDatabases(
                    fixtures.getValue("content").flatMap { it.outputDirectory },
                    fixtures.getValue("empty-content").flatMap { it.outputDirectory },
                ),
            )
        }
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
