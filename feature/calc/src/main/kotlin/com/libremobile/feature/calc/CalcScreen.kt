package com.libremobile.feature.calc

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.libremobile.core.model.common.DocumentType
import com.libremobile.core.ui.canvas.SpreadsheetGridCanvas
import com.libremobile.core.ui.component.FormulaBarView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalcScreen(
    viewModel: CalcViewModel,
    onNavigateBack: () -> Unit,
    onSaveRequested: (DocumentType) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.fileName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (uiState.isDirty) {
                            Text(
                                text = "Edited • Unsaved",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }, enabled = uiState.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { viewModel.redo() }, enabled = uiState.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = { onSaveRequested(DocumentType.WRITER_PDF) }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                    IconButton(onClick = { onSaveRequested(uiState.documentType) }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Formula Bar
            FormulaBarView(
                cellCoordinate = uiState.selectedCell.a1String,
                formulaText = uiState.formulaBarText,
                onFormulaChange = { viewModel.updateFormulaText(it) },
                onApplyFormula = { viewModel.applyCellEdit() },
                onCancelFormula = { viewModel.selectCell(uiState.selectedCell) },
                onInsertFunction = { viewModel.insertFunction(it) }
            )

            // Sheet Tabs
            ScrollableTabRow(
                selectedTabIndex = uiState.workbook.activeSheetIndex,
                edgePadding = 0.dp
            ) {
                uiState.workbook.sheets.forEachIndexed { index, sheet ->
                    Tab(
                        selected = index == uiState.workbook.activeSheetIndex,
                        onClick = { /* switch sheet */ },
                        text = { Text(sheet.name, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Infinite 2D Grid Canvas
            SpreadsheetGridCanvas(
                sheet = uiState.workbook.activeSheet,
                selectedCell = uiState.selectedCell,
                onCellSelected = { viewModel.selectCell(it) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
