package com.libremobile.core.engine.pdf

import com.libremobile.core.model.calc.CalcWorkbook
import com.libremobile.core.model.calc.CellPosition
import com.libremobile.core.model.impress.PresentationDocument
import com.libremobile.core.model.writer.Paragraph
import com.libremobile.core.model.writer.WriterDocument
import java.io.ByteArrayOutputStream
import java.io.OutputStream

object PdfExporter {

    fun exportWriter(document: WriterDocument, outputStream: OutputStream) {
        val pageW = (document.pageSetup.widthMm * 72f / 25.4f).toInt()
        val pageH = (document.pageSetup.heightMm * 72f / 25.4f).toInt()

        val textLines = mutableListOf<String>()
        for (block in document.blocks) {
            when (block) {
                is Paragraph -> textLines.add(block.plainText)
                is com.libremobile.core.model.writer.TableNode -> {
                    for (row in block.rows) {
                        val rowStr = row.cells.joinToString(" | ") { it.paragraphs.joinToString(" ") { p -> p.plainText } }
                        textLines.add("[ $rowStr ]")
                    }
                }
            }
        }

        val pdfBytes = renderSimplePdfPages(
            lines = textLines,
            pageWidth = pageW,
            pageHeight = pageH,
            fontSize = 12,
            margin = 54
        )
        outputStream.write(pdfBytes)
    }

    fun exportCalc(workbook: CalcWorkbook, outputStream: OutputStream) {
        val sheet = workbook.sheets.firstOrNull() ?: return
        val maxCol = sheet.maxUsedCol
        val maxRow = sheet.maxUsedRow

        val lines = mutableListOf<String>()
        lines.add("Sheet: ${sheet.name}")
        lines.add("--------------------------------------------------------------------------------")

        for (r in 0..maxRow) {
            val rowTokens = mutableListOf<String>()
            for (c in 0..maxCol) {
                val cell = sheet.getCell(CellPosition(c, r))
                rowTokens.add(cell?.displayValue ?: "")
            }
            if (rowTokens.any { it.isNotEmpty() }) {
                lines.add(rowTokens.joinToString("\t| "))
            }
        }

        val pdfBytes = renderSimplePdfPages(
            lines = lines,
            pageWidth = 792, // Letter landscape / wide
            pageHeight = 612,
            fontSize = 10,
            margin = 40
        )
        outputStream.write(pdfBytes)
    }

    fun exportImpress(document: PresentationDocument, outputStream: OutputStream) {
        val slideW = document.slideWidthPt.toInt()
        val slideH = document.slideHeightPt.toInt()

        val pageStreams = mutableListOf<ByteArray>()

        for ((idx, slide) in document.slides.withIndex()) {
            val contentSb = StringBuilder()
            contentSb.append("q\n")

            // Title banner
            contentSb.append("BT\n/F1 18 Tf\n50 ${slideH - 50} Td\n(Slide ${idx + 1}) Tj\nET\n")

            var y = slideH - 90
            for (shape in slide.shapes) {
                val tf = shape.textFrame
                if (tf != null) {
                    for (p in tf.paragraphs) {
                        val text = escapePdfString(p.plainText)
                        contentSb.append("BT\n/F1 14 Tf\n50 $y Td\n($text) Tj\nET\n")
                        y -= 22
                    }
                }
            }

            contentSb.append("Q\n")
            pageStreams.add(contentSb.toString().toByteArray(Charsets.US_ASCII))
        }

        val pdf = assemblePdfDocument(pageStreams, slideW, slideH)
        outputStream.write(pdf)
    }

    private fun renderSimplePdfPages(
        lines: List<String>,
        pageWidth: Int,
        pageHeight: Int,
        fontSize: Int,
        margin: Int
    ): ByteArray {
        val lineHeight = (fontSize * 1.4).toInt()
        val usableHeight = pageHeight - 2 * margin
        val linesPerPage = (usableHeight / lineHeight).coerceAtLeast(1)

        val pages = lines.chunked(linesPerPage)
        val actualPages = if (pages.isEmpty()) listOf(listOf("")) else pages

        val pageStreams = mutableListOf<ByteArray>()
        for (pageLines in actualPages) {
            val sb = StringBuilder()
            sb.append("q\n")
            var currentY = pageHeight - margin - fontSize
            for (line in pageLines) {
                val escaped = escapePdfString(line)
                sb.append("BT\n/F1 $fontSize Tf\n$margin $currentY Td\n($escaped) Tj\nET\n")
                currentY -= lineHeight
            }
            sb.append("Q\n")
            pageStreams.add(sb.toString().toByteArray(Charsets.US_ASCII))
        }

        return assemblePdfDocument(pageStreams, pageWidth, pageHeight)
    }

    private fun assemblePdfDocument(
        pageStreams: List<ByteArray>,
        pageWidth: Int,
        pageHeight: Int
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()

        fun writeString(str: String) {
            out.write(str.toByteArray(Charsets.US_ASCII))
        }

        // 1. PDF Header
        writeString("%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n")

        val numPages = pageStreams.size
        // Object IDs:
        // 1: Catalog
        // 2: Pages
        // 3: Font
        // 4 .. 4 + numPages - 1: Page objects
        // 4 + numPages .. 4 + 2 * numPages - 1: Content stream objects

        val catalogObj = 1
        val pagesObj = 2
        val fontObj = 3
        val firstPageObj = 4
        val firstContentObj = firstPageObj + numPages

        // 1: Catalog
        offsets.add(out.size())
        writeString("$catalogObj 0 obj\n<< /Type /Catalog /Pages $pagesObj 0 R >>\nendobj\n")

        // 2: Pages
        offsets.add(out.size())
        val kids = (0 until numPages).joinToString(" ") { "${firstPageObj + it} 0 R" }
        writeString("$pagesObj 0 obj\n<< /Type /Pages /Kids [ $kids ] /Count $numPages >>\nendobj\n")

        // 3: Font
        offsets.add(out.size())
        writeString("$fontObj 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n")

        // Page objects
        for (i in 0 until numPages) {
            val pageNum = firstPageObj + i
            val contentNum = firstContentObj + i
            offsets.add(out.size())
            writeString("$pageNum 0 obj\n<< /Type /Page /Parent $pagesObj 0 R /MediaBox [ 0 0 $pageWidth $pageHeight ] /Contents $contentNum 0 R /Resources << /Font << /F1 $fontObj 0 R >> >> >>\nendobj\n")
        }

        // Content stream objects
        for (i in 0 until numPages) {
            val contentNum = firstContentObj + i
            val streamBytes = pageStreams[i]
            offsets.add(out.size())
            writeString("$contentNum 0 obj\n<< /Length ${streamBytes.size} >>\nstream\n")
            out.write(streamBytes)
            writeString("\nendstream\nendobj\n")
        }

        // Xref table
        val xrefOffset = out.size()
        val totalObjs = offsets.size + 1
        writeString("xref\n0 $totalObjs\n")
        writeString("0000000000 65535 f \n")
        for (offset in offsets) {
            writeString(String.format("%010d 00000 n \n", offset))
        }

        // Trailer
        writeString("trailer\n<< /Size $totalObjs /Root $catalogObj 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")

        return out.toByteArray()
    }

    private fun escapePdfString(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("\r", " ")
            .replace("\n", " ")
    }
}
