package com.libremobile.app.saf

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.libremobile.core.model.common.DocumentType
import java.io.InputStream
import java.io.OutputStream

class StorageAccessManager(private val context: Context) {

    fun takePersistablePermission(uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            // Some URI providers don't support persistable permissions
        }
    }

    fun getFileName(uri: Uri): String {
        var name = "Untitled"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name
    }

    fun getFileSize(uri: Uri): Long {
        var size = 0L
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.SIZE)
                if (index != -1) {
                    size = it.getLong(index)
                }
            }
        }
        return size
    }

    fun openInputStream(uri: Uri): InputStream? {
        return context.contentResolver.openInputStream(uri)
    }

    fun openOutputStream(uri: Uri): OutputStream? {
        return context.contentResolver.openOutputStream(uri, "wt")
    }

    fun detectDocumentType(fileName: String): DocumentType {
        val ext = fileName.substringAfterLast(".", "docx").lowercase()
        return DocumentType.fromExtension(ext) ?: DocumentType.WRITER_DOCX
    }
}
