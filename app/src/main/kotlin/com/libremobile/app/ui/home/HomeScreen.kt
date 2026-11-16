package com.libremobile.app.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.libremobile.app.data.db.RecentDocument
import com.libremobile.app.data.db.RecentDocumentRepository
import com.libremobile.core.model.common.DocumentType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: RecentDocumentRepository,
    onNewWriter: () -> Unit,
    onNewCalc: () -> Unit,
    onNewImpress: () -> Unit,
    onOpenUri: (Uri, DocumentType) -> Unit,
    onLaunchScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val recentDocs by repository.recentDocuments.collectAsState()

    // SAF Open Document Picker
    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = uri.path ?: ""
            val ext = path.substringAfterLast(".", "docx").lowercase()
            val type = DocumentType.fromExtension(ext) ?: DocumentType.WRITER_DOCX
            onOpenUri(uri, type)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Article,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("LibreMobile", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        openDocLauncher.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.oasis.opendocument.text",
                                "text/plain",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.oasis.opendocument.spreadsheet",
                                "text/csv",
                                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                "application/vnd.oasis.opendocument.presentation",
                                "*/*"
                            )
                        )
                    }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Open File")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onLaunchScanner,
                icon = { Icon(Icons.Default.DocumentScanner, contentDescription = null) },
                text = { Text("ML Scan") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Quick Create Section
            item {
                Text(
                    text = "Create New Document",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SuiteActionCard(
                        title = "Writer",
                        subtitle = "DOCX / ODT",
                        icon = Icons.Default.Description,
                        color = Color(0xFF1E88E5),
                        onClick = onNewWriter,
                        modifier = Modifier.weight(1f)
                    )
                    SuiteActionCard(
                        title = "Calc",
                        subtitle = "XLSX / ODS",
                        icon = Icons.Default.TableChart,
                        color = Color(0xFF2E7D32),
                        onClick = onNewCalc,
                        modifier = Modifier.weight(1f)
                    )
                    SuiteActionCard(
                        title = "Impress",
                        subtitle = "PPTX / ODP",
                        icon = Icons.Default.Slideshow,
                        color = Color(0xFFF57C00),
                        onClick = onNewImpress,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Recent Documents Section
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "Recent Documents",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${recentDocs.size} files",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (recentDocs.isEmpty()) {
                item {
                    Surface(
                        tonalElevation = 1.dp,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No recent documents yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Create a document above or scan pages with ML Kit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(recentDocs) { doc ->
                    RecentDocumentItem(
                        doc = doc,
                        onClick = { onOpenUri(Uri.parse(doc.uriString), doc.documentType) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SuiteActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(14.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = color
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentDocumentItem(
    doc: RecentDocument,
    onClick: () -> Unit
) {
    val icon = when (doc.documentType) {
        DocumentType.WRITER_DOCX, DocumentType.WRITER_ODT, DocumentType.WRITER_TXT, DocumentType.WRITER_PDF -> Icons.Default.Description
        DocumentType.CALC_XLSX, DocumentType.CALC_ODS, DocumentType.CALC_CSV -> Icons.Default.TableChart
        DocumentType.IMPRESS_PPTX, DocumentType.IMPRESS_ODP -> Icons.Default.Slideshow
    }

    val iconColor = when (doc.documentType) {
        DocumentType.WRITER_DOCX, DocumentType.WRITER_ODT, DocumentType.WRITER_TXT, DocumentType.WRITER_PDF -> Color(0xFF1E88E5)
        DocumentType.CALC_XLSX, DocumentType.CALC_ODS, DocumentType.CALC_CSV -> Color(0xFF2E7D32)
        DocumentType.IMPRESS_PPTX, DocumentType.IMPRESS_ODP -> Color(0xFFF57C00)
    }

    val dateStr = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(doc.lastAccessedTimestamp))

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = iconColor.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Text(
                    text = "${doc.documentType.extension.uppercase()} • $dateStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
