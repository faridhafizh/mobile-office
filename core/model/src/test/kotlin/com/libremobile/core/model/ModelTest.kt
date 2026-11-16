package com.libremobile.core.model

import com.google.common.truth.Truth.assertThat
import com.libremobile.core.model.calc.CellPosition
import com.libremobile.core.model.calc.CellRange
import com.libremobile.core.model.calc.CalcWorkbook
import com.libremobile.core.model.calc.CellData
import com.libremobile.core.model.calc.CellType
import com.libremobile.core.model.common.ColorRgba
import com.libremobile.core.model.common.DocumentType
import com.libremobile.core.model.undo.Command
import com.libremobile.core.model.undo.UndoRedoManager
import com.libremobile.core.model.writer.Paragraph
import com.libremobile.core.model.writer.WriterDocument
import org.junit.jupiter.api.Test

class ModelTest {

    @Test
    fun testCellPositionConversions() {
        assertThat(CellPosition.toColName(0)).isEqualTo("A")
        assertThat(CellPosition.toColName(25)).isEqualTo("Z")
        assertThat(CellPosition.toColName(26)).isEqualTo("AA")
        assertThat(CellPosition.toColName(27)).isEqualTo("AB")

        assertThat(CellPosition.fromColName("A")).isEqualTo(0)
        assertThat(CellPosition.fromColName("Z")).isEqualTo(25)
        assertThat(CellPosition.fromColName("AA")).isEqualTo(26)
        assertThat(CellPosition.fromColName("AB")).isEqualTo(27)

        val pos = CellPosition.fromA1String("B10")
        assertThat(pos.col).isEqualTo(1)
        assertThat(pos.row).isEqualTo(9)
        assertThat(pos.a1String).isEqualTo("B10")

        val posWithDollar = CellPosition.fromA1String("\$C\$5")
        assertThat(posWithDollar.col).isEqualTo(2)
        assertThat(posWithDollar.row).isEqualTo(4)
    }

    @Test
    fun testCellRangeParsing() {
        val range = CellRange.parse("A1:C3")
        assertThat(range).isNotNull()
        assertThat(range!!.minCol).isEqualTo(0)
        assertThat(range.maxCol).isEqualTo(2)
        assertThat(range.minRow).isEqualTo(0)
        assertThat(range.maxRow).isEqualTo(2)
        assertThat(range.contains(CellPosition(1, 1))).isTrue()
        assertThat(range.contains(CellPosition(3, 3))).isFalse()
    }

    @Test
    fun testUndoRedoManager() {
        val manager = UndoRedoManager<String>()
        var state = "initial"

        val cmd1 = object : Command<String> {
            override val description = "Append A"
            override fun execute(target: String) = target + "+A"
            override fun undo(target: String) = target.removeSuffix("+A")
        }

        state = manager.execute(cmd1, state)
        assertThat(state).isEqualTo("initial+A")
        assertThat(manager.canUndo).isTrue()

        state = manager.undo(state)
        assertThat(state).isEqualTo("initial")
        assertThat(manager.canRedo).isTrue()

        state = manager.redo(state)
        assertThat(state).isEqualTo("initial+A")
    }

    @Test
    fun testWriterDocumentPlainText() {
        val doc = WriterDocument(
            blocks = listOf(
                Paragraph.simple("Hello"),
                Paragraph.simple("World")
            )
        )
        assertThat(doc.fullPlainText).isEqualTo("Hello\nWorld")
    }

    @Test
    fun testColorParsing() {
        val hex = "#FF5722"
        val color = ColorRgba.fromHex(hex)
        assertThat(color.red).isEqualTo(0xFF)
        assertThat(color.green).isEqualTo(0x57)
        assertThat(color.blue).isEqualTo(0x22)
    }

    @Test
    fun testDocumentTypeMapping() {
        assertThat(DocumentType.fromExtension("docx")).isEqualTo(DocumentType.WRITER_DOCX)
        assertThat(DocumentType.fromExtension("xlsx")).isEqualTo(DocumentType.CALC_XLSX)
        assertThat(DocumentType.fromExtension("pptx")).isEqualTo(DocumentType.IMPRESS_PPTX)
        assertThat(DocumentType.fromExtension("odt")).isEqualTo(DocumentType.WRITER_ODT)
        assertThat(DocumentType.fromExtension("ods")).isEqualTo(DocumentType.CALC_ODS)
        assertThat(DocumentType.fromExtension("odp")).isEqualTo(DocumentType.IMPRESS_ODP)
    }
}
