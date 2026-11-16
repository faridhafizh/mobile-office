package com.libremobile.core.engine.calc

import com.libremobile.core.engine.formula.FormulaEngine
import com.libremobile.core.engine.util.XmlStreamHelper
import com.libremobile.core.engine.util.XmlStreamHelper.escapeXml
import com.libremobile.core.engine.util.XmlStreamHelper.findChildElements
import com.libremobile.core.engine.util.XmlStreamHelper.findFirstChildElement
import com.libremobile.core.engine.util.ZipHelper
import com.libremobile.core.model.calc.*
import com.libremobile.core.model.common.DocumentMetadata
import java.io.InputStream
import java.io.OutputStream
import org.w3c.dom.Element

object XlsxEngine {

    fun readXlsx(inputStream: InputStream): CalcWorkbook {
        val entries = ZipHelper.readZip(inputStream)

        // 1. Read shared strings if present
        val sharedStrings = mutableListOf<String>()
        val sstBytes = entries["xl/sharedStrings.xml"]
        if (sstBytes != null) {
            val sstDoc = XmlStreamHelper.parseXml(sstBytes)
            val siElements = sstDoc.documentElement.findChildElements("si")
            for (si in siElements) {
                val tElem = si.findFirstChildElement("t")
                sharedStrings.add(tElem?.textContent ?: "")
            }
        }

        // 2. Read sheets from xl/worksheets/sheet1.xml (or more)
        val sheets = mutableListOf<Sheet>()
        var sheetIndex = 1
        while (entries.containsKey("xl/worksheets/sheet$sheetIndex.xml")) {
            val sheetBytes = entries["xl/worksheets/sheet$sheetIndex.xml"]!!
            val sheetDoc = XmlStreamHelper.parseXml(sheetBytes)
            val sheetData = sheetDoc.documentElement.findFirstChildElement("sheetData")

            val cells = mutableMapOf<CellPosition, CellData>()
            if (sheetData != null) {
                val rowElements = sheetData.findChildElements("row")
                for (rowElem in rowElements) {
                    val cElements = rowElem.findChildElements("c")
                    for (cElem in cElements) {
                        val ref = cElem.getAttribute("r")
                        val pos = if (ref.isNotEmpty()) CellPosition.fromA1String(ref) else CellPosition(0, 0)
                        val typeAttr = cElem.getAttribute("t")

                        val formulaElem = cElem.findFirstChildElement("f")
                        val valueElem = cElem.findFirstChildElement("v")

                        val rawValue: String
                        val evaluatedValue: String
                        val cellType: CellType

                        if (formulaElem != null) {
                            val fText = "=" + (formulaElem.textContent ?: "")
                            rawValue = fText
                            evaluatedValue = valueElem?.textContent ?: ""
                            cellType = CellType.FORMULA
                        } else if (typeAttr == "s") {
                            // shared string
                            val sIndex = valueElem?.textContent?.toIntOrNull() ?: -1
                            val text = if (sIndex in sharedStrings.indices) sharedStrings[sIndex] else ""
                            rawValue = text
                            evaluatedValue = text
                            cellType = CellType.STRING
                        } else if (typeAttr == "b") {
                            val boolVal = valueElem?.textContent == "1"
                            rawValue = if (boolVal) "TRUE" else "FALSE"
                            evaluatedValue = rawValue
                            cellType = CellType.BOOLEAN
                        } else {
                            val text = valueElem?.textContent ?: ""
                            rawValue = text
                            evaluatedValue = text
                            val num = text.toDoubleOrNull()
                            cellType = if (num != null) CellType.NUMBER else CellType.STRING
                        }

                        val numVal = evaluatedValue.toDoubleOrNull()
                        cells[pos] = CellData(
                            position = pos,
                            rawValue = rawValue,
                            evaluatedValue = evaluatedValue,
                            numericValue = numVal,
                            type = cellType
                        )
                    }
                }
            }

            sheets.add(Sheet(name = "Sheet$sheetIndex", cells = cells))
            sheetIndex++
        }

        val workbook = CalcWorkbook(
            metadata = DocumentMetadata(title = "Imported XLSX"),
            sheets = if (sheets.isEmpty()) listOf(Sheet()) else sheets
        )

        // Evaluate formulas on load
        return FormulaEngine(workbook).evaluateWorkbook()
    }

    fun writeXlsx(workbook: CalcWorkbook, outputStream: OutputStream) {
        val entries = mutableMapOf<String, ByteArray>()

        entries["[Content_Types].xml"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""".toByteArray(Charsets.UTF_8)

        entries["_rels/.rels"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)

        entries["xl/_rels/workbook.xml.rels"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)

        // Build shared strings table
        val sharedStrings = mutableListOf<String>()
        val stringIndexMap = mutableMapOf<String, Int>()

        val sheet = workbook.sheets.firstOrNull() ?: Sheet()
        for ((_, cell) in sheet.cells) {
            if (cell.type == CellType.STRING && !cell.isFormula && cell.rawValue.isNotEmpty()) {
                if (!stringIndexMap.containsKey(cell.rawValue)) {
                    stringIndexMap[cell.rawValue] = sharedStrings.size
                    sharedStrings.add(cell.rawValue)
                }
            }
        }

        // Shared strings xml
        val sstSb = StringBuilder()
        sstSb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${sharedStrings.size}" uniqueCount="${sharedStrings.size}">
""")
        for (str in sharedStrings) {
            sstSb.append("  <si><t xml:space=\"preserve\">${escapeXml(str)}</t></si>\n")
        }
        sstSb.append("</sst>")
        entries["xl/sharedStrings.xml"] = sstSb.toString().toByteArray(Charsets.UTF_8)

        // Workbook xml
        entries["xl/workbook.xml"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="${escapeXml(sheet.name)}" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>""".toByteArray(Charsets.UTF_8)

        // Styles xml
        entries["xl/styles.xml"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
  <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
  <borders count="1"><border><left/><right/><top/><bottom/></border></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>
</styleSheet>""".toByteArray(Charsets.UTF_8)

        // Sheet1 xml
        val sheetXml = buildSheetXml(sheet, stringIndexMap)
        entries["xl/worksheets/sheet1.xml"] = sheetXml.toByteArray(Charsets.UTF_8)

        ZipHelper.writeZip(entries, outputStream)
    }

    private fun buildSheetXml(sheet: Sheet, stringIndexMap: Map<String, Int>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>
""")

        val cellsByRow = sheet.cells.values.groupBy { it.position.row }.toSortedMap()
        for ((rowIndex, rowCells) in cellsByRow) {
            val rNum = rowIndex + 1
            sb.append("    <row r=\"$rNum\">\n")
            val sortedCells = rowCells.sortedBy { it.position.col }
            for (cell in sortedCells) {
                val ref = cell.position.a1String
                if (cell.isFormula) {
                    val formula = cell.rawValue.removePrefix("=")
                    sb.append("      <c r=\"$ref\"><f>${escapeXml(formula)}</f>")
                    if (cell.evaluatedValue.isNotEmpty()) {
                        sb.append("<v>${escapeXml(cell.evaluatedValue)}</v>")
                    }
                    sb.append("</c>\n")
                } else if (cell.type == CellType.NUMBER) {
                    val nv = cell.numericValue
                    val nStr = if (nv != null) {
                        if (nv % 1.0 == 0.0) nv.toLong().toString() else nv.toString()
                    } else cell.rawValue
                    sb.append("      <c r=\"$ref\" t=\"n\"><v>$nStr</v></c>\n")
                } else if (cell.type == CellType.BOOLEAN) {
                    val bVal = if (cell.rawValue.equals("TRUE", true)) "1" else "0"
                    sb.append("      <c r=\"$ref\" t=\"b\"><v>$bVal</v></c>\n")
                } else if (cell.type == CellType.STRING && cell.rawValue.isNotEmpty()) {
                    val sIndex = stringIndexMap[cell.rawValue] ?: 0
                    sb.append("      <c r=\"$ref\" t=\"s\"><v>$sIndex</v></c>\n")
                }
            }
            sb.append("    </row>\n")
        }

        sb.append("""  </sheetData>
</worksheet>""")
        return sb.toString()
    }
}
