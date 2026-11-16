package com.libremobile.core.engine.calc

import com.libremobile.core.engine.util.XmlStreamHelper
import com.libremobile.core.engine.util.XmlStreamHelper.escapeXml
import com.libremobile.core.engine.util.XmlStreamHelper.findChildElements
import com.libremobile.core.engine.util.XmlStreamHelper.findFirstChildElement
import com.libremobile.core.engine.util.ZipHelper
import com.libremobile.core.model.calc.*
import com.libremobile.core.model.common.DocumentMetadata
import com.libremobile.core.model.common.DocumentType
import java.io.InputStream
import java.io.OutputStream
import org.w3c.dom.Element

object OdsEngine {

    fun readOds(inputStream: InputStream): CalcWorkbook {
        val entries = ZipHelper.readZip(inputStream)
        val contentBytes = entries["content.xml"]
            ?: throw IllegalArgumentException("Invalid ODS: missing content.xml")

        val doc = XmlStreamHelper.parseXml(contentBytes)
        val bodyElem = doc.documentElement.findFirstChildElement("body")
        val ssElem = bodyElem?.findFirstChildElement("spreadsheet")
            ?: return CalcWorkbook()

        val sheets = mutableListOf<Sheet>()
        val tableElements = ssElem.findChildElements("table")

        for (tableElem in tableElements) {
            val tableName = tableElem.getAttribute("table:name").ifEmpty { "Sheet${sheets.size + 1}" }
            val cells = mutableMapOf<CellPosition, CellData>()

            var rowIndex = 0
            val rowElements = tableElem.findChildElements("table-row")
            for (rowElem in rowElements) {
                var colIndex = 0
                val cellElements = rowElem.findChildElements("table-cell")
                for (cellElem in cellElements) {
                    val repeatColumns = cellElem.getAttribute("table:number-columns-repeated").toIntOrNull() ?: 1

                    val formulaAttr = cellElem.getAttribute("table:formula")
                    val valueType = cellElem.getAttribute("office:value-type")
                    val valueAttr = cellElem.getAttribute("office:value")
                    val pElem = cellElem.findFirstChildElement("p")
                    val textVal = pElem?.textContent ?: valueAttr

                    if (textVal.isNotEmpty() || formulaAttr.isNotEmpty()) {
                        val pos = CellPosition(colIndex, rowIndex)
                        val isFormula = formulaAttr.isNotEmpty()
                        val raw = if (isFormula) formulaAttr.removePrefix("of:") else textVal
                        val numVal = valueAttr.toDoubleOrNull() ?: textVal.toDoubleOrNull()

                        cells[pos] = CellData(
                            position = pos,
                            rawValue = raw,
                            evaluatedValue = textVal,
                            numericValue = numVal,
                            type = when {
                                isFormula -> CellType.FORMULA
                                valueType == "float" || numVal != null -> CellType.NUMBER
                                valueType == "boolean" -> CellType.BOOLEAN
                                else -> CellType.STRING
                            }
                        )
                    }

                    colIndex += repeatColumns
                    if (colIndex > 1000) break // safety guard for empty repeating columns
                }
                rowIndex++
            }

            sheets.add(Sheet(name = tableName, cells = cells))
        }

        return CalcWorkbook(
            metadata = DocumentMetadata(title = "Imported ODS"),
            sheets = if (sheets.isEmpty()) listOf(Sheet()) else sheets
        )
    }

    fun writeOds(workbook: CalcWorkbook, outputStream: OutputStream) {
        val entries = mutableMapOf<String, ByteArray>()

        entries["META-INF/manifest.xml"] = """<?xml version="1.0" encoding="UTF-8"?>
<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2">
  <manifest:file-entry manifest:full-path="/" manifest:version="1.2" manifest:media-type="${DocumentType.CALC_ODS.mimeType}"/>
  <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
  <manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/>
</manifest:manifest>""".toByteArray(Charsets.UTF_8)

        entries["styles.xml"] = """<?xml version="1.0" encoding="UTF-8"?>
<office:document-styles xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
  xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
  office:version="1.2">
</office:document-styles>""".toByteArray(Charsets.UTF_8)

        val contentXml = buildContentXml(workbook)
        entries["content.xml"] = contentXml.toByteArray(Charsets.UTF_8)

        ZipHelper.writeOdfZip(DocumentType.CALC_ODS.mimeType, entries, outputStream)
    }

    private fun buildContentXml(workbook: CalcWorkbook): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>
<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
  xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
  xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
  xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
  office:version="1.2">
  <office:body>
    <office:spreadsheet>
""")

        for (sheet in workbook.sheets) {
            sb.append("      <table:table table:name=\"${escapeXml(sheet.name)}\">\n")
            val maxCol = sheet.maxUsedCol
            val maxRow = sheet.maxUsedRow

            for (r in 0..maxRow) {
                sb.append("        <table:table-row>\n")
                for (c in 0..maxCol) {
                    val cell = sheet.getCell(CellPosition(c, r))
                    if (cell != null) {
                        if (cell.isFormula) {
                            val f = cell.rawValue
                            val formulaOf = if (f.startsWith("=")) "of:$f" else "of:=$f"
                            sb.append("          <table:table-cell table:formula=\"${escapeXml(formulaOf)}\" office:value-type=\"string\"><text:p>${escapeXml(cell.displayValue)}</text:p></table:table-cell>\n")
                        } else if (cell.type == CellType.NUMBER) {
                            val n = cell.numericValue ?: cell.rawValue
                            sb.append("          <table:table-cell office:value-type=\"float\" office:value=\"$n\"><text:p>$n</text:p></table:table-cell>\n")
                        } else {
                            sb.append("          <table:table-cell office:value-type=\"string\"><text:p>${escapeXml(cell.displayValue)}</text:p></table:table-cell>\n")
                        }
                    } else {
                        sb.append("          <table:table-cell/>\n")
                    }
                }
                sb.append("        </table:table-row>\n")
            }
            sb.append("      </table:table>\n")
        }

        sb.append("""    </office:spreadsheet>
  </office:body>
</office:document-content>""")
        return sb.toString()
    }
}
