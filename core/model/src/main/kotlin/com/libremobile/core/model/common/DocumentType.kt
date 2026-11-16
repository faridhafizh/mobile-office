package com.libremobile.core.model.common

enum class DocumentType(val extension: String, val displayName: String, val mimeType: String) {
    WRITER_DOCX("docx", "Word Document (DOCX)", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    WRITER_ODT("odt", "OpenDocument Text (ODT)", "application/vnd.oasis.opendocument.text"),
    WRITER_TXT("txt", "Plain Text", "text/plain"),
    WRITER_PDF("pdf", "PDF Document", "application/pdf"),
    
    CALC_XLSX("xlsx", "Excel Spreadsheet (XLSX)", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    CALC_ODS("ods", "OpenDocument Spreadsheet (ODS)", "application/vnd.oasis.opendocument.spreadsheet"),
    CALC_CSV("csv", "Comma Separated Values", "text/csv"),
    
    IMPRESS_PPTX("pptx", "PowerPoint Presentation (PPTX)", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
    IMPRESS_ODP("odp", "OpenDocument Presentation (ODP)", "application/vnd.oasis.opendocument.presentation");

    companion object {
        fun fromExtension(ext: String): DocumentType? {
            val normalized = ext.removePrefix(".").lowercase()
            return entries.find { it.extension == normalized }
        }
    }
}
