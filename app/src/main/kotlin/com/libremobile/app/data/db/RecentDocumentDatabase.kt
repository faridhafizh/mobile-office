package com.libremobile.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.libremobile.core.model.common.DocumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class RecentDocument(
    val uriString: String,
    val displayName: String,
    val documentType: DocumentType,
    val fileSizeBytes: Long = 0L,
    val lastAccessedTimestamp: Long = System.currentTimeMillis()
)

class RecentDocumentDatabase(context: Context) : SQLiteOpenHelper(context, "libremobile_recent.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE recent_documents (
                uri_string TEXT PRIMARY KEY,
                display_name TEXT NOT NULL,
                document_type TEXT NOT NULL,
                file_size_bytes INTEGER NOT NULL,
                last_accessed_timestamp INTEGER NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS recent_documents")
        onCreate(db)
    }
}

class RecentDocumentRepository(private val dbHelper: RecentDocumentDatabase) {

    private val _recentDocuments = MutableStateFlow<List<RecentDocument>>(emptyList())
    val recentDocuments: StateFlow<List<RecentDocument>> = _recentDocuments.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val list = mutableListOf<RecentDocument>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "recent_documents",
            null,
            null,
            null,
            null,
            null,
            "last_accessed_timestamp DESC",
            "50"
        )
        cursor.use {
            val uriCol = it.getColumnIndexOrThrow("uri_string")
            val nameCol = it.getColumnIndexOrThrow("display_name")
            val typeCol = it.getColumnIndexOrThrow("document_type")
            val sizeCol = it.getColumnIndexOrThrow("file_size_bytes")
            val timeCol = it.getColumnIndexOrThrow("last_accessed_timestamp")

            while (it.moveToNext()) {
                val typeStr = it.getString(typeCol)
                val docType = DocumentType.entries.find { d -> d.name == typeStr } ?: DocumentType.WRITER_DOCX
                list.add(
                    RecentDocument(
                        uriString = it.getString(uriCol),
                        displayName = it.getString(nameCol),
                        documentType = docType,
                        fileSizeBytes = it.getLong(sizeCol),
                        lastAccessedTimestamp = it.getLong(timeCol)
                    )
                )
            }
        }
        _recentDocuments.value = list
    }

    suspend fun recordAccess(uriString: String, displayName: String, documentType: DocumentType, sizeBytes: Long = 0L) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("uri_string", uriString)
            put("display_name", displayName)
            put("document_type", documentType.name)
            put("file_size_bytes", sizeBytes)
            put("last_accessed_timestamp", System.currentTimeMillis())
        }
        db.insertWithOnConflict("recent_documents", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        refresh()
    }

    suspend fun remove(uriString: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete("recent_documents", "uri_string = ?", arrayOf(uriString))
        refresh()
    }
}
