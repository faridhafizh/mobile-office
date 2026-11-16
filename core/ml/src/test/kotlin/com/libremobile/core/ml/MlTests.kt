package com.libremobile.core.ml

import com.google.common.truth.Truth.assertThat
import com.libremobile.core.ml.entity.EntityExtractionEngine
import com.libremobile.core.ml.model.*
import com.libremobile.core.ml.ocr.TextRecognitionEngine
import com.libremobile.core.ml.smart.SmartDocAssistant
import com.libremobile.core.model.calc.CellPosition
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MlTests {

    @Test
    fun testEntityHeuristicExtraction() {
        val engine = EntityExtractionEngine()
        val sampleText = "Please contact support at info@libremobile.org or call 555-123-4567. Website: https://libremobile.org."

        val entities = engine.heuristicFallbackExtraction(sampleText)
        assertThat(entities.size).isAtLeast(3)

        val email = entities.find { it.type == EntityType.EMAIL }
        assertThat(email).isNotNull()
        assertThat(email!!.text).isEqualTo("info@libremobile.org")
        assertThat(email.actionUri).isEqualTo("mailto:info@libremobile.org")

        val phone = entities.find { it.type == EntityType.PHONE_NUMBER }
        assertThat(phone).isNotNull()
        assertThat(phone!!.text).isEqualTo("555-123-4567")
        assertThat(phone.actionUri).isEqualTo("tel:555-123-4567")

        val url = entities.find { it.type == EntityType.URL }
        assertThat(url).isNotNull()
        assertThat(url!!.text).startsWith("https://libremobile.org")
    }

    @Test
    fun testSmartDocAssistantAnalysis() = runBlocking {
        val assistant = SmartDocAssistant()
        val text = """
            LibreMobile is an offline-first office productivity suite designed for modern mobile platforms.
            It integrates powerful document editing capabilities with on-device machine intelligence.
            Users can edit documents, spreadsheets, and presentations without internet connectivity.
            For assistance, contact help@libremobile.org.
        """.trimIndent()

        val analysis = assistant.analyzeDocument(text)
        assertThat(analysis.wordCount).isGreaterThan(20)
        assertThat(analysis.readingTimeSeconds).isAtLeast(1)
        assertThat(analysis.keySummaryPoints).isNotEmpty()
        assertThat(analysis.actions.any { it.type == SmartActionType.EMAIL }).isTrue()
    }

    @Test
    fun testOcrTableExtractionToCalcWorkbook() {
        val engine = TextRecognitionEngine()

        // Simulate OCR blocks representing a scanned table/receipt
        // Row 1: Header
        val headerLine1 = OcrLine(text = "Item", box = OcrBoundingBox(10f, 10f, 60f, 30f))
        val headerLine2 = OcrLine(text = "Price", box = OcrBoundingBox(120f, 10f, 180f, 30f))
        val block1 = OcrTextBlock(text = "Item Price", box = OcrBoundingBox(10f, 10f, 180f, 30f), lines = listOf(headerLine1, headerLine2))

        // Row 2: Data 1
        val row1Line1 = OcrLine(text = "Coffee", box = OcrBoundingBox(10f, 40f, 60f, 60f))
        val row1Line2 = OcrLine(text = "4.50", box = OcrBoundingBox(120f, 40f, 180f, 60f))
        val block2 = OcrTextBlock(text = "Coffee 4.50", box = OcrBoundingBox(10f, 40f, 180f, 60f), lines = listOf(row1Line1, row1Line2))

        // Row 3: Data 2
        val row2Line1 = OcrLine(text = "Croissant", box = OcrBoundingBox(10f, 70f, 80f, 90f))
        val row2Line2 = OcrLine(text = "3.25", box = OcrBoundingBox(120f, 70f, 180f, 90f))
        val block3 = OcrTextBlock(text = "Croissant 3.25", box = OcrBoundingBox(10f, 70f, 180f, 90f), lines = listOf(row2Line1, row2Line2))

        val ocrResult = OcrDocumentResult(
            fullText = "Item Price\nCoffee 4.50\nCroissant 3.25",
            blocks = listOf(block1, block2, block3)
        )

        val workbook = engine.extractTableToWorkbook(ocrResult)
        val sheet = workbook.sheets[0]

        // Verify cell positions in Calc DOM
        assertThat(sheet.getCell(CellPosition(0, 0))?.displayValue).isEqualTo("Item")
        assertThat(sheet.getCell(CellPosition(1, 0))?.displayValue).isEqualTo("Price")
        assertThat(sheet.getCell(CellPosition(0, 1))?.displayValue).isEqualTo("Coffee")
        assertThat(sheet.getCell(CellPosition(1, 1))?.numericValue).isEqualTo(4.50)
        assertThat(sheet.getCell(CellPosition(0, 2))?.displayValue).isEqualTo("Croissant")
        assertThat(sheet.getCell(CellPosition(1, 2))?.numericValue).isEqualTo(3.25)
    }
}
