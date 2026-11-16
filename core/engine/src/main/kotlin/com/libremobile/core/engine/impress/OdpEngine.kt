package com.libremobile.core.engine.impress

import com.libremobile.core.engine.util.XmlStreamHelper
import com.libremobile.core.engine.util.XmlStreamHelper.escapeXml
import com.libremobile.core.engine.util.XmlStreamHelper.findChildElements
import com.libremobile.core.engine.util.XmlStreamHelper.findFirstChildElement
import com.libremobile.core.engine.util.ZipHelper
import com.libremobile.core.model.common.ColorRgba
import com.libremobile.core.model.common.DocumentMetadata
import com.libremobile.core.model.common.DocumentType
import com.libremobile.core.model.common.Rect2D
import com.libremobile.core.model.impress.*
import com.libremobile.core.model.writer.Paragraph
import com.libremobile.core.model.writer.Run
import java.io.InputStream
import java.io.OutputStream
import org.w3c.dom.Element

object OdpEngine {

    fun readOdp(inputStream: InputStream): PresentationDocument {
        val entries = ZipHelper.readZip(inputStream)
        val contentBytes = entries["content.xml"]
            ?: throw IllegalArgumentException("Invalid ODP: missing content.xml")

        val doc = XmlStreamHelper.parseXml(contentBytes)
        val bodyElem = doc.documentElement.findFirstChildElement("body")
        val presElem = bodyElem?.findFirstChildElement("presentation")
            ?: return PresentationDocument()

        val slides = mutableListOf<Slide>()
        val pageElements = presElem.findChildElements("page")

        var slideNum = 1
        for (pageElem in pageElements) {
            val shapes = mutableListOf<ShapeNode>()
            val frameElements = pageElem.findChildElements("frame")

            for (frameElem in frameElements) {
                val textBoxElem = frameElem.findFirstChildElement("text-box")
                val paragraphs = mutableListOf<Paragraph>()
                if (textBoxElem != null) {
                    for (pElem in textBoxElem.findChildElements("p")) {
                        val text = pElem.textContent ?: ""
                        paragraphs.add(Paragraph.simple(text))
                    }
                }

                shapes.add(
                    ShapeNode(
                        type = ShapeType.TEXT_BOX,
                        textFrame = if (paragraphs.isNotEmpty()) TextFrameNode(paragraphs) else null
                    )
                )
            }

            slides.add(Slide(slideNumber = slideNum, shapes = shapes))
            slideNum++
        }

        return PresentationDocument(
            metadata = DocumentMetadata(title = "Imported ODP"),
            slides = if (slides.isEmpty()) listOf(Slide()) else slides
        )
    }

    fun writeOdp(document: PresentationDocument, outputStream: OutputStream) {
        val entries = mutableMapOf<String, ByteArray>()

        entries["META-INF/manifest.xml"] = """<?xml version="1.0" encoding="UTF-8"?>
<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2">
  <manifest:file-entry manifest:full-path="/" manifest:version="1.2" manifest:media-type="${DocumentType.IMPRESS_ODP.mimeType}"/>
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

        ZipHelper.writeOdfZip(DocumentType.IMPRESS_ODP.mimeType, entries, outputStream)
    }

    private fun buildContentXml(document: PresentationDocument): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>
<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
  xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
  xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
  xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
  xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"
  xmlns:presentation="urn:oasis:names:tc:opendocument:xmlns:presentation:1.0"
  office:version="1.2">
  <office:body>
    <office:presentation>
""")

        for ((idx, slide) in document.slides.withIndex()) {
            val pageName = "page${idx + 1}"
            sb.append("      <draw:page draw:name=\"$pageName\">\n")
            for (shape in slide.shapes) {
                val svgX = "${(shape.bounds.left / 72f) * 2.54f}cm"
                val svgY = "${(shape.bounds.top / 72f) * 2.54f}cm"
                val svgW = "${(shape.bounds.width / 72f) * 2.54f}cm"
                val svgH = "${(shape.bounds.height / 72f) * 2.54f}cm"

                sb.append("        <draw:frame svg:x=\"$svgX\" svg:y=\"$svgY\" svg:width=\"$svgW\" svg:height=\"$svgH\">\n")
                sb.append("          <draw:text-box>\n")
                val tf = shape.textFrame
                if (tf != null) {
                    for (p in tf.paragraphs) {
                        sb.append("            <text:p>${escapeXml(p.plainText)}</text:p>\n")
                    }
                }
                sb.append("          </draw:text-box>\n")
                sb.append("        </draw:frame>\n")
            }
            sb.append("      </draw:page>\n")
        }

        sb.append("""    </office:presentation>
  </office:body>
</office:document-content>""")
        return sb.toString()
    }
}
