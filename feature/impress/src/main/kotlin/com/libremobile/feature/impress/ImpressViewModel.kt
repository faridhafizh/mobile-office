package com.libremobile.feature.impress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libremobile.core.engine.impress.OdpEngine
import com.libremobile.core.engine.impress.PptxEngine
import com.libremobile.core.engine.pdf.PdfExporter
import com.libremobile.core.model.common.ColorRgba
import com.libremobile.core.model.common.DocumentType
import com.libremobile.core.model.common.Rect2D
import com.libremobile.core.model.impress.*
import com.libremobile.core.model.undo.Command
import com.libremobile.core.model.undo.UndoRedoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream

data class ImpressUiState(
    val document: PresentationDocument = PresentationDocument(
        slides = listOf(
            Slide(
                shapes = listOf(
                    ShapeNode(
                        bounds = Rect2D(80f, 60f, 880f, 160f),
                        textFrame = TextFrameNode.simple("Presentation Title")
                    ),
                    ShapeNode(
                        bounds = Rect2D(80f, 180f, 880f, 440f),
                        textFrame = TextFrameNode.simple("• Add your presentation bullet points here\n• Offline ML intelligence enabled\n• Compatible with LibreOffice Impress")
                    )
                )
            )
        )
    ),
    val fileName: String = "Untitled.pptx",
    val documentType: DocumentType = DocumentType.IMPRESS_PPTX,
    val activeSlideIndex: Int = 0,
    val isDirty: Boolean = false,
    val isProcessing: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

class ImpressViewModel : ViewModel() {

    private val undoManager = UndoRedoManager<PresentationDocument>()

    private val _uiState = MutableStateFlow(ImpressUiState())
    val uiState: StateFlow<ImpressUiState> = _uiState.asStateFlow()

    fun loadPresentation(inputStream: InputStream, fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isProcessing = true) }
            val ext = fileName.substringAfterLast(".", "pptx").lowercase()
            val docType = DocumentType.fromExtension(ext) ?: DocumentType.IMPRESS_PPTX
            val pres = when (docType) {
                DocumentType.IMPRESS_ODP -> OdpEngine.readOdp(inputStream)
                else -> PptxEngine.readPptx(inputStream)
            }
            undoManager.clear()
            _uiState.update {
                it.copy(
                    document = pres,
                    fileName = fileName,
                    documentType = docType,
                    activeSlideIndex = 0,
                    isDirty = false,
                    isProcessing = false,
                    canUndo = false,
                    canRedo = false
                )
            }
        }
    }

    fun savePresentation(outputStream: OutputStream, docType: DocumentType = _uiState.value.documentType) {
        viewModelScope.launch(Dispatchers.IO) {
            val pres = _uiState.value.document
            when (docType) {
                DocumentType.IMPRESS_ODP -> OdpEngine.writeOdp(pres, outputStream)
                DocumentType.WRITER_PDF -> PdfExporter.exportImpress(pres, outputStream)
                else -> PptxEngine.writePptx(pres, outputStream)
            }
            _uiState.update { it.copy(isDirty = false) }
        }
    }

    fun selectSlide(index: Int) {
        if (index in _uiState.value.document.slides.indices) {
            _uiState.update { it.copy(activeSlideIndex = index) }
        }
    }

    fun addSlide() {
        val currentDoc = _uiState.value.document
        val newSlide = Slide(
            slideNumber = currentDoc.slides.size + 1,
            shapes = listOf(
                ShapeNode(
                    bounds = Rect2D(80f, 60f, 880f, 160f),
                    textFrame = TextFrameNode.simple("New Slide Title")
                )
            )
        )

        val cmd = object : Command<PresentationDocument> {
            override val description = "Add slide"
            override fun execute(target: PresentationDocument): PresentationDocument {
                return target.copy(slides = target.slides + newSlide)
            }
            override fun undo(target: PresentationDocument): PresentationDocument {
                return target.copy(slides = target.slides.dropLast(1))
            }
        }

        val next = undoManager.execute(cmd, currentDoc)
        _uiState.update {
            it.copy(
                document = next,
                activeSlideIndex = next.slides.lastIndex,
                isDirty = true,
                canUndo = undoManager.canUndo,
                canRedo = undoManager.canRedo
            )
        }
    }

    fun deleteCurrentSlide() {
        val currentDoc = _uiState.value.document
        if (currentDoc.slides.size <= 1) return

        val indexToDelete = _uiState.value.activeSlideIndex
        val slideToDelete = currentDoc.slides[indexToDelete]

        val cmd = object : Command<PresentationDocument> {
            override val description = "Delete slide"
            override fun execute(target: PresentationDocument): PresentationDocument {
                val list = target.slides.toMutableList()
                list.removeAt(indexToDelete)
                return target.copy(slides = list)
            }
            override fun undo(target: PresentationDocument): PresentationDocument {
                val list = target.slides.toMutableList()
                list.add(indexToDelete, slideToDelete)
                return target.copy(slides = list)
            }
        }

        val next = undoManager.execute(cmd, currentDoc)
        _uiState.update {
            it.copy(
                document = next,
                activeSlideIndex = indexToDelete.coerceAtMost(next.slides.lastIndex),
                isDirty = true,
                canUndo = undoManager.canUndo,
                canRedo = undoManager.canRedo
            )
        }
    }

    fun undo() {
        if (!undoManager.canUndo) return
        val current = _uiState.value.document
        val reverted = undoManager.undo(current)
        _uiState.update {
            it.copy(
                document = reverted,
                activeSlideIndex = it.activeSlideIndex.coerceAtMost(reverted.slides.lastIndex),
                isDirty = true,
                canUndo = undoManager.canUndo,
                canRedo = undoManager.canRedo
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
                activeSlideIndex = it.activeSlideIndex.coerceAtMost(reapplied.slides.lastIndex),
                isDirty = true,
                canUndo = undoManager.canUndo,
                canRedo = undoManager.canRedo
            )
        }
    }
}
