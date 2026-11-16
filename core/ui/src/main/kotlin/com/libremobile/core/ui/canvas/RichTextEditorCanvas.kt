package com.libremobile.core.ui.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.libremobile.core.model.writer.*

@Composable
fun RichTextEditorCanvas(
    document: WriterDocument,
    onParagraphChange: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp)
    ) {
        // Document Page (A4 simulated paper)
        Surface(
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .shadow(6.dp, RoundedCornerShape(4.dp))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                itemsIndexed(document.blocks) { index, block ->
                    when (block) {
                        is Paragraph -> {
                            ParagraphItem(
                                paragraph = block,
                                onTextChange = { newText -> onParagraphChange(index, newText) }
                            )
                        }
                        is TableNode -> {
                            TableItem(table = block)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ParagraphItem(
    paragraph: Paragraph,
    onTextChange: (String) -> Unit
) {
    val align = when (paragraph.style.alignment) {
        TextAlignment.CENTER -> TextAlign.Center
        TextAlignment.RIGHT -> TextAlign.Right
        TextAlignment.JUSTIFY -> TextAlign.Justify
        TextAlignment.LEFT -> TextAlign.Left
    }

    val fontSize = when (paragraph.style.headingLevel) {
        HeadingLevel.H1 -> 24.sp
        HeadingLevel.H2 -> 20.sp
        HeadingLevel.H3 -> 17.sp
        HeadingLevel.H4 -> 15.sp
        else -> 14.sp
    }

    val fontWeight = if (paragraph.style.headingLevel != HeadingLevel.NONE) FontWeight.Bold else FontWeight.Normal

    val annotated = buildAnnotatedString {
        for (run in paragraph.runs) {
            val spanStyle = SpanStyle(
                fontWeight = if (run.style.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (run.style.italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = when {
                    run.style.underline && run.style.strikethrough -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                    run.style.underline -> TextDecoration.Underline
                    run.style.strikethrough -> TextDecoration.LineThrough
                    else -> TextDecoration.None
                },
                color = Color(run.style.color.toArgbLong())
            )
            withStyle(spanStyle) {
                append(run.text)
            }
        }
    }

    BasicTextField(
        value = paragraph.plainText,
        onValueChange = onTextChange,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = align,
            color = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

@Composable
private fun TableItem(table: TableNode) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        for (row in table.rows) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                for (cell in row.cells) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
                            .padding(6.dp)
                    ) {
                        Text(
                            text = cell.paragraphs.joinToString("\n") { it.plainText },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
