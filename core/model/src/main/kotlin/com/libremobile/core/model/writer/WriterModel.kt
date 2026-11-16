package com.libremobile.core.model.writer

import com.libremobile.core.model.common.ColorRgba
import com.libremobile.core.model.common.DocumentMetadata
import java.util.UUID

enum class TextAlignment {
    LEFT, CENTER, RIGHT, JUSTIFY
}

enum class HeadingLevel(val level: Int) {
    NONE(0), H1(1), H2(2), H3(3), H4(4), H5(5), H6(6)
}

enum class ListType {
    NONE, BULLET, NUMBERED
}

data class TextStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val fontSizePt: Float = 11f,
    val color: ColorRgba = ColorRgba.BLACK,
    val highlightColor: ColorRgba? = null,
    val fontFamily: String = "Liberation Serif"
)

data class ParagraphStyle(
    val alignment: TextAlignment = TextAlignment.LEFT,
    val headingLevel: HeadingLevel = HeadingLevel.NONE,
    val listType: ListType = ListType.NONE,
    val listIndex: Int = 0,
    val indentLevel: Int = 0,
    val lineSpacingMultiplier: Float = 1.15f,
    val spaceBeforePt: Float = 0f,
    val spaceAfterPt: Float = 6f
)

sealed interface WriterBlock {
    val id: String
}

data class Run(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val style: TextStyle = TextStyle()
)

data class Paragraph(
    override val id: String = UUID.randomUUID().toString(),
    val runs: List<Run> = emptyList(),
    val style: ParagraphStyle = ParagraphStyle()
) : WriterBlock {
    val plainText: String get() = runs.joinToString("") { it.text }

    companion object {
        fun simple(text: String, style: ParagraphStyle = ParagraphStyle(), textStyle: TextStyle = TextStyle()): Paragraph {
            return Paragraph(
                runs = listOf(Run(text = text, style = textStyle)),
                style = style
            )
        }
    }
}

data class TableCellNode(
    val id: String = UUID.randomUUID().toString(),
    val paragraphs: List<Paragraph> = listOf(Paragraph.simple("")),
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
    val backgroundColor: ColorRgba? = null
)

data class TableRowNode(
    val id: String = UUID.randomUUID().toString(),
    val cells: List<TableCellNode> = emptyList()
)

data class TableNode(
    override val id: String = UUID.randomUUID().toString(),
    val rows: List<TableRowNode> = emptyList()
) : WriterBlock

data class PageSetup(
    val widthMm: Float = 210f,   // A4
    val heightMm: Float = 297f,
    val marginLeftMm: Float = 25f,
    val marginRightMm: Float = 25f,
    val marginTopMm: Float = 25f,
    val marginBottomMm: Float = 25f,
    val isLandscape: Boolean = false
)

data class WriterDocument(
    val id: String = UUID.randomUUID().toString(),
    val metadata: DocumentMetadata = DocumentMetadata(),
    val pageSetup: PageSetup = PageSetup(),
    val blocks: List<WriterBlock> = listOf(Paragraph.simple(""))
) {
    val allParagraphs: List<Paragraph>
        get() = blocks.flatMap { block ->
            when (block) {
                is Paragraph -> listOf(block)
                is TableNode -> block.rows.flatMap { it.cells.flatMap { cell -> cell.paragraphs } }
            }
        }

    val fullPlainText: String
        get() = allParagraphs.joinToString("\n") { it.plainText }
}
