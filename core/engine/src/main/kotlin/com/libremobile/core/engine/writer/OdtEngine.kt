package com.libremobile.core.engine.writer

import com.libremobile.core.engine.util.XmlStreamHelper
import com.libremobile.core.engine.util.XmlStreamHelper.escapeXml
import com.libremobile.core.engine.util.XmlStreamHelper.findChildElements
import com.libremobile.core.engine.util.XmlStreamHelper.findFirstChildElement
import com.libremobile.core.engine.util.ZipHelper
import com.libremobile.core.model.common.DocumentMetadata
import com.libremobile.core.model.common.DocumentType
import com.libremobile.core.model.writer.*
import java.io.InputStream
import java.io.OutputStream
import org.w3c.dom.Element

object OdtEngine {

    fun readOdt(inputStream: InputStream): WriterDocument {
        val entries = ZipHelper.readZip(inputStream)
        val contentBytes = entries["content.xml"]
            ?: throw IllegalArgumentException("Invalid ODT: missing content.xml")

        val doc = XmlStreamHelper.parseXml(contentBytes)
        val bodyElem = doc.documentElement.findFirstChildElement("body")
        val textElem = bodyElem?.findFirstChildElement("text")
            ?: return WriterDocument()

        val blocks = mutableListOf<WriterBlock>()
        val children = textElem.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element) {
                val tag = node.localName ?: node.tagName.substringAfter(":")
                when (tag) {
                    "p", "h" -> blocks.add(parseParagraph(node))
                    "table" -> blocks.add(parseTable(node))
                }
            }
        }

        return WriterDocument(
            metadata = DocumentMetadata(title = "Imported ODT"),
            blocks = if (blocks.isEmpty()) listOf(Paragraph.simple("")) else blocks
        )
    }

    private fun parseParagraph(pElem: Element): Paragraph {
        val runs = mutableListOf<Run>()
        val children = pElem.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element) {
                val tag = node.localName ?: node.tagName.substringAfter(":")
                if (tag == "span") {
                    val text = node.textContent ?: ""
                    runs.add(Run(text = text))
                }
            } else if (node.nodeType == org.w3c.dom.Node.TEXT_NODE) {
                val text = node.textContent ?: ""
                if (text.isNotEmpty()) {
                    runs.add(Run(text = text))
                }
            }
        }

        if (runs.isEmpty()) {
            val fullText = pElem.textContent ?: ""
            runs.add(Run(text = fullText))
        }

        val tag = pElem.localName ?: pElem.tagName.substringAfter(":")
        val headingLevel = if (tag == "h") {
            val outlineLevel = pElem.getAttribute("text:outline-level").toIntOrNull() ?: 1
            when (outlineLevel) {
                1 -> HeadingLevel.H1
                2 -> HeadingLevel.H2
                3 -> HeadingLevel.H3
                else -> HeadingLevel.H4
            }
        } else HeadingLevel.NONE

        return Paragraph(runs = runs, style = ParagraphStyle(headingLevel = headingLevel))
    }

    private fun parseTable(tableElem: Element): TableNode {
        val rows = mutableListOf<TableRowNode>()
        for (rowElem in tableElem.findChildElements("table-row")) {
            val cells = mutableListOf<TableCellNode>()
            for (cellElem in rowElem.findChildElements("table-cell")) {
                val paragraphs = cellElem.findChildElements("p").map { parseParagraph(it) }
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

    fun writeOdt(document: WriterDocument, outputStream: OutputStream) {
        val entries = mutableMapOf<String, ByteArray>()

        entries["META-INF/manifest.xml"] = """<?xml version="1.0" encoding="UTF-8"?>
<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2">
  <manifest:file-entry manifest:full-path="/" manifest:version="1.2" manifest:media-type="${DocumentType.WRITER_ODT.mimeType}"/>
  <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
  <manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/>
</manifest:manifest>""".toByteArray(Charsets.UTF_8)

        entries["styles.xml"] = """<?xml version="1.0" encoding="UTF-8"?>
<office:document-styles xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
  xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
  xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
  office:version="1.2">
</office:document-styles>""".toByteArray(Charsets.UTF_8)

        val contentXml = buildContentXml(document)
        entries["content.xml"] = contentXml.toByteArray(Charsets.UTF_8)

        ZipHelper.writeOdfZip(DocumentType.WRITER_ODT.mimeType, entries, outputStream)
    }

    private fun buildContentXml(document: WriterDocument): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>
<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
  xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
  xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
  xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
  xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
  office:version="1.2">
  <office:automatic-styles>
    <style:style style:name="Bold" style:family="text"><style:text-properties fo:font-weight="bold"/></style:style>
    <style:style style:name="Italic" style:family="text"><style:text-properties fo:font-style="italic"/></style:style>
    <style:style style:name="Underline" style:family="text"><style:text-properties style:text-underline-style="solid"/></style:style>
    <style:style style:name="Center" style:family="paragraph"><style:paragraph-properties fo:text-align="center"/></style:style>
    <style:style style:name="Right" style:family="paragraph"><style:paragraph-properties fo:text-align="end"/></style:style>
  </office:automatic-styles>
  <office:body>
    <office:text>
""")

        for (block in document.blocks) {
            when (block) {
                is Paragraph -> appendParagraphXml(sb, block)
                is TableNode -> appendTableXml(sb, block)
            }
        }

        sb.append("""    </office:text>
  </office:body>
</office:document-content>""")
        return sb.toString()
    }

    private fun appendParagraphXml(sb: StringBuilder, paragraph: Paragraph) {
        val tagName = if (paragraph.style.headingLevel != HeadingLevel.NONE) {
            "text:h text:outline-level=\"${paragraph.style.headingLevel.level}\""
        } else {
            val styleAttr = when (paragraph.style.alignment) {
                TextAlignment.CENTER -> " text:style-name=\"Center\""
                TextAlignment.RIGHT -> " text:style-name=\"Right\""
                else -> ""
            }
            "text:p$styleAttr"
        }

        sb.append("      <$tagName>")
        for (run in paragraph.runs) {
            val styleName = when {
                run.style.bold -> "text:style-name=\"Bold\""
                run.style.italic -> "text:style-name=\"Italic\""
                run.style.underline -> "text:style-name=\"Underline\""
                else -> ""
            }
            if (styleName.isNotEmpty()) {
                sb.append("<text:span $styleName>${escapeXml(run.text)}</text:span>")
            } else {
                sb.append(escapeXml(run.text))
            }
        }
        val closeTag = if (paragraph.style.headingLevel != HeadingLevel.NONE) "text:h" else "text:p"
        sb.append("</$closeTag>\n")
    }

    private fun appendTableXml(sb: StringBuilder, table: TableNode) {
        sb.append("      <table:table>\n")
        for (row in table.rows) {
            sb.append("        <table:table-row>\n")
            for (cell in row.cells) {
                sb.append("          <table:table-cell office:value-type=\"string\">\n")
                for (p in cell.paragraphs) {
                    appendParagraphXml(sb, p)
                }
                sb.append("          </table:table-cell>\n")
            }
            sb.append("        </table:table-row>\n")
        }
        sb.append("      </table:table>\n")
    }
}
