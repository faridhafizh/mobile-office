package com.libremobile.feature.impress

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.libremobile.core.model.common.DocumentType
import com.libremobile.core.ui.canvas.SlideViewportCanvas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImpressScreen(
    viewModel: ImpressViewModel,
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
                    // Add Slide
                    IconButton(onClick = { viewModel.addSlide() }) {
                        Icon(Icons.Default.PostAdd, contentDescription = "Add Slide")
                    }
                    // Delete Slide
                    IconButton(
                        onClick = { viewModel.deleteCurrentSlide() },
                        enabled = uiState.document.slides.size > 1
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Slide")
                    }
                    // Export to PDF
                    IconButton(onClick = { onSaveRequested(DocumentType.WRITER_PDF) }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                    // Save
                    IconButton(onClick = { onSaveRequested(uiState.documentType) }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SlideViewportCanvas(
                document = uiState.document,
                activeSlideIndex = uiState.activeSlideIndex,
                onSlideSelect = { viewModel.selectSlide(it) }
            )
        }
    }
}
