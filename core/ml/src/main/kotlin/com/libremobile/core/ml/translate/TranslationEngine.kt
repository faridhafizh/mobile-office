package com.libremobile.core.ml.translate

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.libremobile.core.ml.model.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TranslationEngine {

    private val languageIdentifier: LanguageIdentifier by lazy {
        LanguageIdentification.getClient()
    }

    suspend fun identifyLanguage(text: String): String = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext "und"
        try {
            val task = languageIdentifier.identifyLanguage(text)
            Tasks.await(task)
        } catch (e: Exception) {
            "und"
        }
    }

    suspend fun translateText(
        text: String,
        targetLanguage: String = TranslateLanguage.ENGLISH,
        sourceLanguage: String? = null
    ): TranslationResult = withContext(Dispatchers.Default) {
        if (text.isBlank()) {
            return@withContext TranslationResult(text, "", "und", targetLanguage)
        }

        val detectedSource = sourceLanguage ?: run {
            val lang = identifyLanguage(text)
            if (lang == "und") TranslateLanguage.ENGLISH else lang
        }

        if (detectedSource == targetLanguage) {
            return@withContext TranslationResult(text, text, detectedSource, targetLanguage)
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(detectedSource)
            .setTargetLanguage(targetLanguage)
            .build()

        val translator: Translator = Translation.getClient(options)
        try {
            val conditions = DownloadConditions.Builder().build()
            Tasks.await(translator.downloadModelIfNeeded(conditions))

            val translateTask = translator.translate(text)
            val translated = Tasks.await(translateTask)

            TranslationResult(
                originalText = text,
                translatedText = translated,
                sourceLanguage = detectedSource,
                targetLanguage = targetLanguage
            )
        } catch (e: Exception) {
            TranslationResult(
                originalText = text,
                translatedText = "[Offline translation unavailable: ${e.message}]",
                sourceLanguage = detectedSource,
                targetLanguage = targetLanguage
            )
        } finally {
            translator.close()
        }
    }
}
