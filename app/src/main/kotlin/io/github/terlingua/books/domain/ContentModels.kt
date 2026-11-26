package io.github.terlingua.books.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Block tree produced at build time by build-logic's MarkdownCompiler. */
@Serializable
sealed interface Block {
    @Serializable @SerialName("h")
    data class Heading(@SerialName("l") val level: Int, @SerialName("c") val content: List<Inline>) : Block

    @Serializable @SerialName("p")
    data class Paragraph(@SerialName("c") val content: List<Inline>) : Block

    @Serializable @SerialName("code")
    data class Code(val lang: String = "", val tokens: List<CodeToken> = emptyList()) : Block {
        val text: String get() = tokens.joinToString("") { it.v }
    }

    @Serializable @SerialName("quote")
    data class Quote(@SerialName("b") val blocks: List<Block>) : Block

    @Serializable @SerialName("ul")
    data class BulletList(val items: List<List<Block>>) : Block

    @Serializable @SerialName("ol")
    data class OrderedList(val start: Int = 1, val items: List<List<Block>>) : Block

    @Serializable @SerialName("table")
    data class Table(
        val align: List<String> = emptyList(),
        val head: List<List<Inline>> = emptyList(),
        val rows: List<List<List<Inline>>> = emptyList(),
    ) : Block

    @Serializable @SerialName("hr")
    data object Rule : Block

    @Serializable @SerialName("img")
    data class Image(val src: String, val alt: String = "") : Block
}

@Serializable
sealed interface Inline {
    @Serializable @SerialName("text")
    data class Text(val v: String) : Inline

    @Serializable @SerialName("code")
    data class Code(val v: String) : Inline

    @Serializable @SerialName("em")
    data class Emphasis(val c: List<Inline>) : Inline

    @Serializable @SerialName("strong")
    data class Strong(val c: List<Inline>) : Inline

    @Serializable @SerialName("del")
    data class Strike(val c: List<Inline>) : Inline

    @Serializable @SerialName("link")
    data class Link(val href: String, val c: List<Inline>) : Inline

    @Serializable @SerialName("br")
    data object Break : Inline
}

/** `k` is one of kw, str, com, num, lit, fn, type, ann, op, var, tag, attr or empty for plain text. */
@Serializable
data class CodeToken(val k: String = "", val v: String)

fun List<Inline>.plainText(): String = joinToString("") {
    when (it) {
        is Inline.Text -> it.v
        is Inline.Code -> it.v
        is Inline.Emphasis -> it.c.plainText()
        is Inline.Strong -> it.c.plainText()
        is Inline.Strike -> it.c.plainText()
        is Inline.Link -> it.c.plainText()
        Inline.Break -> "\n"
    }
}
