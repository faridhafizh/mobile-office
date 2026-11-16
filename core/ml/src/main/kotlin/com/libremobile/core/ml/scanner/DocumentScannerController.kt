package com.libremobile.core.ml.scanner

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScannedDocumentResult(
    val pageImageUris: List<Uri>,
    val pdfUri: Uri?,
    val pageCount: Int
)

class DocumentScannerController(
    private val context: Context
) {
    private val scannerOptions: GmsDocumentScannerOptions = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setPageLimit(100)
        .setResultFormats(
            GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
            GmsDocumentScannerOptions.RESULT_FORMAT_PDF
        )
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .build()

    val client: GmsDocumentScanner by lazy {
        GmsDocumentScanning.getClient(scannerOptions)
    }

    fun isGooglePlayServicesAvailable(): Boolean {
        val availability = GoogleApiAvailability.getInstance()
        val code = availability.isGooglePlayServicesAvailable(context)
        return code == ConnectionResult.SUCCESS
    }

    fun startScan(activity: ComponentActivity, onIntentReady: (IntentSenderRequest) -> Unit) {
        if (isGooglePlayServicesAvailable()) {
            client.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    onIntentReady(IntentSenderRequest.Builder(intentSender).build())
                }
        }
    }

    suspend fun parseScanResult(data: GmsDocumentScanningResult): ScannedDocumentResult = withContext(Dispatchers.Default) {
        val imageUris = mutableListOf<Uri>()
        data.pages?.forEach { page ->
            page.imageUri?.let { imageUris.add(it) }
        }
        val pdfUri = data.pdf?.uri

        ScannedDocumentResult(
            pageImageUris = imageUris,
            pdfUri = pdfUri,
            pageCount = imageUris.size
        )
    }
}
