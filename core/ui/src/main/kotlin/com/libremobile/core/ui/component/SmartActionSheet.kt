package com.libremobile.core.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.libremobile.core.ml.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartActionSheet(
    analysis: SmartDocAnalysis?,
    onActionClick: (SmartAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Offline Document Intelligence",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (analysis == null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else {
                // Document Stats Badge
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text("${analysis.wordCount} words") },
                        leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text("~${analysis.readingTimeSeconds}s read") },
                        leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text("Lang: ${analysis.languageCode.uppercase()}") },
                        leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                // Summary Points
                if (analysis.keySummaryPoints.isNotEmpty()) {
                    Text(
                        text = "Key Takeaways (Extractive Summary):",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    analysis.keySummaryPoints.forEach { point ->
                        Text(
                            text = "• $point",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                }

                // Smart Actions & Entities
                Text(
                    text = "Detected Context Actions:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                if (analysis.actions.isEmpty()) {
                    Text(
                        text = "No actionable entities detected in active document text.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                        items(analysis.actions) { action ->
                            val icon = when (action.type) {
                                SmartActionType.DIAL -> Icons.Default.Phone
                                SmartActionType.EMAIL -> Icons.Default.Email
                                SmartActionType.MAP -> Icons.Default.Place
                                SmartActionType.BROWSE -> Icons.Default.OpenInBrowser
                                SmartActionType.CALENDAR -> Icons.Default.CalendarToday
                                SmartActionType.TRANSLATE -> Icons.Default.Translate
                                else -> Icons.Default.SmartButton
                            }

                            ListItem(
                                headlineContent = { Text(action.label, style = MaterialTheme.typography.bodyMedium) },
                                supportingContent = { Text(action.payload, style = MaterialTheme.typography.labelSmall) },
                                leadingContent = {
                                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                trailingContent = {
                                    FilledTonalButton(
                                        onClick = { onActionClick(action) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Open", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
