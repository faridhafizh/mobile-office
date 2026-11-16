package com.libremobile.feature.calc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libremobile.core.engine.calc.CsvEngine
import com.libremobile.core.engine.calc.OdsEngine
import com.libremobile.core.engine.calc.XlsxEngine
import com.libremobile.core.engine.formula.FormulaEngine
import com.libremobile.core.engine.pdf.PdfExporter
import com.libremobile.core.ml.model.OcrDocumentResult
import com.libremobile.core.ml.ocr.TextRecognitionEngine
import com.libremobile.core.model.calc.*
import com.libremobile.core.model.common.DocumentType
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

data class CalcUiState(
    val workbook: CalcWorkbook = CalcWorkbook(),
    val fileName: String = "Untitled.xlsx",
    val documentType: DocumentType = DocumentType.CALC_XLSX,
    val selectedCell: CellPosition = CellPosition(0, 0),
    val formulaBarText: String = "",
    val isDirty: Boolean = false,
    val isProcessing: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val showMlDialog: Boolean = false
)

class CalcViewModel(
    private val ocrEngine: TextRecognitionEngine = TextRecognitionEngine()
) : ViewModel() {

    private val undoManager = UndoRedoManager<CalcWorkbook>()

    private val _uiState = MutableStateFlow(CalcUiState())
    val uiState: StateFlow<CalcUiState> = _uiState.asStateFlow()

    fun loadWorkbook(inputStream: InputStream, fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isProcessing = true) }
            val ext = fileName.substringAfterLast(".").lowercase()
            val docType = DocumentType.fromExtension(ext) ?: DocumentType.CALC_XLSX
            val wb = when (docType) {
                DocumentType.CALC_ODS -> OdsEngine.readOds(inputStream)
                DocumentType.CALC_CSV -> CsvEngine.readCsv(inputStream)
                else -> XlsxEngine.readXlsx(inputStream)
            }
            val evaluatedWb = FormulaEngine(wb).evaluateWorkbook()
            undoManager.clear()
            _uiState.update {
                it.copy(
                    workbook = evaluatedWb,
                    fileName = fileName,
                    documentType = docType,
                    isDirty = false,
                    isProcessing = false,
                    selectedCell = CellPosition(0, 0),
                    formulaBarText = evaluatedWb.activeSheet.getCellOrEmpty(0, 0).rawValue,
                    canUndo = false,
                    canRedo = false
                )
            }
        }
    }

    fun saveWorkbook(outputStream: OutputStream, docType: DocumentType = _uiState.value.documentType) {
        viewModelScope.launch(Dispatchers.IO) {
            val wb = _uiState.value.workbook
            when (docType) {
                DocumentType.CALC_ODS -> OdsEngine.writeOds(wb, outputStream)
                DocumentType.CALC_CSV -> CsvEngine.writeCsv(wb, outputStream)
                DocumentType.WRITER_PDF -> PdfExporter.exportCalc(wb, outputStream)
                else -> XlsxEngine.writeXlsx(wb, outputStream)
            }
            _uiState.update { it.copy(isDirty = false) }
        }
    }

    fun selectCell(pos: CellPosition) {
        val cell = _uiState.value.workbook.activeSheet.getCellOrEmpty(pos.col, pos.row)
        _uiState.update {
            it.copy(
                selectedCell = pos,
                formulaBarText = cell.rawValue
            )
        }
    }

    fun updateFormulaText(text: String) {
        _uiState.update { it.copy(formulaBarText = text) }
    }

    fun applyCellEdit() {
        val pos = _uiState.value.selectedCell
        val newRaw = _uiState.value.formulaBarText
        val currentWb = _uiState.value.workbook
        val currentSheet = currentWb.activeSheet
        val oldCell = currentSheet.getCellOrEmpty(pos.col, pos.row)

        if (oldCell.rawValue == newRaw) return

        val newCell = oldCell.copy(
            rawValue = newRaw,
            type = when {
                newRaw.startsWith("=") -> CellType.FORMULA
                newRaw.toDoubleOrNull() != null -> CellType.NUMBER
                newRaw.isEmpty() -> CellType.EMPTY
                else -> CellType.STRING
            }
        )

        val cmd = object : Command<CalcWorkbook> {
            override val description = "Edit cell ${pos.a1String}"
            override fun execute(target: CalcWorkbook): CalcWorkbook {
                val updatedSheet = target.activeSheet.setCell(newCell)
                val newWb = target.updateActiveSheet { updatedSheet }
                return FormulaEngine(newWb).evaluateWorkbook()
            }
            override fun undo(target: CalcWorkbook): CalcWorkbook {
                val updatedSheet = target.activeSheet.setCell(oldCell)
                val newWb = target.updateActiveSheet { updatedSheet }
                return FormulaEngine(newWb).evaluateWorkbook()
            }
        }

        val next = undoManager.execute(cmd, currentWb)
        _uiState.update {
            it.copy(
                workbook = next,
                isDirty = true,
                canUndo = undoManager.canUndo,
                canRedo = undoManager.canRedo
            )
        }
    }

    fun insertFunction(funcName: String) {
        val pos = _uiState.value.selectedCell
        val newText = if (_uiState.value.formulaBarText.isEmpty()) "=$funcName()" else "${_uiState.value.formulaBarText}+$funcName()"
        _uiState.update { it.copy(formulaBarText = newText) }
    }

    fun importOcrTable(ocrResult: OcrDocumentResult) {
        val tableWb = ocrEngine.extractTableToWorkbook(ocrResult)
        val cmd = object : Command<CalcWorkbook> {
            override val description = "Import OCR Table"
            override fun execute(target: CalcWorkbook): CalcWorkbook = tableWb
            override fun undo(target: CalcWorkbook): CalcWorkbook = target
        }
        val next = undoManager.execute(cmd, _uiState.value.workbook)
        _uiState.update {
            it.copy(
                workbook = next,
                isDirty = true,
                selectedCell = CellPosition(0, 0),
                formulaBarText = next.activeSheet.getCellOrEmpty(0, 0).rawValue,
                canUndo = undoManager.canUndo,
                canRedo = undoManager.canRedo
            )
        }
    }

    fun undo() {
        if (!undoManager.canUndo) return
        val current = _uiState.value.workbook
        val reverted = undoManager.undo(current)
        _uiState.update {
            it.copy(
                workbook = reverted,
                isDirty = true,
                canUndo = undoManager.canUndo,
                canRedo = undoManager.canRedo,
                formulaBarText = reverted.activeSheet.getCellOrEmpty(it.selectedCell.col, it.selectedCell.row).rawValue
            )
        }
    }

    fun redo() {
        if (!undoManager.canRedo) return
        val current = _uiState.value.workbook
        val reapplied = undoManager.redo(current)
        _uiState.update {
            it.copy(
                workbook = reapplied,
                isDirty = true,
                canUndo = undoManager.canUndo,
                canRedo = undoManager.canRedo,
                formulaBarText = reapplied.activeSheet.getCellOrEmpty(it.selectedCell.col, it.selectedCell.row).rawValue
            )
        }
    }
}
