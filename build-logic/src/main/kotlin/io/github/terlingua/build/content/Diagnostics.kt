package io.github.terlingua.build.content

import java.io.File

internal class Diagnostics(private val root: File) {
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    fun error(file: File, message: String) {
        errors += "${rel(file)}: $message"
    }

    fun warn(file: File, message: String) {
        warnings += "${rel(file)}: $message"
    }

    private fun rel(file: File): String =
        runCatching { file.relativeTo(root.parentFile ?: root).invariantSeparatorsPath }.getOrDefault(file.path)
}

class ContentValidationException(val errors: List<String>) : RuntimeException(
    buildString {
        append("Content validation failed with ${errors.size} error(s):")
        errors.forEach { append("\n  - ").append(it) }
    },
)
