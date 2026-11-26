package io.github.terlingua.build.content

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.schema.CoreSchema
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Reads `content/authors` and `content/books`, validates structure and quizzes, and renders
 * Markdown. All problems are collected and reported together via [ContentValidationException].
 */
internal class ContentLoader(private val root: File, private val buildDate: LocalDate) {

    private val diagnostics = Diagnostics(root)
    private val assets = AssetCollector(root)
    private val markdown = MarkdownCompiler(assets)

    val warnings: List<String> get() = diagnostics.warnings

    fun load(): Library {
        val authors = loadAuthors()
        val books = loadBooks(authors.associateBy { it.id })
        if (diagnostics.errors.isNotEmpty()) throw ContentValidationException(diagnostics.errors.toList())
        return Library(authors, books, assets.assets.toSortedSet())
    }

    private fun loadAuthors(): List<Author> {
        val dir = File(root, "authors")
        if (!dir.isDirectory) return emptyList()
        return visible(dir).filter { it.isFile && it.extension in YAML_EXTENSIONS }.mapNotNull { file ->
            val id = file.nameWithoutExtension
            if (!SLUG.matches(id)) {
                diagnostics.error(file, "invalid author id '$id' (use lowercase letters, digits and single hyphens)")
                return@mapNotNull null
            }
            val f = fields(file) ?: return@mapNotNull null
            val name = f.string("name", required = true) ?: return@mapNotNull null
            val avatar = f.string("avatar")?.let { path ->
                assets.resolveImage(path, file.parentFile) ?: null.also {
                    diagnostics.warn(file, "avatar '$path' not found under content/; it will be omitted")
                }
            }
            Author(id, name, f.string("email"), f.string("bio"), avatar, f.stringMap("socials"))
        }
    }

    private fun loadBooks(authors: Map<String, Author>): List<Book> {
        val dir = File(root, "books")
        if (!dir.isDirectory) return emptyList()
        return visible(dir).filter { it.isDirectory }
            .mapNotNull { loadBook(it, authors) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    private fun loadBook(dir: File, authors: Map<String, Author>): Book? {
        val slug = dir.name
        if (!SLUG.matches(slug)) {
            diagnostics.error(dir, "invalid book slug '$slug' (use lowercase letters, digits and single hyphens)")
            return null
        }
        val file = YAML_EXTENSIONS.map { File(dir, "book.$it") }.firstOrNull { it.isFile }
        if (file == null) {
            diagnostics.error(dir, "missing book.yml")
            return null
        }
        val f = fields(file) ?: return null
        if (f.bool("draft")) return null

        val title = f.string("title", required = true) ?: slug
        val authorIds = (f.stringList("author") + f.stringList("authors")).distinct()
        authorIds.filterNot { it in authors }.forEach {
            diagnostics.warn(file, "author '$it' has no content/authors/$it.yml; showing the id instead")
        }
        val date = f.date("date")
        val updated = f.date("updated")
        val image = f.string("image")?.let { path ->
            assets.resolveImage(path, dir) ?: null.also { diagnostics.warn(file, "image '$path' not found under content/; it will be omitted") }
        }
        val chapters = loadChapters(dir, slug)
        if (chapters.isEmpty()) diagnostics.warn(dir, "book has no published chapters")
        if (date != null && date.isAfter(buildDate)) return null

        return Book(
            slug = slug,
            title = title,
            summary = f.string("summary").orEmpty(),
            authorIds = authorIds,
            date = date,
            updated = updated,
            tags = f.stringList("tags").distinct(),
            imageAsset = image,
            chapters = chapters,
        )
    }

    private fun loadChapters(bookDir: File, bookSlug: String): List<Chapter> {
        val numbers = mutableMapOf<Int, String>()
        val slugs = mutableMapOf<String, String>()
        return visible(bookDir)
            .filter { it.isDirectory && it.name !in IGNORED_DIRS }
            .mapNotNull { dir ->
                val (number, slug) = numbered(dir, dir.name) ?: return@mapNotNull null
                if (!unique(dir, number, slug, numbers, slugs)) return@mapNotNull null
                val file = YAML_EXTENSIONS.map { File(dir, "chapter.$it") }.firstOrNull { it.isFile }
                if (file == null) {
                    diagnostics.error(dir, "missing chapter.yml")
                    return@mapNotNull null
                }
                val f = fields(file) ?: return@mapNotNull null
                if (f.bool("draft")) return@mapNotNull null
                val title = f.string("title", required = true) ?: slug
                val sections = loadSections(dir, "$bookSlug/$slug")
                if (sections.isEmpty()) diagnostics.warn(dir, "chapter has no published sections")
                Chapter(slug, number, title, f.string("summary").orEmpty(), sections)
            }
            .sortedBy { it.number }
    }

    private fun loadSections(chapterDir: File, keyPrefix: String): List<Section> {
        val numbers = mutableMapOf<Int, String>()
        val slugs = mutableMapOf<String, String>()
        return visible(chapterDir)
            .filter { it.isFile && it.extension.lowercase() == "md" }
            .mapNotNull { file ->
                val (number, slug) = numbered(file, file.nameWithoutExtension) ?: return@mapNotNull null
                if (!unique(file, number, slug, numbers, slugs)) return@mapNotNull null
                loadSection(file, number, slug, "$keyPrefix/$slug")
            }
            .sortedBy { it.number }
    }

    private fun loadSection(file: File, number: Int, slug: String, key: String): Section? {
        val (frontMatter, body) = splitFrontMatter(file.readText())
        if (frontMatter == null) {
            diagnostics.error(file, "missing YAML front matter (--- title: ... ---)")
            return null
        }
        val f = parseFields(file, frontMatter) ?: return null
        if (f.bool("draft")) return null
        val title = f.string("title", required = true) ?: return null
        return Section(
            key = key,
            slug = slug,
            number = number,
            title = title,
            body = markdown.blocks(body, file.parentFile),
            questions = parseQuestions(file, f.raw("questions")),
        )
    }

    private fun parseQuestions(file: File, raw: Any?): List<Question> {
        if (raw == null) return emptyList()
        if (raw !is List<*>) {
            diagnostics.error(file, "'questions' must be a list")
            return emptyList()
        }
        val ids = mutableSetOf<String>()
        return raw.mapIndexedNotNull { index, item ->
            if (item !is Map<*, *>) {
                diagnostics.error(file, "question #${index + 1} must be a mapping")
                return@mapIndexedNotNull null
            }
            val f = Fields(file, item)
            val id = f.string("id") ?: "q${index + 1}"
            val label = "question '$id'"
            var valid = true
            fun fail(message: String) {
                diagnostics.error(file, "$label $message")
                valid = false
            }
            if (!ID.matches(id)) fail("has an invalid id (use letters, digits, '-' or '_')")
            if (!ids.add(id)) fail("is a duplicate id within this section")
            val prompt = f.string("prompt")
            if (prompt.isNullOrBlank()) fail("is missing a prompt")

            val rawOptions = f.raw("options") as? List<*>
            if (rawOptions == null || rawOptions.size < 2) fail("must have at least 2 options (found ${rawOptions?.size ?: 0})")
            val optionIds = mutableSetOf<String>()
            val options = rawOptions.orEmpty().mapIndexedNotNull { i, opt ->
                if (opt !is Map<*, *>) {
                    fail("option #${i + 1} must be a mapping")
                    return@mapIndexedNotNull null
                }
                val of = Fields(file, opt)
                val optionId = of.string("id") ?: if (i < 26) ('a' + i).toString() else "o${i + 1}"
                if (!ID.matches(optionId)) fail("option '$optionId' has an invalid id")
                if (!optionIds.add(optionId)) fail("has duplicate option id '$optionId'")
                val text = of.string("text")
                if (text.isNullOrBlank()) fail("option '$optionId' is missing text")
                Option(optionId, markdown.inlines(text.orEmpty(), file.parentFile), of.bool("correct"))
            }
            val correct = options.count { it.correct }
            if (rawOptions != null && rawOptions.size >= 2 && correct == 0) fail("has no correct option")
            val type = when (val t = f.string("type")?.lowercase()) {
                null -> if (correct > 1) QuestionType.MULTIPLE else QuestionType.SINGLE
                "single" -> QuestionType.SINGLE.also { if (correct > 1) fail("is type 'single' but has $correct correct options") }
                "multiple" -> QuestionType.MULTIPLE
                else -> QuestionType.SINGLE.also { fail("has unknown type '$t' (use single or multiple)") }
            }
            if (!valid) return@mapIndexedNotNull null
            Question(
                id = id,
                type = type,
                prompt = markdown.inlines(prompt.orEmpty(), file.parentFile),
                explanation = f.string("explanation")?.takeIf { it.isNotBlank() }?.let { markdown.blocks(it, file.parentFile) },
                options = options,
            )
        }
    }

    private fun numbered(file: File, name: String): Pair<Int, String>? {
        val match = NUMBERED.matchEntire(name)
        if (match == null) {
            diagnostics.error(file, "'$name' is not numbered (expected NN-slug, e.g. 01-getting-started)")
            return null
        }
        val slug = match.groupValues[2]
        if (!SLUG.matches(slug)) {
            diagnostics.error(file, "invalid slug '$slug' (use lowercase letters, digits and single hyphens)")
            return null
        }
        return match.groupValues[1].toInt() to slug
    }

    private fun unique(file: File, number: Int, slug: String, numbers: MutableMap<Int, String>, slugs: MutableMap<String, String>): Boolean {
        var ok = true
        numbers.putIfAbsent(number, file.name)?.let {
            diagnostics.error(file, "duplicate number $number (also used by '$it')")
            ok = false
        }
        slugs.putIfAbsent(slug, file.name)?.let {
            diagnostics.error(file, "duplicate slug '$slug' (also used by '$it')")
            ok = false
        }
        return ok
    }

    private fun visible(dir: File): List<File> =
        dir.listFiles().orEmpty().filterNot { it.name.startsWith(".") }.sortedBy { it.name }

    private fun fields(file: File): Fields? = parseFields(file, file.readText())

    private fun parseFields(file: File, yaml: String): Fields? {
        val parsed = try {
            Load(LoadSettings.builder().setSchema(CoreSchema()).setLabel(file.name).build()).loadFromString(yaml)
        } catch (e: Exception) {
            diagnostics.error(file, "invalid YAML: ${e.message?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: e::class.simpleName}")
            return null
        }
        return when (parsed) {
            null -> Fields(file, emptyMap<String, Any?>())
            is Map<*, *> -> Fields(file, parsed)
            else -> null.also { diagnostics.error(file, "YAML must be a mapping of fields") }
        }
    }

    private inner class Fields(val file: File, private val map: Map<*, *>) {
        fun raw(key: String): Any? = map[key]

        fun string(key: String, required: Boolean = false): String? = when (val v = map[key]) {
            null -> null.also { if (required) diagnostics.error(file, "missing required field '$key'") }
            is String -> v.trim().ifEmpty { null }.also { if (required && it == null) diagnostics.error(file, "field '$key' must not be empty") }
            is Number, is Boolean -> v.toString()
            else -> null.also { diagnostics.error(file, "field '$key' must be a string") }
        }

        fun bool(key: String): Boolean = when (val v = map[key]) {
            null -> false
            is Boolean -> v
            else -> false.also { diagnostics.error(file, "field '$key' must be true or false (found '$v')") }
        }

        fun stringList(key: String): List<String> = when (val v = map[key]) {
            null -> emptyList()
            is String -> v.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            is List<*> -> v.mapNotNull { item ->
                when (item) {
                    is String, is Number, is Boolean -> item.toString().trim().ifEmpty { null }
                    else -> null.also { diagnostics.error(file, "field '$key' must be a list of strings") }
                }
            }
            else -> emptyList<String>().also { diagnostics.error(file, "field '$key' must be a string or list") }
        }

        fun stringMap(key: String): Map<String, String> = when (val v = map[key]) {
            null -> emptyMap()
            is Map<*, *> -> v.entries.mapNotNull { (k, value) ->
                val s = value?.toString()?.trim().orEmpty()
                if (k == null || s.isEmpty()) null else k.toString() to s
            }.toMap(linkedMapOf())
            else -> emptyMap<String, String>().also { diagnostics.error(file, "field '$key' must be a mapping") }
        }

        fun date(key: String): LocalDate? {
            val s = string(key) ?: return null
            return try {
                LocalDate.parse(s.take(10))
            } catch (_: DateTimeParseException) {
                null.also { diagnostics.error(file, "field '$key' must be a date (YYYY-MM-DD), found '$s'") }
            }
        }
    }

    companion object {
        private val YAML_EXTENSIONS = listOf("yml", "yaml")
        private val IGNORED_DIRS = setOf("images", "assets")
        val SLUG = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")
        private val NUMBERED = Regex("^(\\d+)-(.*)$")
        private val ID = Regex("^[A-Za-z0-9][A-Za-z0-9_-]*$")
        private val FRONT_MATTER_END = Regex("^---[ \\t]*$", RegexOption.MULTILINE)

        fun splitFrontMatter(text: String): Pair<String?, String> {
            val normalized = text.removePrefix("﻿").replace("\r\n", "\n")
            if (!normalized.startsWith("---\n")) return null to normalized
            val end = FRONT_MATTER_END.find(normalized, 4) ?: return null to normalized
            return normalized.substring(4, end.range.first) to normalized.substring(end.range.last + 1).removePrefix("\n")
        }
    }
}
