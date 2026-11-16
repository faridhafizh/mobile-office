package com.libremobile.core.model.impress

import com.libremobile.core.model.common.ColorRgba
import com.libremobile.core.model.common.DocumentMetadata
import com.libremobile.core.model.common.Rect2D
import com.libremobile.core.model.writer.Paragraph
import com.libremobile.core.model.writer.TextStyle
import java.util.UUID

enum class SlideLayout {
    TITLE,
    TITLE_AND_CONTENT,
    SECTION_HEADER,
    TWO_CONTENT,
    BLANK
}

enum class ShapeType {
    RECTANGLE,
    ROUNDED_RECTANGLE,
    ELLIPSE,
    TEXT_BOX,
    LINE,
    ARROW
}

data class FillStyle(
    val color: ColorRgba? = null
) {
    val isVisible: Boolean get() = color != null && color.alpha > 0
    companion object {
        val NONE = FillStyle(null)
        fun solid(color: ColorRgba) = FillStyle(color)
    }
}

data class StrokeStyle(
    val color: ColorRgba? = null,
    val widthPt: Float = 1f
) {
    val isVisible: Boolean get() = color != null && color.alpha > 0 && widthPt > 0f
    companion object {
        val NONE = StrokeStyle(null, 0f)
        fun solid(color: ColorRgba, width: Float = 1f) = StrokeStyle(color, width)
    }
}

data class TextFrameNode(
    val paragraphs: List<Paragraph> = emptyList(),
    val verticalAlign: String = "TOP",
    val wordWrap: Boolean = true
) {
    val plainText: String get() = paragraphs.joinToString("\n") { it.plainText }

    companion object {
        fun simple(text: String, textStyle: TextStyle = TextStyle(fontSizePt = 16f)): TextFrameNode {
            return TextFrameNode(
                paragraphs = listOf(Paragraph.simple(text, textStyle = textStyle))
            )
        }
    }
}

data class ShapeNode(
    val id: String = UUID.randomUUID().toString(),
    val type: ShapeType = ShapeType.RECTANGLE,
    val bounds: Rect2D = Rect2D(50f, 50f, 300f, 150f),
    val rotationDeg: Float = 0f,
    val fill: FillStyle = FillStyle.solid(ColorRgba.WHITE),
    val stroke: StrokeStyle = StrokeStyle.solid(ColorRgba.GRAY, 1f),
    val textFrame: TextFrameNode? = null
)

data class Slide(
    val id: String = UUID.randomUUID().toString(),
    val slideNumber: Int = 1,
    val layout: SlideLayout = SlideLayout.TITLE_AND_CONTENT,
    val backgroundColor: ColorRgba = ColorRgba.WHITE,
    val shapes: List<ShapeNode> = emptyList(),
    val notesText: String = ""
)

data class PresentationDocument(
    val id: String = UUID.randomUUID().toString(),
    val metadata: DocumentMetadata = DocumentMetadata(),
    val slideWidthPt: Float = 960f,  // 16:9 standard (13.33 x 7.5 in)
    val slideHeightPt: Float = 540f,
    val slides: List<Slide> = listOf(Slide())
) {
    fun getSlide(index: Int): Slide? = slides.getOrNull(index)

    fun updateSlide(index: Int, updater: (Slide) -> Slide): PresentationDocument {
        val updated = slides.mapIndexed { idx, s ->
            if (idx == index) updater(s) else s
        }
        return copy(slides = updated)
    }
}
