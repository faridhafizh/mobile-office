package com.libremobile.core.ml.smart

import com.libremobile.core.ml.entity.EntityExtractionEngine
import com.libremobile.core.ml.model.*
import com.libremobile.core.ml.translate.TranslationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmartDocAssistant(
    private val entityEngine: EntityExtractionEngine = EntityExtractionEngine(),
    private val translationEngine: TranslationEngine = TranslationEngine()
) {

    suspend fun analyzeDocument(text: String): SmartDocAnalysis = withContext(Dispatchers.Default) {
        if (text.isBlank()) {
            return@withContext SmartDocAnalysis(
                languageCode = "en",
                entities = emptyList(),
                actions = emptyList(),
                wordCount = 0,
                characterCount = 0,
                readingTimeSeconds = 0,
                keySummaryPoints = emptyList()
            )
        }

        val lang = translationEngine.identifyLanguage(text)
        val entities = entityEngine.extractEntities(text)

        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val wordCount = words.size
        val charCount = text.length
        val readingTime = (wordCount / 3.5).toInt().coerceAtLeast(1) // avg reading speed 210 wpm = 3.5 words/sec

        val keyPoints = extractKeySentences(text, maxSentences = 3)

        val actions = mutableListOf<SmartAction>()
        for (entity in entities) {
            when (entity.type) {
                EntityType.PHONE_NUMBER -> actions.add(SmartAction(entity.actionLabel, SmartActionType.DIAL, entity.actionUri))
                EntityType.EMAIL -> actions.add(SmartAction(entity.actionLabel, SmartActionType.EMAIL, entity.actionUri))
                EntityType.ADDRESS -> actions.add(SmartAction(entity.actionLabel, SmartActionType.MAP, entity.actionUri))
                EntityType.URL -> actions.add(SmartAction(entity.actionLabel, SmartActionType.BROWSE, entity.actionUri))
                EntityType.DATE_TIME -> actions.add(SmartAction(entity.actionLabel, SmartActionType.CALENDAR, entity.actionUri))
                else -> {}
            }
        }

        if (lang != "und" && lang != "en") {
            actions.add(
                SmartAction(
                    label = "Translate document from ${lang.uppercase()}",
                    type = SmartActionType.TRANSLATE,
                    payload = lang
                )
            )
        }

        SmartDocAnalysis(
            languageCode = lang,
            entities = entities,
            actions = actions.distinctBy { it.payload },
            wordCount = wordCount,
            characterCount = charCount,
            readingTimeSeconds = readingTime,
            keySummaryPoints = keyPoints
        )
    }

    /**
     * Fast, 100% offline extractive summarization heuristic based on sentence scoring
     */
    fun extractKeySentences(text: String, maxSentences: Int = 3): List<String> {
        val sentences = text.split(Regex("""(?<=[.!?])\s+""")).map { it.trim() }.filter { it.length > 20 }
        if (sentences.size <= maxSentences) return sentences

        val wordFreq = mutableMapOf<String, Int>()
        val words = text.lowercase().split(Regex("""\W+""")).filter { it.length > 3 }
        for (w in words) {
            wordFreq[w] = (wordFreq[w] ?: 0) + 1
        }

        val scoredSentences = sentences.map { sentence ->
            val sWords = sentence.lowercase().split(Regex("""\W+""")).filter { it.length > 3 }
            val score = sWords.sumOf { wordFreq[it] ?: 0 }
            sentence to score
        }

        return scoredSentences
            .sortedByDescending { it.second }
            .take(maxSentences)
            .map { it.first }
    }
}
