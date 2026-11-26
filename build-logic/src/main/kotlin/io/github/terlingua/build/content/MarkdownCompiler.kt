package io.github.terlingua.build.content

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import java.io.File

/**
 * Compiles Markdown into the JSON block tree consumed by the app's native Compose renderer.
 *
 * Blocks: h, p, code, quote, ul, ol, table, hr, img. Inlines: text, em, strong, del, code, link, br.
 */
internal class MarkdownCompiler(private val assets: AssetCollector) {

    private val parser: Parser = Parser.builder()
        .extensions(listOf(TablesExtension.create(), StrikethroughExtension.create(), AutolinkExtension.create()))
        .build()

    fun blocks(markdown: String, baseDir: File): Rendered {
        val doc = parser.parse(markdown)
        val text = StringBuilder()
        val blocks = blockList(doc, baseDir, text)
        return Rendered(Json.encode(blocks), text.toString().trim())
    }

    /** Renders short Markdown (prompts, options) to a flat inline list. */
    fun inlines(markdown: String, baseDir: File): Rendered {
        val doc = parser.parse(markdown)
        val text = StringBuilder()
        val out = mutableListOf<Map<String, Any?>>()
        var child = doc.firstChild
        while (child != null) {
            if (out.isNotEmpty()) out += mapOf("t" to "br").also { text.append('\n') }
            if (child is Paragraph || child is Heading) {
                out += inlineList(child, baseDir, text)
            } else {
                val plain = StringBuilder()
                collectText(child, plain)
                out += mapOf("t" to "text", "v" to plain.toString().trim()).also { text.append(plain) }
            }
            child = child.next
        }
        return Rendered(Json.encode(mergeText(out)), text.toString().trim())
    }

    private fun blockList(parent: Node, baseDir: File, text: StringBuilder): List<Map<String, Any?>> {
        val out = mutableListOf<Map<String, Any?>>()
        var node = parent.firstChild
        while (node != null) {
            block(node, baseDir, text)?.let { out += it }
            node = node.next
        }
        return out
    }

    private fun block(node: Node, baseDir: File, text: StringBuilder): Map<String, Any?>? = when (node) {
        is Heading -> mapOf("t" to "h", "l" to node.level, "c" to inlineList(node, baseDir, text)).also { text.append('\n') }
        is Paragraph -> {
            val only = node.firstChild
            if (only is Image && only.next == null) {
                imageBlock(only, baseDir, text)
            } else {
                mapOf("t" to "p", "c" to inlineList(node, baseDir, text)).also { text.append('\n') }
            }
        }
        is BlockQuote -> mapOf("t" to "quote", "b" to blockList(node, baseDir, text))
        is BulletList -> mapOf("t" to "ul", "items" to listItems(node, baseDir, text))
        is OrderedList -> mapOf("t" to "ol", "start" to (node.markerStartNumber ?: 1), "items" to listItems(node, baseDir, text))
        is FencedCodeBlock -> codeBlock(node.literal, node.info.orEmpty().trim().substringBefore(' ').substringBefore('{'), text)
        is IndentedCodeBlock -> codeBlock(node.literal, "", text)
        is ThematicBreak -> mapOf("t" to "hr")
        is TableBlock -> table(node, baseDir, text)
        is HtmlBlock -> htmlToText(node.literal).takeIf { it.isNotBlank() }?.let {
            text.append(it).append('\n')
            mapOf("t" to "p", "c" to listOf(mapOf("t" to "text", "v" to it)))
        }
        is LinkReferenceDefinition -> null
        else -> {
            val plain = StringBuilder()
            collectText(node, plain)
            plain.toString().takeIf { it.isNotBlank() }?.let {
                text.append(it).append('\n')
                mapOf("t" to "p", "c" to listOf(mapOf("t" to "text", "v" to it)))
            }
        }
    }

    private fun listItems(list: Node, baseDir: File, text: StringBuilder): List<List<Map<String, Any?>>> {
        val items = mutableListOf<List<Map<String, Any?>>>()
        var item = list.firstChild
        while (item != null) {
            if (item is ListItem) items += blockList(item, baseDir, text)
            item = item.next
        }
        return items
    }

    private fun codeBlock(literal: String, language: String, text: StringBuilder): Map<String, Any?> {
        val code = literal.removeSuffix("\n")
        text.append(code).append('\n')
        val tokens = SyntaxHighlighter.highlight(code, language).map { t ->
            if (t.kind.isEmpty()) mapOf("v" to t.text) else mapOf("k" to t.kind, "v" to t.text)
        }
        return mapOf("t" to "code", "lang" to language.lowercase(), "tokens" to tokens)
    }

    private fun imageBlock(image: Image, baseDir: File, text: StringBuilder): Map<String, Any?> {
        val alt = StringBuilder().also { collectText(image, it) }.toString()
        text.append(alt).append('\n')
        val asset = assets.resolveImage(image.destination, baseDir)
        return if (asset != null) {
            mapOf("t" to "img", "src" to asset, "alt" to alt)
        } else {
            mapOf("t" to "p", "c" to listOf(mapOf("t" to "link", "href" to image.destination, "c" to listOf(mapOf("t" to "text", "v" to alt.ifBlank { image.destination })))))
        }
    }

    private fun table(node: TableBlock, baseDir: File, text: StringBuilder): Map<String, Any?> {
        val head = mutableListOf<List<List<Map<String, Any?>>>>()
        val rows = mutableListOf<List<List<Map<String, Any?>>>>()
        val align = mutableListOf<String>()
        var section = node.firstChild
        while (section != null) {
            var row = section.firstChild
            while (row != null) {
                if (row is TableRow) {
                    val cells = mutableListOf<List<Map<String, Any?>>>()
                    var cell = row.firstChild
                    while (cell != null) {
                        if (cell is TableCell) {
                            cells += inlineList(cell, baseDir, text)
                            text.append(' ')
                            if (section is TableHead) {
                                align += when (cell.alignment) {
                                    TableCell.Alignment.LEFT -> "l"
                                    TableCell.Alignment.CENTER -> "c"
                                    TableCell.Alignment.RIGHT -> "r"
                                    else -> ""
                                }
                            }
                        }
                        cell = cell.next
                    }
                    text.append('\n')
                    if (section is TableHead) head.add(cells) else if (section is TableBody) rows.add(cells)
                }
                row = row.next
            }
            section = section.next
        }
        return mapOf("t" to "table", "align" to align, "head" to head.firstOrNull().orEmpty(), "rows" to rows)
    }

    private fun inlineList(parent: Node, baseDir: File, text: StringBuilder): List<Map<String, Any?>> {
        val out = mutableListOf<Map<String, Any?>>()
        var node = parent.firstChild
        while (node != null) {
            inline(node, baseDir, text)?.let { out += it }
            node = node.next
        }
        return mergeText(out)
    }

    private fun inline(node: Node, baseDir: File, text: StringBuilder): Map<String, Any?>? = when (node) {
        is Text -> mapOf("t" to "text", "v" to node.literal).also { text.append(node.literal) }
        is Code -> mapOf("t" to "code", "v" to node.literal).also { text.append(node.literal) }
        is Emphasis -> mapOf("t" to "em", "c" to inlineList(node, baseDir, text))
        is StrongEmphasis -> mapOf("t" to "strong", "c" to inlineList(node, baseDir, text))
        is Strikethrough -> mapOf("t" to "del", "c" to inlineList(node, baseDir, text))
        is Link -> mapOf("t" to "link", "href" to node.destination, "c" to inlineList(node, baseDir, text))
        is Image -> {
            val alt = StringBuilder().also { collectText(node, it) }.toString()
            text.append(alt)
            mapOf("t" to "text", "v" to alt)
        }
        is SoftLineBreak -> mapOf("t" to "text", "v" to " ").also { text.append(' ') }
        is HardLineBreak -> mapOf("t" to "br").also { text.append('\n') }
        is HtmlInline -> if (BR.matches(node.literal)) mapOf("t" to "br").also { text.append('\n') } else null
        else -> {
            val plain = StringBuilder().also { collectText(node, it) }.toString()
            text.append(plain)
            mapOf("t" to "text", "v" to plain)
        }
    }

    private fun mergeText(items: List<Map<String, Any?>>): List<Map<String, Any?>> {
        val out = mutableListOf<Map<String, Any?>>()
        for (item in items) {
            val last = out.lastOrNull()
            if (item["t"] == "text" && last?.get("t") == "text") {
                out[out.lastIndex] = mapOf("t" to "text", "v" to (last["v"] as String) + (item["v"] as String))
            } else {
                out += item
            }
        }
        return out
    }

    private fun collectText(node: Node, out: StringBuilder) {
        when (node) {
            is Text -> out.append(node.literal)
            is Code -> out.append(node.literal)
            is FencedCodeBlock -> out.append(node.literal)
            is IndentedCodeBlock -> out.append(node.literal)
            is SoftLineBreak, is HardLineBreak -> out.append(' ')
            else -> {
                var child = node.firstChild
                while (child != null) {
                    collectText(child, out)
                    child = child.next
                }
            }
        }
    }

    private companion object {
        val BR = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
        val TAG = Regex("""<!--[\s\S]*?-->|<[^>]+>""")
        val ENTITIES = mapOf("&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&nbsp;" to " ")

        fun htmlToText(html: String): String {
            var s = BR.replace(html, "\n").replace(TAG, "")
            ENTITIES.forEach { (k, v) -> s = s.replace(k, v) }
            return s.lines().joinToString("\n") { it.trim() }.trim()
        }
    }
}

/** Resolves local image references and records them for copying into APK assets. */
internal class AssetCollector(private val contentRoot: File) {
    private val canonicalRoot = contentRoot.canonicalFile
    private val collected = sortedSetOf<String>()
    val assets: Set<String> get() = collected

    /** Returns the APK asset path (`terlingua/<path under content>`) or null if not a local image. */
    fun resolveImage(raw: String?, baseDir: File?): String? {
        val ref = raw?.trim()?.substringBefore('#')?.substringBefore('?').orEmpty()
        if (ref.isEmpty() || ref.contains("://") || ref.startsWith("data:") || ref.startsWith("mailto:")) return null
        val candidates = buildList {
            if (ref.startsWith("/")) add(File(contentRoot, ref.trimStart('/')))
            else {
                baseDir?.let { add(File(it, ref)) }
                add(File(contentRoot, ref))
            }
        }
        val file = candidates.map { it.canonicalFile }.firstOrNull {
            it.isFile && it.path.startsWith(canonicalRoot.path + File.separator) && it.extension.lowercase() in IMAGE_EXTENSIONS
        } ?: return null
        val rel = file.relativeTo(canonicalRoot).invariantSeparatorsPath
        collected += rel
        return "$ASSET_PREFIX/$rel"
    }

    companion object {
        const val ASSET_PREFIX = "terlingua"
        val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif")
    }
}
