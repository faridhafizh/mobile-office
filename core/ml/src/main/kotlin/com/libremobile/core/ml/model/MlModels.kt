package com.libremobile.core.ml.model

import com.libremobile.core.model.common.Rect2D

enum class OcrScript {
    LATIN,
    CHINESE,
    DEVANAGARI
}

data class OcrBoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

data class OcrElement(
    val text: String,
    val box: OcrBoundingBox,
    val confidence: Float = 1.0f
)

data class OcrLine(
    val text: String,
    val box: OcrBoundingBox,
    val elements: List<OcrElement> = emptyList()
)

data class OcrTextBlock(
    val text: String,
    val box: OcrBoundingBox,
    val lines: List<OcrLine> = emptyList()
)

data class OcrDocumentResult(
    val fullText: String,
    val blocks: List<OcrTextBlock>
)

enum class EntityType {
    PHONE_NUMBER,
    EMAIL,
    ADDRESS,
    DATE_TIME,
    URL,
    TRACKING_NUMBER,
    MONEY
}

data class ExtractedEntity(
    val text: String,
    val type: EntityType,
    val startIndex: Int,
    val endIndex: Int,
    val actionUri: String,
    val actionLabel: String
)

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String
)

enum class SmartActionType {
    DIAL,
    EMAIL,
    MAP,
    CALENDAR,
    BROWSE,
    INSERT_TABLE,
    TRANSLATE,
    SUMMARIZE
}

data class SmartAction(
    val label: String,
    val type: SmartActionType,
    val payload: String
)

data class SmartDocAnalysis(
    val languageCode: String,
    val entities: List<ExtractedEntity>,
    val actions: List<SmartAction>,
    val wordCount: Int,
    val characterCount: Int,
    val readingTimeSeconds: Int,
    val keySummaryPoints: List<String>
)
