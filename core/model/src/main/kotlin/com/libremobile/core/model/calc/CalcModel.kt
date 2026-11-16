package com.libremobile.core.model.calc

import com.libremobile.core.model.common.ColorRgba
import com.libremobile.core.model.common.DocumentMetadata
import java.util.UUID

data class CellPosition(val col: Int, val row: Int) : Comparable<CellPosition> {
    val a1String: String get() = toColName(col) + (row + 1)

    override fun compareTo(other: CellPosition): Int {
        val rowCmp = row.compareTo(other.row)
        return if (rowCmp != 0) rowCmp else col.compareTo(other.col)
    }

    companion object {
        fun toColName(colIndex: Int): String {
            var temp = colIndex
            val sb = StringBuilder()
            while (temp >= 0) {
                sb.insert(0, ('A'.code + (temp % 26)).toChar())
                temp = (temp / 26) - 1
            }
            return sb.toString()
        }

        fun fromColName(colStr: String): Int {
            var result = 0
            for (ch in colStr.uppercase()) {
                if (ch in 'A'..'Z') {
                    result = result * 26 + (ch - 'A' + 1)
                }
            }
            return result - 1
        }

        fun fromA1String(a1: String): CellPosition {
            val clean = a1.trim().replace("$", "")
            val letters = clean.takeWhile { it.isLetter() }
            val digits = clean.dropWhile { it.isLetter() }
            val col = fromColName(letters)
            val row = (digits.toIntOrNull() ?: 1) - 1
            return CellPosition(col.coerceAtLeast(0), row.coerceAtLeast(0))
        }
    }
}

data class CellRange(
    val startCol: Int,
    val startRow: Int,
    val endCol: Int,
    val endRow: Int
) {
    val minCol: Int get() = minOf(startCol, endCol)
    val maxCol: Int get() = maxOf(startCol, endCol)
    val minRow: Int get() = minOf(startRow, endRow)
    val maxRow: Int get() = maxOf(startRow, endRow)

    val a1Range: String get() = "${CellPosition(minCol, minRow).a1String}:${CellPosition(maxCol, maxRow).a1String}"

    fun contains(pos: CellPosition): Boolean {
        return pos.col in minCol..maxCol && pos.row in minRow..maxRow
    }

    fun forEachPosition(action: (CellPosition) -> Unit) {
        for (r in minRow..maxRow) {
            for (c in minCol..maxCol) {
                action(CellPosition(c, r))
            }
        }
    }

    companion object {
        fun single(pos: CellPosition): CellRange = CellRange(pos.col, pos.row, pos.col, pos.row)
        
        fun parse(rangeStr: String): CellRange? {
            val parts = rangeStr.split(":")
            return when (parts.size) {
                1 -> {
                    val p = CellPosition.fromA1String(parts[0])
                    single(p)
                }
                2 -> {
                    val p1 = CellPosition.fromA1String(parts[0])
                    val p2 = CellPosition.fromA1String(parts[1])
                    CellRange(p1.col, p1.row, p2.col, p2.row)
                }
                else -> null
            }
        }
    }
}

enum class CellType {
    EMPTY, STRING, NUMBER, BOOLEAN, FORMULA, ERROR
}

enum class HorizontalAlign {
    LEFT, CENTER, RIGHT, GENERAL
}

enum class VerticalAlign {
    TOP, CENTER, BOTTOM
}

data class CellStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val fontSizePt: Float = 10f,
    val textColor: ColorRgba = ColorRgba.BLACK,
    val backgroundColor: ColorRgba? = null,
    val horizontalAlign: HorizontalAlign = HorizontalAlign.GENERAL,
    val verticalAlign: VerticalAlign = VerticalAlign.CENTER,
    val numberFormat: String = "General"
)

data class CellData(
    val position: CellPosition,
    val rawValue: String = "",
    val evaluatedValue: String = "",
    val numericValue: Double? = null,
    val type: CellType = CellType.STRING,
    val style: CellStyle = CellStyle()
) {
    val displayValue: String
        get() = when {
            evaluatedValue.isNotEmpty() -> evaluatedValue
            rawValue.isNotEmpty() -> rawValue
            else -> ""
        }

    val isFormula: Boolean get() = rawValue.startsWith("=")
}

data class Sheet(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Sheet1",
    val cells: Map<CellPosition, CellData> = emptyMap(),
    val columnWidths: Map<Int, Float> = emptyMap(),
    val rowHeights: Map<Int, Float> = emptyMap(),
    val defaultColWidth: Float = 80f,
    val defaultRowHeight: Float = 24f
) {
    fun getCell(pos: CellPosition): CellData? = cells[pos]

    fun getCellOrEmpty(col: Int, row: Int): CellData {
        val pos = CellPosition(col, row)
        return cells[pos] ?: CellData(pos, type = CellType.EMPTY)
    }

    fun setCell(data: CellData): Sheet {
        val mutable = cells.toMutableMap()
        if (data.rawValue.isEmpty() && data.style == CellStyle()) {
            mutable.remove(data.position)
        } else {
            mutable[data.position] = data
        }
        return copy(cells = mutable)
    }

    val maxUsedCol: Int get() = cells.keys.maxOfOrNull { it.col } ?: 0
    val maxUsedRow: Int get() = cells.keys.maxOfOrNull { it.row } ?: 0
}

data class CalcWorkbook(
    val id: String = UUID.randomUUID().toString(),
    val metadata: DocumentMetadata = DocumentMetadata(),
    val sheets: List<Sheet> = listOf(Sheet(name = "Sheet1")),
    val activeSheetIndex: Int = 0
) {
    val activeSheet: Sheet
        get() = sheets.getOrElse(activeSheetIndex) { sheets.firstOrNull() ?: Sheet() }

    fun updateActiveSheet(updater: (Sheet) -> Sheet): CalcWorkbook {
        val updatedSheets = sheets.mapIndexed { index, sheet ->
            if (index == activeSheetIndex) updater(sheet) else sheet
        }
        return copy(sheets = updatedSheets)
    }
}
