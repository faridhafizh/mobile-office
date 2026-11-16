package com.libremobile.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.libremobile.app.data.db.RecentDocumentDatabase
import com.libremobile.app.data.db.RecentDocumentRepository
import com.libremobile.app.saf.StorageAccessManager
import com.libremobile.app.ui.navigation.LibreNavHost
import com.libremobile.app.ui.navigation.Screen
import com.libremobile.core.model.common.DocumentType
import com.libremobile.core.ui.theme.LibreMobileTheme

class MainActivity : ComponentActivity() {

    private val dbHelper by lazy { RecentDocumentDatabase(applicationContext) }
    private val repository by lazy { RecentDocumentRepository(dbHelper) }
    private val storageManager by lazy { StorageAccessManager(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LibreMobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    LaunchedEffect(Unit) {
                        repository.refresh()

                        // Handle incoming document intent from external apps
                        intent?.data?.let { uri ->
                            storageManager.takePersistablePermission(uri)
                            val name = storageManager.getFileName(uri)
                            val type = storageManager.detectDocumentType(name)
                            repository.recordAccess(uri.toString(), name, type, storageManager.getFileSize(uri))

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
                        }
                    }

                    LibreNavHost(
                        navController = navController,
                        repository = repository,
                        storageManager = storageManager
                    )
                }
            }
        }
    }
}
