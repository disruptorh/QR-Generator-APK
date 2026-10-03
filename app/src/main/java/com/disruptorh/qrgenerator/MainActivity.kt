package com.disruptorh.qrgenerator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.disruptorh.qrgenerator.core.OutputFormat
import com.disruptorh.qrgenerator.ui.QrScreen
import com.disruptorh.qrgenerator.ui.QrTheme
import java.io.File
import java.io.IOException

/**
 * Single-screen host. It owns the three things that need Android services --
 * the document picker, the share sheet and the clipboard -- and leaves everything
 * about the QR itself to the shared core.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: QrViewModel by viewModels()

    private var notice by mutableStateOf<String?>(null)

    /** Bytes waiting for the user to pick a destination for them. */
    private var pending: ExportFile? = null

    private lateinit var savePng: ActivityResultLauncher<String>
    private lateinit var saveSvg: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        savePng = registerForActivityResult(
            ActivityResultContracts.CreateDocument(OutputFormat.png.mime),
        ) { uri -> finishSave(uri) }
        saveSvg = registerForActivityResult(
            ActivityResultContracts.CreateDocument(OutputFormat.svg.mime),
        ) { uri -> finishSave(uri) }

        setContent {
            QrTheme {
                QrScreen(
                    viewModel = viewModel,
                    onSave = ::save,
                    onShare = ::share,
                    onCopy = ::copy,
                    notice = notice,
                    onNoticeConsumed = { notice = null },
                )
            }
        }
    }

    private fun save(format: OutputFormat) {
        val result = viewModel.export(format)
        val file = result.file
        if (file == null) {
            notice = result.error
            return
        }
        pending = file
        if (format == OutputFormat.png) {
            savePng.launch(file.filename)
        } else {
            saveSvg.launch(file.filename)
        }
    }

    private fun finishSave(uri: Uri?) {
        val file = pending
        pending = null
        if (uri == null || file == null) return
        val written = try {
            contentResolver.openOutputStream(uri)?.use { stream -> stream.write(file.bytes) } != null
        } catch (error: IOException) {
            false
        }
        notice = if (written) "Guardado: ${file.filename}" else "No se pudo escribir el archivo"
    }

    private fun share(format: OutputFormat) {
        val result = viewModel.export(format)
        val file = result.file
        if (file == null) {
            notice = result.error
            return
        }
        val directory = File(cacheDir, "shared").apply { mkdirs() }
        val target = File(directory, file.filename)
        try {
            target.writeBytes(file.bytes)
        } catch (error: IOException) {
            notice = "No se pudo preparar el archivo para compartir"
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", target)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = file.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Compartir el código QR"))
    }

    private fun copy(payload: String) {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Código QR", payload))
        notice = "Contenido copiado"
    }
}
