package com.libremobile.core.engine.writer

import com.libremobile.core.model.common.DocumentMetadata
import com.libremobile.core.model.writer.Paragraph
import com.libremobile.core.model.writer.WriterDocument
import java.io.InputStream
import java.io.OutputStream

object PlainTextEngine {

    fun readText(inputStream: InputStream): WriterDocument {
        val content = inputStream.bufferedReader(Charsets.UTF_8).readText()
        val paragraphs = content.lines().map { Paragraph.simple(it) }
        return WriterDocument(
            metadata = DocumentMetadata(title = "Plain Text"),
            blocks = if (paragraphs.isEmpty()) listOf(Paragraph.simple("")) else paragraphs
        )
    }

    fun writeText(document: WriterDocument, outputStream: OutputStream) {
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(document.fullPlainText)
        }
    }
}
