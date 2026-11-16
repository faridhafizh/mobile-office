package com.libremobile.core.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FormulaBarView(
    cellCoordinate: String,
    formulaText: String,
    onFormulaChange: (String) -> Unit,
    onApplyFormula: () -> Unit,
    onCancelFormula: () -> Unit,
    onInsertFunction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Coordinate Badge (e.g. A1)
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = cellCoordinate.ifEmpty { "A1" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // fx Icon
                Icon(
                    Icons.Default.Functions,
                    contentDescription = "Function",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp).padding(end = 4.dp)
                )

                // Formula Input Field
                OutlinedTextField(
                    value = formulaText,
                    onValueChange = onFormulaChange,
                    singleLine = true,
                    placeholder = { Text("Enter value or =SUM(A1:B5)...", style = MaterialTheme.typography.bodySmall) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        if (formulaText.isNotEmpty()) {
                            Row {
                                IconButton(onClick = onCancelFormula, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = onApplyFormula, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Check, contentDescription = "Apply", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                )
            }

            // Quick function suggestion chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                listOf("SUM", "AVERAGE", "IF", "MAX", "MIN", "COUNT", "VLOOKUP").forEach { func ->
                    SuggestionChip(
                        onClick = { onInsertFunction(func) },
                        label = { Text(func, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        }
    }
}
