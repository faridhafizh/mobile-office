package com.libremobile.core.engine

import com.google.common.truth.Truth.assertThat
import com.libremobile.core.engine.calc.CsvEngine
import com.libremobile.core.engine.calc.OdsEngine
import com.libremobile.core.engine.calc.XlsxEngine
import com.libremobile.core.engine.formula.FormulaEngine
import com.libremobile.core.engine.formula.FormulaValue
import com.libremobile.core.engine.impress.OdpEngine
import com.libremobile.core.engine.impress.PptxEngine
import com.libremobile.core.engine.pdf.PdfExporter
import com.libremobile.core.engine.writer.DocxEngine
import com.libremobile.core.engine.writer.OdtEngine
import com.libremobile.core.engine.writer.PlainTextEngine
import com.libremobile.core.model.calc.*
import com.libremobile.core.model.common.ColorRgba
import com.libremobile.core.model.impress.PresentationDocument
import com.libremobile.core.model.impress.ShapeNode
import com.libremobile.core.model.impress.Slide
import com.libremobile.core.model.impress.TextFrameNode
import com.libremobile.core.model.writer.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class EngineTests {

    @Test
    fun testFormulaEngineEvaluations() {
        val cells = mutableMapOf<CellPosition, CellData>()
        cells[CellPosition(0, 0)] = CellData(CellPosition(0, 0), rawValue = "10", evaluatedValue = "10", numericValue = 10.0, type = CellType.NUMBER) // A1
        cells[CellPosition(0, 1)] = CellData(CellPosition(0, 1), rawValue = "20", evaluatedValue = "20", numericValue = 20.0, type = CellType.NUMBER) // A2
        cells[CellPosition(0, 2)] = CellData(CellPosition(0, 2), rawValue = "30", evaluatedValue = "30", numericValue = 30.0, type = CellType.NUMBER) // A3
        cells[CellPosition(1, 0)] = CellData(CellPosition(1, 0), rawValue = "=SUM(A1:A3)", type = CellType.FORMULA) // B1
        cells[CellPosition(1, 1)] = CellData(CellPosition(1, 1), rawValue = "=AVERAGE(A1:A3)", type = CellType.FORMULA) // B2
        cells[CellPosition(1, 2)] = CellData(CellPosition(1, 2), rawValue = "=IF(A1>5, \"Greater\", \"Smaller\")", type = CellType.FORMULA) // B3
        cells[CellPosition(1, 3)] = CellData(CellPosition(1, 3), rawValue = "=A1*2 + A2", type = CellType.FORMULA) // B4

        val workbook = CalcWorkbook(sheets = listOf(Sheet(name = "Sheet1", cells = cells)))
        val evaluated = FormulaEngine(workbook).evaluateWorkbook()
        val evalSheet = evaluated.sheets[0]

        assertThat(evalSheet.getCell(CellPosition(1, 0))?.evaluatedValue).isEqualTo("60")
        assertThat(evalSheet.getCell(CellPosition(1, 1))?.evaluatedValue).isEqualTo("20")
        assertThat(evalSheet.getCell(CellPosition(1, 2))?.evaluatedValue).isEqualTo("Greater")
        assertThat(evalSheet.getCell(CellPosition(1, 3))?.evaluatedValue).isEqualTo("40")
    }

    @Test
    fun testDocxRoundTrip() {
        val originalDoc = WriterDocument(
            blocks = listOf(
                Paragraph.simple("Title Heading", style = ParagraphStyle(headingLevel = HeadingLevel.H1)),
                Paragraph(
                    runs = listOf(
                        Run(text = "Hello ", style = TextStyle(bold = true)),
                        Run(text = "LibreMobile!", style = TextStyle(italic = true, color = ColorRgba.BLUE))
                    ),
                    style = ParagraphStyle(alignment = TextAlignment.CENTER)
                ),
                TableNode(
                    rows = listOf(
                        TableRowNode(
                            cells = listOf(
                                TableCellNode(paragraphs = listOf(Paragraph.simple("Cell 1"))),
                                TableCellNode(paragraphs = listOf(Paragraph.simple("Cell 2")))
                            )
                        )
                    )
                )
            )
        )

        val out = ByteArrayOutputStream()
        DocxEngine.writeDocx(originalDoc, out)
        val bytes = out.toByteArray()
        assertThat(bytes.size).isGreaterThan(500)

        val readDoc = DocxEngine.readDocx(ByteArrayInputStream(bytes))
        assertThat(readDoc.blocks.size).isEqualTo(3)

        val p1 = readDoc.blocks[0] as Paragraph
        assertThat(p1.plainText).isEqualTo("Title Heading")
        assertThat(p1.style.headingLevel).isEqualTo(HeadingLevel.H1)

        val p2 = readDoc.blocks[1] as Paragraph
        assertThat(p2.plainText).isEqualTo("Hello LibreMobile!")
        assertThat(p2.style.alignment).isEqualTo(TextAlignment.CENTER)

        val tbl = readDoc.blocks[2] as TableNode
        assertThat(tbl.rows[0].cells[0].paragraphs[0].plainText).isEqualTo("Cell 1")
    }

    @Test
    fun testOdtRoundTrip() {
        val originalDoc = WriterDocument(
            blocks = listOf(
                Paragraph.simple("ODT Document Header", style = ParagraphStyle(headingLevel = HeadingLevel.H1)),
                Paragraph(
                    runs = listOf(
                        Run(text = "Formatted ", style = TextStyle(bold = true)),
                        Run(text = "OpenDocument text run.")
                    )
                )
            )
        )

        val out = ByteArrayOutputStream()
        OdtEngine.writeOdt(originalDoc, out)
        val bytes = out.toByteArray()
        assertThat(bytes.size).isGreaterThan(500)

        val readDoc = OdtEngine.readOdt(ByteArrayInputStream(bytes))
        assertThat(readDoc.blocks.size).isEqualTo(2)
        assertThat(readDoc.blocks[0] is Paragraph).isTrue()
        assertThat((readDoc.blocks[0] as Paragraph).plainText).contains("ODT Document Header")
    }

    @Test
    fun testXlsxRoundTrip() {
        val cells = mutableMapOf<CellPosition, CellData>()
        cells[CellPosition(0, 0)] = CellData(CellPosition(0, 0), rawValue = "Product", evaluatedValue = "Product", type = CellType.STRING)
        cells[CellPosition(1, 0)] = CellData(CellPosition(1, 0), rawValue = "Price", evaluatedValue = "Price", type = CellType.STRING)
        cells[CellPosition(0, 1)] = CellData(CellPosition(0, 1), rawValue = "Book", evaluatedValue = "Book", type = CellType.STRING)
        cells[CellPosition(1, 1)] = CellData(CellPosition(1, 1), rawValue = "25", evaluatedValue = "25", numericValue = 25.0, type = CellType.NUMBER)

        val originalWorkbook = CalcWorkbook(sheets = listOf(Sheet(name = "Sales", cells = cells)))

        val out = ByteArrayOutputStream()
        XlsxEngine.writeXlsx(originalWorkbook, out)
        val bytes = out.toByteArray()
        assertThat(bytes.size).isGreaterThan(500)

        val readWorkbook = XlsxEngine.readXlsx(ByteArrayInputStream(bytes))
        val readSheet = readWorkbook.sheets[0]

        assertThat(readSheet.getCell(CellPosition(0, 0))?.displayValue).isEqualTo("Product")
        assertThat(readSheet.getCell(CellPosition(1, 1))?.displayValue).isEqualTo("25")
    }

    @Test
    fun testOdsRoundTrip() {
        val cells = mutableMapOf<CellPosition, CellData>()
        cells[CellPosition(0, 0)] = CellData(CellPosition(0, 0), rawValue = "OpenDocument", evaluatedValue = "OpenDocument", type = CellType.STRING)
        cells[CellPosition(0, 1)] = CellData(CellPosition(0, 1), rawValue = "42", evaluatedValue = "42", numericValue = 42.0, type = CellType.NUMBER)

        val workbook = CalcWorkbook(sheets = listOf(Sheet(name = "Sheet1", cells = cells)))

        val out = ByteArrayOutputStream()
        OdsEngine.writeOds(workbook, out)
        val bytes = out.toByteArray()
        assertThat(bytes.size).isGreaterThan(500)

        val readWorkbook = OdsEngine.readOds(ByteArrayInputStream(bytes))
        assertThat(readWorkbook.sheets[0].getCell(CellPosition(0, 0))?.displayValue).isEqualTo("OpenDocument")
    }

    @Test
    fun testCsvEngine() {
        val cells = mutableMapOf<CellPosition, CellData>()
        cells[CellPosition(0, 0)] = CellData(CellPosition(0, 0), rawValue = "Name, Full", evaluatedValue = "Name, Full", type = CellType.STRING)
        cells[CellPosition(1, 0)] = CellData(CellPosition(1, 0), rawValue = "Score", evaluatedValue = "Score", type = CellType.STRING)
        cells[CellPosition(0, 1)] = CellData(CellPosition(0, 1), rawValue = "Alice \"The Best\"", evaluatedValue = "Alice \"The Best\"", type = CellType.STRING)
        cells[CellPosition(1, 1)] = CellData(CellPosition(1, 1), rawValue = "98.5", evaluatedValue = "98.5", numericValue = 98.5, type = CellType.NUMBER)

        val wb = CalcWorkbook(sheets = listOf(Sheet(cells = cells)))
        val out = ByteArrayOutputStream()
        CsvEngine.writeCsv(wb, out)

        val csvString = out.toString(Charsets.UTF_8)
        assertThat(csvString).contains("\"Name, Full\"")
        assertThat(csvString).contains("\"Alice \"\"The Best\"\"\"")

        val parsedWb = CsvEngine.readCsv(ByteArrayInputStream(out.toByteArray()))
        val parsedSheet = parsedWb.sheets[0]
        assertThat(parsedSheet.getCell(CellPosition(0, 0))?.displayValue).isEqualTo("Name, Full")
        assertThat(parsedSheet.getCell(CellPosition(0, 1))?.displayValue).isEqualTo("Alice \"The Best\"")
        assertThat(parsedSheet.getCell(CellPosition(1, 1))?.numericValue).isEqualTo(98.5)
    }

    @Test
    fun testPptxRoundTrip() {
        val pres = PresentationDocument(
            slides = listOf(
                Slide(
                    shapes = listOf(
                        ShapeNode(textFrame = TextFrameNode.simple("Welcome to LibreMobile Presentation"))
                    )
                )
            )
        )

        val out = ByteArrayOutputStream()
        PptxEngine.writePptx(pres, out)
        val bytes = out.toByteArray()
        assertThat(bytes.size).isGreaterThan(500)

        val readPres = PptxEngine.readPptx(ByteArrayInputStream(bytes))
        assertThat(readPres.slides.size).isEqualTo(1)
        val shape = readPres.slides[0].shapes[0]
        assertThat(shape.textFrame?.plainText).contains("Welcome to LibreMobile Presentation")
    }

    @Test
    fun testPdfExport() {
        val doc = WriterDocument(blocks = listOf(Paragraph.simple("PDF Export test paragraph.")))
        val out = ByteArrayOutputStream()
        PdfExporter.exportWriter(doc, out)
        val bytes = out.toByteArray()

        assertThat(bytes.size).isGreaterThan(100)
        val header = String(bytes.copyOfRange(0, 8), Charsets.US_ASCII)
        assertThat(header).startsWith("%PDF-1.4")
    }

    @Test
    fun testLibreOfficeDesktopCliFidelity(@TempDir tempDir: File) {
        // QA Engineer requirement: Verify with LibreOffice desktop CLI
        val docxFile = File(tempDir, "sample.docx")
        val odtFile = File(tempDir, "sample.odt")
        val xlsxFile = File(tempDir, "sample.xlsx")
        val odsFile = File(tempDir, "sample.ods")
        val pptxFile = File(tempDir, "sample.pptx")
        val odpFile = File(tempDir, "sample.odp")

        // 1. DOCX
        val writerDoc = WriterDocument(blocks = listOf(Paragraph.simple("LibreOffice Verification DOCX")))
        docxFile.outputStream().use { DocxEngine.writeDocx(writerDoc, it) }

        // 2. ODT
        odtFile.outputStream().use { OdtEngine.writeOdt(writerDoc, it) }

        // 3. XLSX
        val cells = mapOf(CellPosition(0, 0) to CellData(CellPosition(0, 0), rawValue = "Test", evaluatedValue = "Test", type = CellType.STRING))
        val calcWb = CalcWorkbook(sheets = listOf(Sheet(cells = cells)))
        xlsxFile.outputStream().use { XlsxEngine.writeXlsx(calcWb, it) }

        // 4. ODS
        odsFile.outputStream().use { OdsEngine.writeOds(calcWb, it) }

        // 5. PPTX
        val presDoc = PresentationDocument(slides = listOf(Slide(shapes = listOf(ShapeNode(textFrame = TextFrameNode.simple("Slide Impress"))))))
        pptxFile.outputStream().use { PptxEngine.writePptx(presDoc, it) }

        // 6. ODP
        odpFile.outputStream().use { OdpEngine.writeOdp(presDoc, it) }

        // Run LibreOffice CLI to convert each document to PDF in headless mode
        val filesToTest = listOf(docxFile, odtFile, xlsxFile, odsFile, pptxFile, odpFile)
        for (file in filesToTest) {
            val process = ProcessBuilder(
                "libreoffice",
                "--headless",
                "--convert-to",
                "pdf",
                "--outdir",
                tempDir.absolutePath,
                file.absolutePath
            ).start()
            val exitCode = process.waitFor()
            assertThat(exitCode).isEqualTo(0)

            val generatedPdf = File(tempDir, file.nameWithoutExtension + ".pdf")
            assertThat(generatedPdf.exists()).isTrue()
            assertThat(generatedPdf.length()).isGreaterThan(0)
        }
    }
}
