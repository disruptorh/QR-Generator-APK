package com.disruptorh.qrgenerator

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.disruptorh.qrgenerator.core.Inputs
import com.disruptorh.qrgenerator.core.OutputFormat
import com.disruptorh.qrgenerator.core.QrCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** File bytes ready to write or share, plus the name the core suggested. */
data class ExportFile(val filename: String, val mime: String, val bytes: ByteArray)

data class ExportResult(val file: ExportFile?, val error: String)

/**
 * Holds the editor state and re-runs the core whenever it changes.
 *
 * Generation, verification and rasterizing are all a fraction of a millisecond,
 * so the flow recomputes straight from every keystroke instead of debouncing:
 * the preview on screen always describes the values just typed.
 */
class QrViewModel : ViewModel() {

    data class UiState(
        val result: QrCore.Result = QrCore.Result(null, "", ""),
        val preview: Bitmap? = null,
    ) {
        val generated: QrCore.Generated? get() = result.generated
    }

    /** The editor state; the screen reads it to draw the widgets. */
    val inputs = MutableStateFlow(Inputs())

    val state: StateFlow<UiState> = inputs
        .map { compute(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun update(block: (Inputs) -> Inputs) {
        inputs.value = block(inputs.value)
    }

    /** Renders the current inputs for export. Only a verified symbol is written. */
    fun export(format: OutputFormat): ExportResult {
        val current = inputs.value
        return try {
            val result = QrCore.generate(current)
            val generated = result.generated
                ?: return ExportResult(null, result.errorMessage)
            val bytes = when (format) {
                OutputFormat.png -> QrCore.exportPng(current)
                OutputFormat.svg -> QrCore.exportSvg(current)
            }
            ExportResult(ExportFile(generated.filename, format.mime, bytes), "")
        } catch (error: IllegalStateException) {
            ExportResult(null, error.message ?: "No se pudo exportar el código")
        }
    }

    private fun compute(inputs: Inputs): UiState {
        val result = try {
            QrCore.generate(inputs)
        } catch (error: IllegalStateException) {
            QrCore.Result(null, "", error.message ?: "El núcleo no pudo leer las entradas")
        }
        val symbol = result.generated
        val preview = if (symbol != null) {
            try {
                QrCore.preview(inputs, symbol.previewWidth, symbol.previewHeight)
            } catch (error: IllegalStateException) {
                null
            }
        } else {
            null
        }
        return UiState(result, preview)
    }
}
