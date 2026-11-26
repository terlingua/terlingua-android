package io.github.terlingua.books.ui.reader

import android.content.ClipData
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.terlingua.books.domain.Block
import io.github.terlingua.books.domain.Inline
import io.github.terlingua.books.R
import io.github.terlingua.books.feedback.FeedbackTextButton
import io.github.terlingua.books.ui.components.focusRing
import io.github.terlingua.books.ui.components.rememberAssetBitmap
import io.github.terlingua.books.ui.theme.LocalCodeColors
import kotlinx.coroutines.launch

@Composable
fun BlocksColumn(blocks: List<Block>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { BlockView(it) }
    }
}

@Composable
fun BlockView(block: Block, modifier: Modifier = Modifier) {
    val typography = MaterialTheme.typography
    when (block) {
        is Block.Heading -> InlineText(
            block.content,
            style = when (block.level) {
                1 -> typography.headlineMedium
                2 -> typography.headlineSmall
                3 -> typography.titleLarge
                else -> typography.titleMedium
            },
            modifier = modifier.semantics { heading() }.padding(top = 8.dp),
        )
        is Block.Paragraph -> InlineText(block.content, modifier = modifier)
        is Block.Code -> CodeBlock(block, modifier)
        is Block.Quote -> Row(modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
            BlocksColumn(block.blocks, Modifier.padding(start = 12.dp))
        }
        is Block.BulletList -> ListBlock(block.items, modifier) { "•" }
        is Block.OrderedList -> ListBlock(block.items, modifier) { "${block.start + it}." }
        is Block.Table -> TableBlock(block, modifier)
        Block.Rule -> HorizontalDivider(modifier.padding(vertical = 8.dp))
        is Block.Image -> ImageBlock(block, modifier)
    }
}

@Composable
fun InlineText(content: List<Inline>, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge, color: Color = Color.Unspecified) {
    val text = rememberInlines(content)
    Text(text, modifier = modifier, style = style, color = color)
}

@Composable
fun rememberInlines(content: List<Inline>): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    val code = LocalCodeColors.current
    return remember(content, colors, code) {
        val linkStyles = TextLinkStyles(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline))
        val codeStyle = SpanStyle(fontFamily = FontFamily.Monospace, background = code.background, color = code.plain)
        buildAnnotatedString {
            fun appendInlines(items: List<Inline>) {
                items.forEach { item ->
                    when (item) {
                        is Inline.Text -> append(item.v)
                        is Inline.Code -> withStyle(codeStyle) { append(item.v) }
                        is Inline.Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInlines(item.c) }
                        is Inline.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { appendInlines(item.c) }
                        is Inline.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { appendInlines(item.c) }
                        is Inline.Link -> withLink(LinkAnnotation.Url(item.href, linkStyles)) { appendInlines(item.c) }
                        Inline.Break -> append('\n')
                    }
                }
            }
            appendInlines(content)
        }
    }
}

@Composable
private fun ListBlock(items: List<List<Block>>, modifier: Modifier, marker: (Int) -> String) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { index, blocks ->
            Row {
                Text(marker(index), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(min = 24.dp).padding(end = 8.dp))
                BlocksColumn(blocks, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CodeBlock(block: Block.Code, modifier: Modifier) {
    val colors = LocalCodeColors.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember(block) { mutableStateOf(false) }
    val text = remember(block, colors) {
        buildAnnotatedString {
            block.tokens.forEach { t -> withStyle(SpanStyle(color = colors.forKind(t.k), fontStyle = if (t.k == "com") FontStyle.Italic else null)) { append(t.v) } }
        }
    }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = colors.background) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(block.lang.ifEmpty { "text" }, style = MaterialTheme.typography.labelMedium, color = colors.comment, modifier = Modifier.weight(1f))
                FeedbackTextButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(block.lang, block.text)))
                            copied = true
                        }
                    },
                    modifier = Modifier.focusRing(),
                ) {
                    if (copied) Icon(Icons.Filled.Done, contentDescription = null, tint = colors.comment)
                    Text(stringResource(if (copied) R.string.code_copied else R.string.code_copy), color = colors.comment)
                }
            }
            SelectionContainer {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun TableBlock(table: Block.Table, modifier: Modifier) {
    val columns = maxOf(table.head.size, table.rows.maxOfOrNull { it.size } ?: 0)
    if (columns == 0) return
    val outline = MaterialTheme.colorScheme.outlineVariant
    val shape = RoundedCornerShape(8.dp)
    val cells = buildList {
        repeat(columns) { add(table.head.getOrNull(it).orEmpty() to true) }
        table.rows.forEach { row -> repeat(columns) { add(row.getOrNull(it).orEmpty() to false) } }
    }
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, outline, shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .horizontalScroll(rememberScrollState()),
    ) {
        GridLayout(columns = columns) {
            cells.forEachIndexed { index, (content, header) ->
                val align = table.align.getOrNull(index % columns)
                InlineText(
                    content,
                    style = (if (header) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium).copy(
                        textAlign = when (align) {
                            "c" -> TextAlign.Center
                            "r" -> TextAlign.End
                            else -> TextAlign.Start
                        },
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Measures cells (row-major) so each column takes the width of its widest cell, capped for readability. */
@Composable
private fun GridLayout(columns: Int, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, _ ->
        val maxCell = 320.dp.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxWidth = maxCell)) }
        val rows = (placeables.size + columns - 1) / columns
        val widths = IntArray(columns) { c -> (0 until rows).maxOf { r -> placeables.getOrNull(r * columns + c)?.width ?: 0 } }
        val heights = IntArray(rows) { r -> (0 until columns).maxOf { c -> placeables.getOrNull(r * columns + c)?.height ?: 0 } }
        layout(widths.sum(), heights.sum()) {
            var y = 0
            for (r in 0 until rows) {
                var x = 0
                for (c in 0 until columns) {
                    placeables.getOrNull(r * columns + c)?.placeRelative(x, y)
                    x += widths[c]
                }
                y += heights[r]
            }
        }
    }
}

@Composable
private fun ImageBlock(block: Block.Image, modifier: Modifier) {
    val bitmap = rememberAssetBitmap(block.src) ?: return
    Image(
        bitmap,
        contentDescription = block.alt.ifBlank { null },
        contentScale = ContentScale.Fit,
        modifier = modifier.widthIn(max = 640.dp).clip(RoundedCornerShape(8.dp)),
    )
}
