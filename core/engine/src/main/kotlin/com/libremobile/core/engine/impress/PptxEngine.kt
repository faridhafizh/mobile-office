package com.libremobile.core.engine.impress

import com.libremobile.core.engine.util.XmlStreamHelper
import com.libremobile.core.engine.util.XmlStreamHelper.escapeXml
import com.libremobile.core.engine.util.XmlStreamHelper.findChildElements
import com.libremobile.core.engine.util.XmlStreamHelper.findFirstChildElement
import com.libremobile.core.engine.util.ZipHelper
import com.libremobile.core.model.common.ColorRgba
import com.libremobile.core.model.common.DocumentMetadata
import com.libremobile.core.model.common.Rect2D
import com.libremobile.core.model.impress.*
import com.libremobile.core.model.writer.Paragraph
import com.libremobile.core.model.writer.Run
import com.libremobile.core.model.writer.TextStyle
import java.io.InputStream
import java.io.OutputStream
import org.w3c.dom.Element

object PptxEngine {

    // 1 pt = 12700 EMUs in OpenXML DrawingML
    private const val EMU_PER_PT = 12700f

    fun readPptx(inputStream: InputStream): PresentationDocument {
        val entries = ZipHelper.readZip(inputStream)

        val slides = mutableListOf<Slide>()
        var slideIndex = 1

        while (entries.containsKey("ppt/slides/slide$slideIndex.xml")) {
            val slideBytes = entries["ppt/slides/slide$slideIndex.xml"]!!
            val slideDoc = XmlStreamHelper.parseXml(slideBytes)
            val cSld = slideDoc.documentElement.findFirstChildElement("cSld")
            val spTree = cSld?.findFirstChildElement("spTree")

            val shapes = mutableListOf<ShapeNode>()
            if (spTree != null) {
                val spElements = spTree.findChildElements("sp")
                for (spElem in spElements) {
                    val spPr = spElem.findFirstChildElement("spPr")
                    val xfrm = spPr?.findFirstChildElement("xfrm")
                    val off = xfrm?.findFirstChildElement("off")
                    val ext = xfrm?.findFirstChildElement("ext")

                    val x = (off?.getAttribute("x")?.toFloatOrNull() ?: 0f) / EMU_PER_PT
                    val y = (off?.getAttribute("y")?.toFloatOrNull() ?: 0f) / EMU_PER_PT
                    val w = (ext?.getAttribute("cx")?.toFloatOrNull() ?: 200f * EMU_PER_PT) / EMU_PER_PT
                    val h = (ext?.getAttribute("cy")?.toFloatOrNull() ?: 100f * EMU_PER_PT) / EMU_PER_PT

                    val bounds = Rect2D(x, y, x + w, y + h)

                    // Text Body
                    val txBody = spElem.findFirstChildElement("txBody")
                    val paragraphs = mutableListOf<Paragraph>()
                    if (txBody != null) {
                        for (pElem in txBody.findChildElements("p")) {
                            val runs = mutableListOf<Run>()
                            for (rElem in pElem.findChildElements("r")) {
                                val tElem = rElem.findFirstChildElement("t")
                                if (tElem != null) {
                                    val text = tElem.textContent ?: ""
                                    runs.add(Run(text = text))
                                }
                            }
                            if (runs.isNotEmpty()) {
                                paragraphs.add(Paragraph(runs = runs))
                            }
                        }
                    }

                    val textFrame = if (paragraphs.isNotEmpty()) TextFrameNode(paragraphs = paragraphs) else null
                    shapes.add(
                        ShapeNode(
                            type = ShapeType.TEXT_BOX,
                            bounds = bounds,
                            textFrame = textFrame
                        )
                    )
                }
            }

            slides.add(
                Slide(
                    slideNumber = slideIndex,
                    shapes = shapes
                )
            )
            slideIndex++
        }

        return PresentationDocument(
            metadata = DocumentMetadata(title = "Imported PPTX"),
            slides = if (slides.isEmpty()) listOf(Slide()) else slides
        )
    }

    fun writePptx(document: PresentationDocument, outputStream: OutputStream) {
        val entries = mutableMapOf<String, ByteArray>()

        entries["[Content_Types].xml"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
  ${document.slides.indices.joinToString("\n  ") { """<Override PartName="/ppt/slides/slide${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""" }}
</Types>""".toByteArray(Charsets.UTF_8)

        entries["_rels/.rels"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)

        // ppt/_rels/presentation.xml.rels
        val presRelsSb = StringBuilder()
        presRelsSb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
""")
        for (i in document.slides.indices) {
            val rId = "rId${i + 1}"
            presRelsSb.append("  <Relationship Id=\"$rId\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide${i + 1}.xml\"/>\n")
        }
        presRelsSb.append("</Relationships>")
        entries["ppt/_rels/presentation.xml.rels"] = presRelsSb.toString().toByteArray(Charsets.UTF_8)

        // ppt/presentation.xml
        val sldSzW = (document.slideWidthPt * EMU_PER_PT).toLong()
        val sldSzH = (document.slideHeightPt * EMU_PER_PT).toLong()
        val presSb = StringBuilder()
        presSb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:sldIdLst>
""")
        for (i in document.slides.indices) {
            val sldId = 256 + i
            val rId = "rId${i + 1}"
            presSb.append("    <p:sldId id=\"$sldId\" r:id=\"$rId\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"/>\n")
        }
        presSb.append("""  </p:sldIdLst>
  <p:sldSz cx="$sldSzW" cy="$sldSzH"/>
</p:presentation>""")
        entries["ppt/presentation.xml"] = presSb.toString().toByteArray(Charsets.UTF_8)

        // Slides
        for (i in document.slides.indices) {
            val slide = document.slides[i]
            val slideXml = buildSlideXml(slide, i + 1)
            entries["ppt/slides/slide${i + 1}.xml"] = slideXml.toByteArray(Charsets.UTF_8)
        }

        ZipHelper.writeZip(entries, outputStream)
    }

    private fun buildSlideXml(slide: Slide, slideNumber: Int): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:spTree>
      <p:nvGrpSpPr>
        <p:cNvPr id="1" name=""/>
        <p:cNvGrpSpPr/>
        <p:nvPr/>
      </p:nvGrpSpPr>
      <p:grpSpPr>
        <a:xfrm>
          <a:off x="0" y="0"/>
          <a:ext cx="0" cy="0"/>
          <a:chOff x="0" y="0"/>
          <a:chExt cx="0" cy="0"/>
        </a:xfrm>
      </p:grpSpPr>
""")

        var shapeId = 2
        for (shape in slide.shapes) {
            val offX = (shape.bounds.left * EMU_PER_PT).toLong()
            val offY = (shape.bounds.top * EMU_PER_PT).toLong()
            val extX = (shape.bounds.width * EMU_PER_PT).toLong()
            val extY = (shape.bounds.height * EMU_PER_PT).toLong()

            sb.append("""      <p:sp>
        <p:nvSpPr>
          <p:cNvPr id="$shapeId" name="Shape $shapeId"/>
          <p:cNvSpPr/>
          <p:nvPr/>
        </p:nvSpPr>
        <p:spPr>
          <a:xfrm>
            <a:off x="$offX" y="$offY"/>
            <a:ext cx="$extX" cy="$extY"/>
          </a:xfrm>
          <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
""")
            val fillColor = shape.fill.color
            if (fillColor != null) {
                val hex = fillColor.toHex().removePrefix("#")
                sb.append("          <a:solidFill><a:srgbClr val=\"$hex\"/></a:solidFill>\n")
            } else {
                sb.append("          <a:noFill/>\n")
            }

            sb.append("        </p:spPr>\n")

            // Text Body
            val tf = shape.textFrame
            if (tf != null) {
                sb.append("""        <p:txBody>
          <a:bodyPr/>
          <a:lstStyle/>
""")
                for (p in tf.paragraphs) {
                    sb.append("          <a:p>\n")
                    for (r in p.runs) {
                        sb.append("            <a:r>\n")
                        val szHundredthsPt = (r.style.fontSizePt * 100).toInt()
                        sb.append("              <a:rPr sz=\"$szHundredthsPt\" b=\"${if (r.style.bold) 1 else 0}\" i=\"${if (r.style.italic) 1 else 0}\"/>\n")
                        sb.append("              <a:t>${escapeXml(r.text)}</a:t>\n")
                        sb.append("            </a:r>\n")
                    }
                    sb.append("          </a:p>\n")
                }
                sb.append("        </p:txBody>\n")
            }
            sb.append("      </p:sp>\n")
            shapeId++
        }

        sb.append("""    </p:spTree>
  </p:cSld>
</p:sld>""")
        return sb.toString()
    }
}
