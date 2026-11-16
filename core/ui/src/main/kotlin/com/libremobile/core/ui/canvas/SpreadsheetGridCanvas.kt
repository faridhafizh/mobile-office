package com.libremobile.core.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.libremobile.core.model.calc.CellPosition
import com.libremobile.core.model.calc.Sheet
import com.libremobile.core.ui.theme.*
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

@Composable
fun SpreadsheetGridCanvas(
    sheet: Sheet,
    selectedCell: CellPosition,
    onCellSelected: (CellPosition) -> Unit,
    modifier: Modifier = Modifier
) {
    var scrollX by remember { mutableFloatStateOf(0f) }
    var scrollY by remember { mutableFloatStateOf(0f) }

    val defaultColWidth = 90f
    val defaultRowHeight = 32f
    val headerColWidth = 50f
    val headerRowHeight = 28f

    val textMeasurer = rememberTextMeasurer()
    val isDark = MaterialTheme.colorScheme.background == LibreBackgroundDark

    val gridLineColor = if (isDark) GridLineDark else GridLineLight
    val headerBgColor = if (isDark) GridHeaderDark else GridHeaderLight
    val canvasBgColor = if (isDark) LibreBackgroundDark else LibreBackgroundLight
    val textColor = MaterialTheme.colorScheme.onBackground

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    val touchX = tapOffset.x - headerColWidth + scrollX
                    val touchY = tapOffset.y - headerRowHeight + scrollY

                    if (touchX >= 0 && touchY >= 0) {
                        val col = floor(touchX / defaultColWidth).toInt()
                        val row = floor(touchY / defaultRowHeight).toInt()
                        onCellSelected(CellPosition(col, row))
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    scrollX = max(0f, scrollX - dragAmount.x)
                    scrollY = max(0f, scrollY - dragAmount.y)
                }
            }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Background
        drawRect(color = canvasBgColor, size = size)

        // Viewport culling calculation: only render visible cells!
        val firstCol = max(0, floor(scrollX / defaultColWidth).toInt())
        val lastCol = firstCol + ((canvasWidth - headerColWidth) / defaultColWidth).toInt() + 2

        val firstRow = max(0, floor(scrollY / defaultRowHeight).toInt())
        val lastRow = firstRow + ((canvasHeight - headerRowHeight) / defaultRowHeight).toInt() + 2

        // 1. Draw Grid Cells
        for (r in firstRow..lastRow) {
            val cellY = headerRowHeight + (r * defaultRowHeight) - scrollY
            if (cellY > canvasHeight) continue

            for (c in firstCol..lastCol) {
                val cellX = headerColWidth + (c * defaultColWidth) - scrollX
                if (cellX > canvasWidth) continue

                val pos = CellPosition(c, r)
                val isSelected = (pos == selectedCell)

                // Selection background
                if (isSelected) {
                    drawRect(
                        color = SelectionHighlight,
                        topLeft = Offset(cellX, cellY),
                        size = Size(defaultColWidth, defaultRowHeight)
                    )
                }

                // Grid border
                drawRect(
                    color = gridLineColor,
                    topLeft = Offset(cellX, cellY),
                    size = Size(defaultColWidth, defaultRowHeight),
                    style = Stroke(width = 1f)
                )

                // Cell Text
                val cell = sheet.getCell(pos)
                if (cell != null && cell.displayValue.isNotEmpty()) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = cell.displayValue,
                        style = TextStyle(fontSize = 12.sp, color = textColor),
                        topLeft = Offset(cellX + 6f, cellY + 6f),
                        size = Size(defaultColWidth - 12f, defaultRowHeight - 12f)
                    )
                }

                // Active Cell Selection Border & Fill Handle
                if (isSelected) {
                    drawRect(
                        color = SelectionBorder,
                        topLeft = Offset(cellX, cellY),
                        size = Size(defaultColWidth, defaultRowHeight),
                        style = Stroke(width = 2.5f)
                    )
                    // Fill Handle dot
                    drawCircle(
                        color = SelectionBorder,
                        radius = 4f,
                        center = Offset(cellX + defaultColWidth, cellY + defaultRowHeight)
                    )
                }
            }
        }

        // 2. Draw Sticky Top Column Headers (A, B, C...)
        drawRect(
            color = headerBgColor,
            topLeft = Offset(0f, 0f),
            size = Size(canvasWidth, headerRowHeight)
        )
        for (c in firstCol..lastCol) {
            val cellX = headerColWidth + (c * defaultColWidth) - scrollX
            if (cellX < headerColWidth - defaultColWidth || cellX > canvasWidth) continue

            drawRect(
                color = gridLineColor,
                topLeft = Offset(cellX, 0f),
                size = Size(defaultColWidth, headerRowHeight),
                style = Stroke(width = 1f)
            )

            val colName = CellPosition.toColName(c)
            drawText(
                textMeasurer = textMeasurer,
                text = colName,
                style = TextStyle(fontSize = 11.sp, color = textColor),
                topLeft = Offset(cellX + (defaultColWidth / 2f) - 6f, 6f)
            )
        }

        // 3. Draw Sticky Left Row Headers (1, 2, 3...)
        drawRect(
            color = headerBgColor,
            topLeft = Offset(0f, 0f),
            size = Size(headerColWidth, canvasHeight)
        )
        for (r in firstRow..lastRow) {
            val cellY = headerRowHeight + (r * defaultRowHeight) - scrollY
            if (cellY < headerRowHeight - defaultRowHeight || cellY > canvasHeight) continue

            drawRect(
                color = gridLineColor,
                topLeft = Offset(0f, cellY),
                size = Size(headerColWidth, defaultRowHeight),
                style = Stroke(width = 1f)
            )

            val rowStr = (r + 1).toString()
            drawText(
                textMeasurer = textMeasurer,
                text = rowStr,
                style = TextStyle(fontSize = 11.sp, color = textColor),
                topLeft = Offset(12f, cellY + 6f)
            )
        }

        // 4. Top-Left corner block
        drawRect(
            color = headerBgColor,
            topLeft = Offset(0f, 0f),
            size = Size(headerColWidth, headerRowHeight)
        )
        drawRect(
            color = gridLineColor,
            topLeft = Offset(0f, 0f),
            size = Size(headerColWidth, headerRowHeight),
            style = Stroke(width = 1.5f)
        )
    }
}
