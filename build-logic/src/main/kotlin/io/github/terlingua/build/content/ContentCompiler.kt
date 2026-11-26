package io.github.terlingua.build.content

import java.io.File
import java.security.MessageDigest
import java.time.LocalDate

object ContentCompiler {
    const val DATABASE_NAME = "terlingua-books.db"

    data class Result(val bookCount: Int, val assetCount: Int, val warnings: List<String>)

    /** Compiles [contentDir] into `<outputDir>/terlingua/` (database + referenced images). */
    fun compile(contentDir: File, outputDir: File, buildDate: LocalDate): Result {
        val loader = ContentLoader(contentDir, buildDate)
        val library = loader.load()
        val target = File(outputDir, AssetCollector.ASSET_PREFIX)
        outputDir.deleteRecursively()
        target.mkdirs()
        DatabaseWriter.write(library, File(target, DATABASE_NAME), hash(contentDir))
        library.assets.forEach { rel -> File(contentDir, rel).copyTo(File(target, rel), overwrite = true) }
        return Result(library.books.size, library.assets.size, loader.warnings)
    }

    private fun hash(root: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        root.walkTopDown().filter { it.isFile }.sortedBy { it.invariantSeparatorsPath }.forEach {
            digest.update(it.relativeTo(root).invariantSeparatorsPath.toByteArray())
            digest.update(it.readBytes())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
