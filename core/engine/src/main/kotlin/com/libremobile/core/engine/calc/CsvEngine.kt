package com.libremobile.core.engine.calc

import com.libremobile.core.model.calc.*
import com.libremobile.core.model.common.DocumentMetadata
import java.io.InputStream
import java.io.OutputStream

object CsvEngine {

    fun readCsv(inputStream: InputStream, sheetName: String = "Sheet1"): CalcWorkbook {
        val reader = inputStream.bufferedReader(Charsets.UTF_8)
        val cells = mutableMapOf<CellPosition, CellData>()

        var rowIndex = 0
        reader.useLines { lines ->
            for (line in lines) {
                if (line.isEmpty()) {
                    rowIndex++
                    continue
                }
                val tokens = parseCsvLine(line)
                for (colIndex in tokens.indices) {
                    val raw = tokens[colIndex]
                    if (raw.isNotEmpty()) {
                        val num = raw.toDoubleOrNull()
                        val type = if (num != null) CellType.NUMBER else CellType.STRING
                        val pos = CellPosition(colIndex, rowIndex)
                        cells[pos] = CellData(
                            position = pos,
                            rawValue = raw,
                            evaluatedValue = raw,
                            numericValue = num,
                            type = type
                        )
                    }
                }
                rowIndex++
            }
        }

        val sheet = Sheet(name = sheetName, cells = cells)
        return CalcWorkbook(
            metadata = DocumentMetadata(title = "CSV Import"),
            sheets = listOf(sheet)
        )
    }

    fun writeCsv(workbook: CalcWorkbook, outputStream: OutputStream, sheetIndex: Int = 0) {
        val sheet = workbook.sheets.getOrElse(sheetIndex) { workbook.sheets.firstOrNull() ?: Sheet() }
        val writer = outputStream.bufferedWriter(Charsets.UTF_8)

        val maxCol = sheet.maxUsedCol
        val maxRow = sheet.maxUsedRow

        for (r in 0..maxRow) {
            val rowTokens = mutableListOf<String>()
            for (c in 0..maxCol) {
                val cell = sheet.getCell(CellPosition(c, r))
                val valStr = cell?.displayValue ?: ""
                rowTokens.add(escapeCsvField(valStr))
            }
            writer.write(rowTokens.joinToString(","))
            writer.write("\r\n")
        }
        writer.flush()
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++ // Skip escaped quote
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(sb.toString().trim())
                    sb.clear()
                }
                else -> {
                    sb.append(c)
                }
            }
            i++
        }
        result.add(sb.toString().trim())
        return result
    }

    private fun escapeCsvField(field: String): String {
        return if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }
}
