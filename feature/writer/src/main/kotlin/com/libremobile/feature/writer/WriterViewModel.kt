package com.libremobile.feature.writer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libremobile.core.engine.pdf.PdfExporter
import com.libremobile.core.engine.writer.DocxEngine
import com.libremobile.core.engine.writer.OdtEngine
import com.libremobile.core.engine.writer.PlainTextEngine
import com.libremobile.core.ml.model.SmartDocAnalysis
import com.libremobile.core.ml.smart.SmartDocAssistant
import com.libremobile.core.model.common.DocumentType
import com.libremobile.core.model.undo.Command
import com.libremobile.core.model.undo.UndoRedoManager
import com.libremobile.core.model.writer.*
import com.libremobile.core.ui.component.EditorToolbarState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

data class WriterUiState(
    val document: WriterDocument = WriterDocument(),
    val fileName: String = "Untitled.docx",
    val documentType: DocumentType = DocumentType.WRITER_DOCX,
    val isDirty: Boolean = false,
    val toolbarState: EditorToolbarState = EditorToolbarState(),
    val smartAnalysis: SmartDocAnalysis? = null,
    val showSmartSheet: Boolean = false,
    val isProcessing: Boolean = false,
    val activeBlockIndex: Int = 0
)

class WriterViewModel(
    private val smartAssistant: SmartDocAssistant = SmartDocAssistant()
) : ViewModel() {

    private val undoManager = UndoRedoManager<WriterDocument>()

    private val _uiState = MutableStateFlow(WriterUiState())
    val uiState: StateFlow<WriterUiState> = _uiState.asStateFlow()

    fun loadDocument(inputStream: InputStream, fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isProcessing = true) }
            val ext = fileName.substringAfterLast(".", "docx").lowercase()
            val docType = DocumentType.fromExtension(ext) ?: DocumentType.WRITER_DOCX
            val doc = when (docType) {
                DocumentType.WRITER_ODT -> OdtEngine.readOdt(inputStream)
                DocumentType.WRITER_TXT -> PlainTextEngine.readText(inputStream)
                else -> DocxEngine.readDocx(inputStream)
            }
            undoManager.clear()
            _uiState.update {
                it.copy(
                    document = doc,
                    fileName = fileName,
                    documentType = docType,
                    isDirty = false,
                    isProcessing = false,
                    toolbarState = updateToolbarState(doc, it.activeBlockIndex)
                )
            }
        }
    }

    fun saveDocument(outputStream: OutputStream, docType: DocumentType = _uiState.value.documentType) {
        viewModelScope.launch(Dispatchers.IO) {
            val doc = _uiState.value.document
            when (docType) {
                DocumentType.WRITER_ODT -> OdtEngine.writeOdt(doc, outputStream)
                DocumentType.WRITER_TXT -> PlainTextEngine.writeText(doc, outputStream)
                DocumentType.WRITER_PDF -> PdfExporter.exportWriter(doc, outputStream)
                else -> DocxEngine.writeDocx(doc, outputStream)
            }
            _uiState.update { it.copy(isDirty = false) }
        }
    }

    fun updateParagraph(index: Int, newText: String) {
        val currentDoc = _uiState.value.document
        val blocks = currentDoc.blocks.toMutableList()
        if (index !in blocks.indices) return

        val oldBlock = blocks[index]
        if (oldBlock !is Paragraph) return

        val newBlock = oldBlock.copy(runs = listOf(Run(text = newText)))
        val cmd = object : Command<WriterDocument> {
            override val description = "Edit text"
            override fun execute(target: WriterDocument): WriterDocument {
                val list = target.blocks.toMutableList()
                list[index] = newBlock
                return target.copy(blocks = list)
            }
            override fun undo(target: WriterDocument): WriterDocument {
                val list = target.blocks.toMutableList()
                list[index] = oldBlock
                return target.copy(blocks = list)
            }
        }

        applyCommand(cmd)
    }

    fun toggleBold() {
        val current = _uiState.value.toolbarState.isBold
        applyStyleToActiveParagraph { runs ->
            runs.map { it.copy(style = it.style.copy(bold = !current)) }
        }
    }

    fun toggleItalic() {
        val current = _uiState.value.toolbarState.isItalic
        applyStyleToActiveParagraph { runs ->
            runs.map { it.copy(style = it.style.copy(italic = !current)) }
        }
    }

    fun toggleUnderline() {
        val current = _uiState.value.toolbarState.isUnderline
        applyStyleToActiveParagraph { runs ->
            runs.map { it.copy(style = it.style.copy(underline = !current)) }
        }
    }

    fun setAlignment(align: TextAlignment) {
        applyParagraphStyleToActive { it.copy(alignment = align) }
    }

    fun setHeading(level: HeadingLevel) {
        applyParagraphStyleToActive { it.copy(headingLevel = level) }
    }

    private fun applyStyleToActiveParagraph(updater: (List<Run>) -> List<Run>) {
        val currentDoc = _uiState.value.document
        val index = _uiState.value.activeBlockIndex
        if (index !in currentDoc.blocks.indices) return
        val oldBlock = currentDoc.blocks[index] as? Paragraph ?: return

        val newRuns = updater(oldBlock.runs)
        val newBlock = oldBlock.copy(runs = newRuns)

        val cmd = object : Command<WriterDocument> {
            override val description = "Format text"
            override fun execute(target: WriterDocument): WriterDocument {
                val list = target.blocks.toMutableList()
                list[index] = newBlock
                return target.copy(blocks = list)
            }
            override fun undo(target: WriterDocument): WriterDocument {
                val list = target.blocks.toMutableList()
                list[index] = oldBlock
                return target.copy(blocks = list)
            }
        }
        applyCommand(cmd)
    }

    private fun applyParagraphStyleToActive(updater: (ParagraphStyle) -> ParagraphStyle) {
        val currentDoc = _uiState.value.document
        val index = _uiState.value.activeBlockIndex
        if (index !in currentDoc.blocks.indices) return
        val oldBlock = currentDoc.blocks[index] as? Paragraph ?: return

        val newBlock = oldBlock.copy(style = updater(oldBlock.style))
        val cmd = object : Command<WriterDocument> {
            override val description = "Format paragraph"
            override fun execute(target: WriterDocument): WriterDocument {
                val list = target.blocks.toMutableList()
                list[index] = newBlock
                return target.copy(blocks = list)
            }
            override fun undo(target: WriterDocument): WriterDocument {
                val list = target.blocks.toMutableList()
                list[index] = oldBlock
                return target.copy(blocks = list)
            }
        }
        applyCommand(cmd)
    }

    fun undo() {
        if (!undoManager.canUndo) return
        val current = _uiState.value.document
        val reverted = undoManager.undo(current)
        _uiState.update {
            it.copy(
                document = reverted,
                isDirty = true,
                toolbarState = updateToolbarState(reverted, it.activeBlockIndex)
            )
        }
    }

    fun redo() {
        if (!undoManager.canRedo) return
        val current = _uiState.value.document
        val reapplied = undoManager.redo(current)
        _uiState.update {
            it.copy(
                document = reapplied,
                isDirty = true,
                toolbarState = updateToolbarState(reapplied, it.activeBlockIndex)
            )
        }
    }

    private fun applyCommand(cmd: Command<WriterDocument>) {
        val current = _uiState.value.document
        val next = undoManager.execute(cmd, current)
        _uiState.update {
            it.copy(
                document = next,
                isDirty = true,
                toolbarState = updateToolbarState(next, it.activeBlockIndex)
            )
        }
    }

    fun triggerSmartAnalysis() {
        viewModelScope.launch {
            _uiState.update { it.copy(showSmartSheet = true, isProcessing = true) }
            val fullText = _uiState.value.document.fullPlainText
            val analysis = smartAssistant.analyzeDocument(fullText)
            _uiState.update { it.copy(smartAnalysis = analysis, isProcessing = false) }
        }
    }

    fun hideSmartSheet() {
        _uiState.update { it.copy(showSmartSheet = false) }
    }

    private fun updateToolbarState(doc: WriterDocument, index: Int): EditorToolbarState {
        val block = doc.blocks.getOrNull(index) as? Paragraph
        val firstRun = block?.runs?.firstOrNull()
        return EditorToolbarState(
            isBold = firstRun?.style?.bold ?: false,
            isItalic = firstRun?.style?.italic ?: false,
            isUnderline = firstRun?.style?.underline ?: false,
            alignment = block?.style?.alignment ?: TextAlignment.LEFT,
            headingLevel = block?.style?.headingLevel ?: HeadingLevel.NONE,
            canUndo = undoManager.canUndo,
            canRedo = undoManager.canRedo
        )
    }
}
