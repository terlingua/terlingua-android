package io.github.terlingua.build.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MarkdownAndHighlightTest {

    @Test
    fun tokensReassembleOriginalSource() {
        val samples = mapOf(
            "kotlin" to "@Composable\nfun A(x: Int = 0x1F) { val s = \"\"\"raw\"\"\" /* c */ }",
            "python" to "def f(x):\n    return x  # done\n'''doc'''",
            "sql" to "SELECT * FROM t WHERE a = 'x' -- c",
            "bash" to "echo \"\$HOME\" \${PATH} # c",
            "xml" to "<a href=\"x\"><!-- c --><b/></a>",
            "json" to "{\"a\": [1, true, null]}",
            "yaml" to "key: value # c\nlist:\n  - 1",
            "unknown" to "anything goes",
        )
        samples.forEach { (lang, code) ->
            assertEquals(lang, code, SyntaxHighlighter.highlight(code, lang).joinToString("") { it.text })
        }
    }

    @Test
    fun classifiesCommonTokens() {
        val tokens = SyntaxHighlighter.highlight("fun main() { println(\"hi\") // x\n}", "kotlin")
        assertTrue(SyntaxHighlighter.Token("kw", "fun") in tokens)
        assertTrue(SyntaxHighlighter.Token("fn", "main") in tokens)
        assertTrue(SyntaxHighlighter.Token("str", "\"hi\"") in tokens)
        assertTrue(SyntaxHighlighter.Token("com", "// x") in tokens)
    }

    @Test
    fun splitsFrontMatter() {
        val (yaml, body) = ContentLoader.splitFrontMatter("---\r\ntitle: A\r\n---\r\nBody\r\n")
        assertEquals("title: A\n", yaml)
        assertEquals("Body\n", body)
        assertNull(ContentLoader.splitFrontMatter("No front matter").first)
    }

    @Test
    fun rendersInlineMarkdownForPrompts() {
        val compiler = MarkdownCompiler(AssetCollector(File(".")))
        val rendered = compiler.inlines("Use `val` and **not** var", File("."))
        assertEquals(
            "[{\"t\":\"text\",\"v\":\"Use \"},{\"t\":\"code\",\"v\":\"val\"},{\"t\":\"text\",\"v\":\" and \"},{\"t\":\"strong\",\"c\":[{\"t\":\"text\",\"v\":\"not\"}]},{\"t\":\"text\",\"v\":\" var\"}]",
            rendered.json,
        )
        assertEquals("Use val and not var", rendered.plainText)
    }
}
