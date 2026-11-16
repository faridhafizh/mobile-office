package com.libremobile.core.ml.entity

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityAnnotation
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractor
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.libremobile.core.ml.model.EntityType
import com.libremobile.core.ml.model.ExtractedEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class EntityExtractionEngine(
    modelIdentifier: String = EntityExtractorOptions.ENGLISH
) {
    private val extractor: EntityExtractor by lazy {
        EntityExtraction.getClient(
            EntityExtractorOptions.Builder(modelIdentifier).build()
        )
    }

    suspend fun prepareModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            val task = extractor.downloadModelIfNeeded()
            Tasks.await(task)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun extractEntities(text: String): List<ExtractedEntity> = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext emptyList()
        try {
            val isReady = extractor.isModelDownloaded
            val readyTask = Tasks.await(isReady)
            if (!readyTask) {
                Tasks.await(extractor.downloadModelIfNeeded())
            }

            val params = EntityExtractionParams.Builder(text).build()
            val task = extractor.annotate(params)
            val annotations = Tasks.await(task)

            val results = mutableListOf<ExtractedEntity>()
            for (annotation in annotations) {
                val entity = annotation.entities.firstOrNull() ?: continue
                val entityType = mapEntityType(entity.type) ?: continue
                val rawText = annotation.annotatedText

                val actionUri = buildActionUri(entityType, rawText, entity)
                val label = buildActionLabel(entityType, rawText)

                results.add(
                    ExtractedEntity(
                        text = rawText,
                        type = entityType,
                        startIndex = annotation.start,
                        endIndex = annotation.end,
                        actionUri = actionUri,
                        actionLabel = label
                    )
                )
            }
            results
        } catch (e: Exception) {
            // Heuristic fallback regex extraction when offline model is unavailable
            heuristicFallbackExtraction(text)
        }
    }

    private fun mapEntityType(type: Int): EntityType? {
        return when (type) {
            Entity.TYPE_PHONE -> EntityType.PHONE_NUMBER
            Entity.TYPE_EMAIL -> EntityType.EMAIL
            Entity.TYPE_ADDRESS -> EntityType.ADDRESS
            Entity.TYPE_DATE_TIME -> EntityType.DATE_TIME
            Entity.TYPE_URL -> EntityType.URL
            Entity.TYPE_TRACKING_NUMBER -> EntityType.TRACKING_NUMBER
            Entity.TYPE_MONEY -> EntityType.MONEY
            else -> null
        }
    }

    private fun buildActionUri(type: EntityType, text: String, entity: Entity): String {
        return when (type) {
            EntityType.PHONE_NUMBER -> "tel:${text.replace(" ", "")}"
            EntityType.EMAIL -> "mailto:$text"
            EntityType.ADDRESS -> "geo:0,0?q=${URLEncoder.encode(text, "UTF-8")}"
            EntityType.URL -> if (text.startsWith("http://") || text.startsWith("https://")) text else "https://$text"
            EntityType.DATE_TIME -> "content://com.android.calendar/time/${System.currentTimeMillis()}"
            EntityType.TRACKING_NUMBER -> "https://www.google.com/search?q=${URLEncoder.encode(text, "UTF-8")}"
            EntityType.MONEY -> ""
        }
    }

    private fun buildActionLabel(type: EntityType, text: String): String {
        return when (type) {
            EntityType.PHONE_NUMBER -> "Call $text"
            EntityType.EMAIL -> "Send Email"
            EntityType.ADDRESS -> "View on Maps"
            EntityType.URL -> "Open Link"
            EntityType.DATE_TIME -> "Add to Calendar"
            EntityType.TRACKING_NUMBER -> "Track Package"
            EntityType.MONEY -> "Currency"
        }
    }

    /**
     * Regex fallback for instant zero-dependency detection
     */
    fun heuristicFallbackExtraction(text: String): List<ExtractedEntity> {
        val results = mutableListOf<ExtractedEntity>()

        // Phone numbers
        val phoneRegex = Regex("""(\+?\d{1,3}[-.\s]?)?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}""")
        phoneRegex.findAll(text).forEach { match ->
            results.add(
                ExtractedEntity(
                    text = match.value,
                    type = EntityType.PHONE_NUMBER,
                    startIndex = match.range.first,
                    endIndex = match.range.last + 1,
                    actionUri = "tel:${match.value.replace(" ", "")}",
                    actionLabel = "Call ${match.value}"
                )
            )
        }

        // Emails
        val emailRegex = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,6}""")
        emailRegex.findAll(text).forEach { match ->
            results.add(
                ExtractedEntity(
                    text = match.value,
                    type = EntityType.EMAIL,
                    startIndex = match.range.first,
                    endIndex = match.range.last + 1,
                    actionUri = "mailto:${match.value}",
                    actionLabel = "Send Email"
                )
            )
        }

        // URLs
        val urlRegex = Regex("""https?://[^\s]+""")
        urlRegex.findAll(text).forEach { match ->
            results.add(
                ExtractedEntity(
                    text = match.value,
                    type = EntityType.URL,
                    startIndex = match.range.first,
                    endIndex = match.range.last + 1,
                    actionUri = match.value,
                    actionLabel = "Open Link"
                )
            )
        }

        return results
    }
}
