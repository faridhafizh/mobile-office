package com.libremobile.core.ml.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.libremobile.core.ml.model.*
import com.libremobile.core.model.calc.*
import com.libremobile.core.model.common.DocumentMetadata
import com.libremobile.core.model.writer.Paragraph
import com.libremobile.core.model.writer.Run
import com.libremobile.core.model.writer.WriterDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class TextRecognitionEngine {

    private val latinRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val chineseRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    private val devanagariRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }

    private fun getRecognizer(script: OcrScript): TextRecognizer = when (script) {
        OcrScript.LATIN -> latinRecognizer
        OcrScript.CHINESE -> chineseRecognizer
        OcrScript.DEVANAGARI -> devanagariRecognizer
    }

    suspend fun recognizeText(bitmap: Bitmap, script: OcrScript = OcrScript.LATIN): OcrDocumentResult = withContext(Dispatchers.Default) {
        val recognizer = getRecognizer(script)
        val image = InputImage.fromBitmap(bitmap, 0)
        val task = recognizer.process(image)
        val visionText = Tasks.await(task)

        convertVisionText(visionText)
    }

    fun convertVisionText(visionText: Text): OcrDocumentResult {
        val blocks = mutableListOf<OcrTextBlock>()
        for (block in visionText.textBlocks) {
            val lines = mutableListOf<OcrLine>()
            for (line in block.lines) {
                val elements = line.elements.map { el ->
                    OcrElement(
                        text = el.text,
                        box = toBox(el.boundingBox)
                    )
                }
                lines.add(
                    OcrLine(
                        text = line.text,
                        box = toBox(line.boundingBox),
                        elements = elements
                    )
                )
            }
            blocks.add(
                OcrTextBlock(
                    text = block.text,
                    box = toBox(block.boundingBox),
                    lines = lines
                )
            )
        }

        return OcrDocumentResult(
            fullText = visionText.text,
            blocks = blocks
        )
    }

    /**
     * Reconstructs OCR text into a WriterDocument with preserved paragraphs in natural reading order
     */
    fun reconstructWriterDocument(ocrResult: OcrDocumentResult): WriterDocument {
        // Sort blocks top-to-bottom, left-to-right
        val sortedBlocks = ocrResult.blocks.sortedWith(
            compareBy<OcrTextBlock> { it.box.top }
                .thenBy { it.box.left }
        )

        val paragraphs = mutableListOf<Paragraph>()
        for (block in sortedBlocks) {
            for (line in block.lines) {
                paragraphs.add(Paragraph(runs = listOf(Run(text = line.text))))
            }
        }

        return WriterDocument(
            metadata = DocumentMetadata(title = "OCR Scanned Document"),
            blocks = if (paragraphs.isEmpty()) listOf(Paragraph.simple("")) else paragraphs
        )
    }

    /**
     * Automatically extracts tabular data from OCR bounding boxes into structured Calc cells!
     * Groups text lines by vertical Y proximity (rows) and horizontal X position (columns).
     */
    fun extractTableToWorkbook(ocrResult: OcrDocumentResult, rowThresholdPx: Float = 16f): CalcWorkbook {
        // Collect all lines across all blocks
        val allLines = ocrResult.blocks.flatMap { it.lines }
        if (allLines.isEmpty()) {
            return CalcWorkbook(sheets = listOf(Sheet(name = "OCR Table")))
        }

        // 1. Cluster lines into rows based on vertical centroid proximity
        val sortedByY = allLines.sortedBy { it.box.centerY }
        val rows = mutableListOf<MutableList<OcrLine>>()

        for (line in sortedByY) {
            val matchingRow = rows.find { rowLines ->
                val avgY = rowLines.map { it.box.centerY }.average().toFloat()
                abs(line.box.centerY - avgY) <= rowThresholdPx
            }
            if (matchingRow != null) {
                matchingRow.add(line)
            } else {
                rows.add(mutableListOf(line))
            }
        }

        // 2. Map row items into columns based on X position
        val cells = mutableMapOf<CellPosition, CellData>()
        for ((rIndex, rowLines) in rows.withIndex()) {
            val sortedByX = rowLines.sortedBy { it.box.left }
            for ((cIndex, line) in sortedByX.withIndex()) {
                val text = line.text.trim()
                val num = text.toDoubleOrNull()
                val type = if (num != null) CellType.NUMBER else CellType.STRING
                val pos = CellPosition(cIndex, rIndex)
                cells[pos] = CellData(
                    position = pos,
                    rawValue = text,
                    evaluatedValue = text,
                    numericValue = num,
                    type = type
                )
            }
        }

        val sheet = Sheet(name = "OCR Scanned Table", cells = cells)
        return CalcWorkbook(
            metadata = DocumentMetadata(title = "OCR Tabular Export"),
            sheets = listOf(sheet)
        )
    }

    private fun toBox(rect: Rect?): OcrBoundingBox {
        return if (rect != null) {
            OcrBoundingBox(
                left = rect.left.toFloat(),
                top = rect.top.toFloat(),
                right = rect.right.toFloat(),
                bottom = rect.bottom.toFloat()
            )
        } else {
            OcrBoundingBox(0f, 0f, 0f, 0f)
        }
    }
}
