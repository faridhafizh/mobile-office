package com.libremobile.core.engine.writer

import com.libremobile.core.engine.util.XmlStreamHelper
import com.libremobile.core.engine.util.XmlStreamHelper.escapeXml
import com.libremobile.core.engine.util.XmlStreamHelper.findChildElements
import com.libremobile.core.engine.util.XmlStreamHelper.findFirstChildElement
import com.libremobile.core.engine.util.ZipHelper
import com.libremobile.core.model.common.ColorRgba
import com.libremobile.core.model.common.DocumentMetadata
import com.libremobile.core.model.writer.*
import java.io.InputStream
import java.io.OutputStream
import org.w3c.dom.Element

object DocxEngine {

    fun readDocx(inputStream: InputStream): WriterDocument {
        val entries = ZipHelper.readZip(inputStream)
        val docXmlBytes = entries["word/document.xml"]
            ?: throw IllegalArgumentException("Invalid DOCX: missing word/document.xml")

        val doc = XmlStreamHelper.parseXml(docXmlBytes)
        val bodyElem = doc.documentElement.findFirstChildElement("body")
            ?: return WriterDocument()

        val blocks = mutableListOf<WriterBlock>()

        val childNodes = bodyElem.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is Element) {
                val tag = node.localName ?: node.tagName.substringAfter(":")
                when (tag) {
                    "p" -> blocks.add(parseParagraph(node))
                    "tbl" -> blocks.add(parseTable(node))
                }
            }
        }

        return WriterDocument(
            metadata = DocumentMetadata(title = "Imported DOCX"),
            blocks = if (blocks.isEmpty()) listOf(Paragraph.simple("")) else blocks
        )
    }

    private fun parseParagraph(pElem: Element): Paragraph {
        var style = ParagraphStyle()
        val pPr = pElem.findFirstChildElement("pPr")
        if (pPr != null) {
            val jc = pPr.findFirstChildElement("jc")?.getAttribute("w:val")
            val align = when (jc) {
                "center" -> TextAlignment.CENTER
                "right" -> TextAlignment.RIGHT
                "both" -> TextAlignment.JUSTIFY
                else -> TextAlignment.LEFT
            }

            val pStyle = pPr.findFirstChildElement("pStyle")?.getAttribute("w:val")
            val heading = when (pStyle) {
                "Heading1", "1" -> HeadingLevel.H1
                "Heading2", "2" -> HeadingLevel.H2
                "Heading3", "3" -> HeadingLevel.H3
                "Heading4", "4" -> HeadingLevel.H4
                else -> HeadingLevel.NONE
            }

            val numPr = pPr.findFirstChildElement("numPr")
            val listType = if (numPr != null) ListType.BULLET else ListType.NONE

            style = style.copy(alignment = align, headingLevel = heading, listType = listType)
        }

        val runs = mutableListOf<Run>()
        val rElements = pElem.findChildElements("r")
        for (rElem in rElements) {
            val textElem = rElem.findFirstChildElement("t") ?: continue
            val text = textElem.textContent ?: ""

            var textStyle = TextStyle()
            val rPr = rElem.findFirstChildElement("rPr")
            if (rPr != null) {
                val isBold = rPr.findFirstChildElement("b") != null
                val isItalic = rPr.findFirstChildElement("i") != null
                val isUnderline = rPr.findFirstChildElement("u") != null
                val isStrike = rPr.findFirstChildElement("strike") != null

                val szVal = rPr.findFirstChildElement("sz")?.getAttribute("w:val")?.toFloatOrNull()
                val fontSize = if (szVal != null) szVal / 2f else 11f

                val colorVal = rPr.findFirstChildElement("color")?.getAttribute("w:val")
                val color = if (colorVal != null && colorVal.length == 6) {
                    ColorRgba.fromHex("#$colorVal")
                } else ColorRgba.BLACK

                textStyle = textStyle.copy(
                    bold = isBold,
                    italic = isItalic,
                    underline = isUnderline,
                    strikethrough = isStrike,
                    fontSizePt = fontSize,
                    color = color
                )
            }
            runs.add(Run(text = text, style = textStyle))
        }

        return Paragraph(runs = runs, style = style)
    }

    private fun parseTable(tblElem: Element): TableNode {
        val rows = mutableListOf<TableRowNode>()
        for (trElem in tblElem.findChildElements("tr")) {
            val cells = mutableListOf<TableCellNode>()
            for (tcElem in trElem.findChildElements("tc")) {
                val paragraphs = tcElem.findChildElements("p").map { parseParagraph(it) }
                cells.add(
                    TableCellNode(
                        paragraphs = if (paragraphs.isEmpty()) listOf(Paragraph.simple("")) else paragraphs
                    )
                )
            }
            rows.add(TableRowNode(cells = cells))
        }
        return TableNode(rows = rows)
    }

    fun writeDocx(document: WriterDocument, outputStream: OutputStream) {
        val entries = mutableMapOf<String, ByteArray>()

        entries["[Content_Types].xml"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>""".toByteArray(Charsets.UTF_8)

        entries["_rels/.rels"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)

        entries["word/_rels/document.xml.rels"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)

        entries["word/styles.xml"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="Liberation Serif" w:hAnsi="Liberation Serif"/>
        <w:sz w:val="22"/>
      </w:rPr>
    </w:rPrDefault>
  </w:docDefaults>
</w:styles>""".toByteArray(Charsets.UTF_8)

        val docXml = buildDocumentXml(document)
        entries["word/document.xml"] = docXml.toByteArray(Charsets.UTF_8)

        ZipHelper.writeZip(entries, outputStream)
    }

    private fun buildDocumentXml(document: WriterDocument): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
""")

        for (block in document.blocks) {
            when (block) {
                is Paragraph -> appendParagraphXml(sb, block)
                is TableNode -> appendTableXml(sb, block)
            }
        }

        // Section / page setup
        val wDxa = (document.pageSetup.widthMm * 56.7f).toInt()
        val hDxa = (document.pageSetup.heightMm * 56.7f).toInt()
        sb.append("""    <w:sectPr>
      <w:pgSz w:w="$wDxa" w:h="$hDxa" ${if (document.pageSetup.isLandscape) "w:orient=\"landscape\"" else ""}/>
      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/>
    </w:sectPr>
  </w:body>
</w:document>""")
        return sb.toString()
    }

    private fun appendParagraphXml(sb: StringBuilder, paragraph: Paragraph) {
        sb.append("    <w:p>\n")
        sb.append("      <w:pPr>\n")

        val jcVal = when (paragraph.style.alignment) {
            TextAlignment.CENTER -> "center"
            TextAlignment.RIGHT -> "right"
            TextAlignment.JUSTIFY -> "both"
            TextAlignment.LEFT -> "left"
        }
        sb.append("        <w:jc w:val=\"$jcVal\"/>\n")

        if (paragraph.style.headingLevel != HeadingLevel.NONE) {
            sb.append("        <w:pStyle w:val=\"Heading${paragraph.style.headingLevel.level}\"/>\n")
        }
        sb.append("      </w:pPr>\n")

        for (run in paragraph.runs) {
            sb.append("      <w:r>\n")
            sb.append("        <w:rPr>\n")
            if (run.style.bold) sb.append("          <w:b/>\n")
            if (run.style.italic) sb.append("          <w:i/>\n")
            if (run.style.underline) sb.append("          <w:u w:val=\"single\"/>\n")
            if (run.style.strikethrough) sb.append("          <w:strike/>\n")

            val szHalfPt = (run.style.fontSizePt * 2).toInt()
            sb.append("          <w:sz w:val=\"$szHalfPt\"/>\n")

            val colorHex = run.style.color.toHex().removePrefix("#")
            sb.append("          <w:color w:val=\"$colorHex\"/>\n")
            sb.append("        </w:rPr>\n")
            sb.append("        <w:t xml:space=\"preserve\">${escapeXml(run.text)}</w:t>\n")
            sb.append("      </w:r>\n")
        }

        sb.append("    </w:p>\n")
    }

    private fun appendTableXml(sb: StringBuilder, table: TableNode) {
        sb.append("    <w:tbl>\n")
        sb.append("""      <w:tblPr>
        <w:tblBorders>
          <w:top w:val="single" w:sz="4" w:space="0" w:color="CCCCCC"/>
          <w:left w:val="single" w:sz="4" w:space="0" w:color="CCCCCC"/>
          <w:bottom w:val="single" w:sz="4" w:space="0" w:color="CCCCCC"/>
          <w:right w:val="single" w:sz="4" w:space="0" w:color="CCCCCC"/>
          <w:insideH w:val="single" w:sz="4" w:space="0" w:color="E0E0E0"/>
          <w:insideV w:val="single" w:sz="4" w:space="0" w:color="E0E0E0"/>
        </w:tblBorders>
      </w:tblPr>
""")
        for (row in table.rows) {
            sb.append("      <w:tr>\n")
            for (cell in row.cells) {
                sb.append("        <w:tc>\n")
                val bg = cell.backgroundColor
                if (bg != null) {
                    val hex = bg.toHex().removePrefix("#")
                    sb.append("          <w:tcPr><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"$hex\"/></w:tcPr>\n")
                }
                for (p in cell.paragraphs) {
                    appendParagraphXml(sb, p)
                }
                sb.append("        </w:tc>\n")
            }
            sb.append("      </w:tr>\n")
        }
        sb.append("    </w:tbl>\n")
    }
}
