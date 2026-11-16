package com.libremobile.core.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.libremobile.core.model.writer.HeadingLevel
import com.libremobile.core.model.writer.TextAlignment

data class EditorToolbarState(
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val alignment: TextAlignment = TextAlignment.LEFT,
    val headingLevel: HeadingLevel = HeadingLevel.NONE,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

@Composable
fun EditorToolbar(
    state: EditorToolbarState,
    onBoldClick: () -> Unit,
    onItalicClick: () -> Unit,
    onUnderlineClick: () -> Unit,
    onAlignClick: (TextAlignment) -> Unit,
    onHeadingClick: (HeadingLevel) -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onSmartActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            // Undo / Redo
            IconButton(onClick = onUndoClick, enabled = state.canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = onRedoClick, enabled = state.canRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }

            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

            // Bold
            FilterChip(
                selected = state.isBold,
                onClick = onBoldClick,
                label = { Icon(Icons.Default.FormatBold, contentDescription = "Bold", modifier = Modifier.size(18.dp)) }
            )
            Spacer(modifier = Modifier.width(4.dp))

            // Italic
            FilterChip(
                selected = state.isItalic,
                onClick = onItalicClick,
                label = { Icon(Icons.Default.FormatItalic, contentDescription = "Italic", modifier = Modifier.size(18.dp)) }
            )
            Spacer(modifier = Modifier.width(4.dp))

            // Underline
            FilterChip(
                selected = state.isUnderline,
                onClick = onUnderlineClick,
                label = { Icon(Icons.Default.FormatUnderlined, contentDescription = "Underline", modifier = Modifier.size(18.dp)) }
            )

            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

            // Align Left
            IconButton(onClick = { onAlignClick(TextAlignment.LEFT) }) {
                Icon(
                    Icons.Default.FormatAlignLeft,
                    contentDescription = "Align Left",
                    tint = if (state.alignment == TextAlignment.LEFT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            // Align Center
            IconButton(onClick = { onAlignClick(TextAlignment.CENTER) }) {
                Icon(
                    Icons.Default.FormatAlignCenter,
                    contentDescription = "Align Center",
                    tint = if (state.alignment == TextAlignment.CENTER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            // Align Right
            IconButton(onClick = { onAlignClick(TextAlignment.RIGHT) }) {
                Icon(
                    Icons.Default.FormatAlignRight,
                    contentDescription = "Align Right",
                    tint = if (state.alignment == TextAlignment.RIGHT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

            // Heading 1 toggle
            FilterChip(
                selected = state.headingLevel == HeadingLevel.H1,
                onClick = {
                    onHeadingClick(if (state.headingLevel == HeadingLevel.H1) HeadingLevel.NONE else HeadingLevel.H1)
                },
                label = { Text("H1") }
            )
            Spacer(modifier = Modifier.width(4.dp))

            FilterChip(
                selected = state.headingLevel == HeadingLevel.H2,
                onClick = {
                    onHeadingClick(if (state.headingLevel == HeadingLevel.H2) HeadingLevel.NONE else HeadingLevel.H2)
                },
                label = { Text("H2") }
            )

            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

            // ML Smart Assistant Button
            FilledTonalButton(
                onClick = onSmartActionClick,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("ML Smart Assist", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
