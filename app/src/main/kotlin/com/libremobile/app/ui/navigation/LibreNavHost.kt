package com.libremobile.app.ui.navigation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.libremobile.app.data.db.RecentDocumentRepository
import com.libremobile.app.saf.StorageAccessManager
import com.libremobile.app.ui.home.HomeScreen
import com.libremobile.core.ml.scanner.DocumentScannerController
import com.libremobile.core.model.common.DocumentType
import com.libremobile.feature.calc.CalcScreen
import com.libremobile.feature.calc.CalcViewModel
import com.libremobile.feature.impress.ImpressScreen
import com.libremobile.feature.impress.ImpressViewModel
import com.libremobile.feature.writer.WriterScreen
import com.libremobile.feature.writer.WriterViewModel
import kotlinx.coroutines.launch
import java.net.URLDecoder

@Composable
fun LibreNavHost(
    navController: NavHostController,
    repository: RecentDocumentRepository,
    storageManager: StorageAccessManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scannerController = remember { DocumentScannerController(context) }

    // Document Scanner launcher
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // Handle scanned document
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        // 1. Home Dashboard
        composable(Screen.Home.route) {
            HomeScreen(
                repository = repository,
                onNewWriter = { navController.navigate(Screen.Writer.createRoute(null)) },
                onNewCalc = { navController.navigate(Screen.Calc.createRoute(null)) },
                onNewImpress = { navController.navigate(Screen.Impress.createRoute(null)) },
                onOpenUri = { uri, type ->
                    storageManager.takePersistablePermission(uri)
                    val name = storageManager.getFileName(uri)
                    val size = storageManager.getFileSize(uri)
                    scope.launch {
                        repository.recordAccess(uri.toString(), name, type, size)
                    }
                    when (type) {
                        DocumentType.WRITER_DOCX, DocumentType.WRITER_ODT, DocumentType.WRITER_TXT, DocumentType.WRITER_PDF -> {
                            navController.navigate(Screen.Writer.createRoute(uri.toString()))
                        }
                        DocumentType.CALC_XLSX, DocumentType.CALC_ODS, DocumentType.CALC_CSV -> {
                            navController.navigate(Screen.Calc.createRoute(uri.toString()))
                        }
                        DocumentType.IMPRESS_PPTX, DocumentType.IMPRESS_ODP -> {
                            navController.navigate(Screen.Impress.createRoute(uri.toString()))
                        }
                    }
                },
                onLaunchScanner = {
                    (context as? androidx.activity.ComponentActivity)?.let { activity ->
                        scannerController.startScan(activity) { request ->
                            scannerLauncher.launch(request)
                        }
                    }
                }
            )
        }

        // 2. Writer Screen
        composable(
            route = Screen.Writer.route,
            arguments = listOf(navArgument("uri") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val uriArg = backStackEntry.arguments?.getString("uri")
            val writerVm: WriterViewModel = viewModel()

            // SAF Save Document Picker
            val saveDocLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            ) { saveUri: Uri? ->
                if (saveUri != null) {
                    storageManager.openOutputStream(saveUri)?.use { out ->
                        writerVm.saveDocument(out)
                    }
                }
            }

            if (uriArg != null) {
                val decoded = URLDecoder.decode(uriArg, "UTF-8")
                val uri = Uri.parse(decoded)
                remember(uri) {
                    storageManager.openInputStream(uri)?.use { stream ->
                        val fileName = storageManager.getFileName(uri)
                        writerVm.loadDocument(stream, fileName)
                    }
                    true
                }
            }

            WriterScreen(
                viewModel = writerVm,
                onNavigateBack = { navController.popBackStack() },
                onSaveRequested = { docType ->
                    val defaultName = if (docType == DocumentType.WRITER_PDF) "Document.pdf" else "Document.docx"
                    saveDocLauncher.launch(defaultName)
                }
            )
        }

        // 3. Calc Screen
        composable(
            route = Screen.Calc.route,
            arguments = listOf(navArgument("uri") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val uriArg = backStackEntry.arguments?.getString("uri")
            val calcVm: CalcViewModel = viewModel()

            val saveDocLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            ) { saveUri: Uri? ->
                if (saveUri != null) {
                    storageManager.openOutputStream(saveUri)?.use { out ->
                        calcVm.saveWorkbook(out)
                    }
                }
            }

            if (uriArg != null) {
                val decoded = URLDecoder.decode(uriArg, "UTF-8")
                val uri = Uri.parse(decoded)
                remember(uri) {
                    storageManager.openInputStream(uri)?.use { stream ->
                        val fileName = storageManager.getFileName(uri)
                        calcVm.loadWorkbook(stream, fileName)
                    }
                    true
                }
            }

            CalcScreen(
                viewModel = calcVm,
                onNavigateBack = { navController.popBackStack() },
                onSaveRequested = { docType ->
                    val defaultName = if (docType == DocumentType.WRITER_PDF) "Spreadsheet.pdf" else "Spreadsheet.xlsx"
                    saveDocLauncher.launch(defaultName)
                }
            )
        }

        // 4. Impress Screen
        composable(
            route = Screen.Impress.route,
            arguments = listOf(navArgument("uri") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val uriArg = backStackEntry.arguments?.getString("uri")
            val impressVm: ImpressViewModel = viewModel()

            val saveDocLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.presentationml.presentation")
            ) { saveUri: Uri? ->
                if (saveUri != null) {
                    storageManager.openOutputStream(saveUri)?.use { out ->
                        impressVm.savePresentation(out)
                    }
                }
            }

            if (uriArg != null) {
                val decoded = URLDecoder.decode(uriArg, "UTF-8")
                val uri = Uri.parse(decoded)
                remember(uri) {
                    storageManager.openInputStream(uri)?.use { stream ->
                        val fileName = storageManager.getFileName(uri)
                        impressVm.loadPresentation(stream, fileName)
                    }
                    true
                }
            }

            ImpressScreen(
                viewModel = impressVm,
                onNavigateBack = { navController.popBackStack() },
                onSaveRequested = { docType ->
                    val defaultName = if (docType == DocumentType.WRITER_PDF) "Presentation.pdf" else "Presentation.pptx"
                    saveDocLauncher.launch(defaultName)
                }
            )
        }
    }
}
