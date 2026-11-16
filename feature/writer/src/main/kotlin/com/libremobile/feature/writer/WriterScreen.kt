package com.libremobile.feature.writer

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.libremobile.core.model.common.DocumentType
import com.libremobile.core.ui.canvas.RichTextEditorCanvas
import com.libremobile.core.ui.component.EditorToolbar
import com.libremobile.core.ui.component.SmartActionSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriterScreen(
    viewModel: WriterViewModel,
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
                    // Export to PDF
                    IconButton(onClick = { onSaveRequested(DocumentType.WRITER_PDF) }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                    // Save document
                    IconButton(onClick = { onSaveRequested(uiState.documentType) }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )
        },
        bottomBar = {
            EditorToolbar(
                state = uiState.toolbarState,
                onBoldClick = { viewModel.toggleBold() },
                onItalicClick = { viewModel.toggleItalic() },
                onUnderlineClick = { viewModel.toggleUnderline() },
                onAlignClick = { viewModel.setAlignment(it) },
                onHeadingClick = { viewModel.setHeading(it) },
                onUndoClick = { viewModel.undo() },
                onRedoClick = { viewModel.redo() },
                onSmartActionClick = { viewModel.triggerSmartAnalysis() }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            RichTextEditorCanvas(
                document = uiState.document,
                onParagraphChange = { index, text -> viewModel.updateParagraph(index, text) }
            )

            if (uiState.showSmartSheet) {
                SmartActionSheet(
                    analysis = uiState.smartAnalysis,
                    onActionClick = { /* Handled via Intent in App */ },
                    onDismiss = { viewModel.hideSmartSheet() }
                )
            }
        }
    }
}
