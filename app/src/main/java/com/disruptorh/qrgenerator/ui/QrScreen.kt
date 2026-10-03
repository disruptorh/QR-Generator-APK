package com.disruptorh.qrgenerator.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.disruptorh.qrgenerator.QrViewModel
import com.disruptorh.qrgenerator.core.Inputs
import com.disruptorh.qrgenerator.core.OutputFormat

// The core refuses to rasterize past this many pixels on a side. The screen keeps
// the slider inside the bound so the user is never offered a size that the core
// will only reject.
private const val MAX_EXPORT_SIDE_PX = 4096
private const val MAX_MODULE_PX = 32

/** At this width the preview and the editor sit side by side instead of stacked. */
private val TwoPaneWidth = 600.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScreen(
    viewModel: QrViewModel,
    onSave: (OutputFormat) -> Unit,
    onShare: (OutputFormat) -> Unit,
    onCopy: (String) -> Unit,
    notice: String?,
    onNoticeConsumed: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val inputs by viewModel.inputs.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var zoomed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(notice) {
        if (!notice.isNullOrEmpty()) {
            snackbar.showSnackbar(notice)
            onNoticeConsumed()
        }
    }

    val generated = state.generated
    val modules = generated?.modules ?: 25
    val side = modules + 2 * inputs.quietZone
    val maxModulePx = (MAX_EXPORT_SIDE_PX / side).coerceIn(1, MAX_MODULE_PX)
    // A bigger symbol at the same pixel size no longer fits, so the stored value
    // follows the symbol instead of waiting to fail at export time.
    LaunchedEffect(maxModulePx) {
        if (inputs.modulePx > maxModulePx) viewModel.update { it.copy(modulePx = maxModulePx) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Código QR") },
                actions = {
                    FormatToggle(
                        selected = inputs.format,
                        onSelect = { format -> viewModel.update { it.copy(format = format) } },
                    )
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                },
            )
        },
        bottomBar = {
            ActionBar(
                enabled = generated != null,
                onSave = { onSave(inputs.format) },
                onShare = { onShare(inputs.format) },
                onCopy = { generated?.payload?.let(onCopy) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (maxWidth >= TwoPaneWidth) {
                Row(modifier = Modifier.fillMaxSize()) {
                    PreviewPane(
                        state = state,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        onZoom = { zoomed = true },
                    )
                    EditorPane(
                        inputs = inputs,
                        errorField = state.result.errorField,
                        onChange = viewModel::update,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    PreviewPane(
                        state = state,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onZoom = { zoomed = true },
                    )
                    EditorPane(
                        inputs = inputs,
                        errorField = state.result.errorField,
                        onChange = viewModel::update,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (settingsOpen) {
        SettingsSheet(
            inputs = inputs,
            maxModulePx = maxModulePx,
            onChange = viewModel::update,
            onDismiss = { settingsOpen = false },
        )
    }

    if (zoomed && generated != null) {
        PreviewDialog(
            state = state,
            format = inputs.format,
            onCopy = { generated.payload.let(onCopy) },
            onDismiss = { zoomed = false },
        )
    }
}

/**
 * The symbol, always on screen: one square as large as the space allows, plus the
 * core's own one-line summary. Tapping it opens the full-screen view, so nothing
 * else has to live under the preview.
 */
@Composable
private fun PreviewPane(
    state: QrViewModel.UiState,
    modifier: Modifier = Modifier,
    onZoom: () -> Unit,
) {
    val generated = state.generated
    val bitmap = state.preview
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                generated == null -> Text(
                    text = state.result.errorMessage.ifEmpty {
                        "Escribe el contenido y el código aparece aquí."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = if (state.result.errorMessage.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )

                bitmap == null -> CircularProgressIndicator()

                else -> Image(
                    // Nearest-neighbour scaling keeps every module a crisp square,
                    // so what is on screen is the symbol that gets exported.
                    painter = remember(bitmap) {
                        BitmapPainter(bitmap.asImageBitmap(), filterQuality = FilterQuality.None)
                    },
                    contentDescription = "Código QR generado",
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable(onClick = onZoom),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        if (generated != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = generated.describe,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Content type and its fields. The only part of the screen that scrolls. */
@Composable
private fun EditorPane(
    inputs: Inputs,
    errorField: String,
    onChange: ((Inputs) -> Inputs) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        TypeSelector(
            selected = inputs.type,
            onSelect = { type -> onChange { it.copy(type = type) } },
        )
        HorizontalDivider()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ContentFields(inputs = inputs, errorField = errorField, onChange = onChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatToggle(selected: OutputFormat, onSelect: (OutputFormat) -> Unit) {
    val options = OutputFormat.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(end = 4.dp)) {
        for (option in options) {
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = option.ordinal,
                    count = options.size,
                ),
            ) {
                Text(option.label)
            }
        }
    }
}

@Composable
private fun ActionBar(
    enabled: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Three buttons share one narrow row, so the label keeps a single
            // line: less padding, smaller type, no wrap.
            Button(
                onClick = onSave,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text("Guardar", maxLines = 1)
            }
            OutlinedButton(
                onClick = onShare,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text("Compartir", maxLines = 1, softWrap = false)
            }
            OutlinedButton(
                onClick = onCopy,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text("Copiar", maxLines = 1)
            }
        }
    }
}

/**
 * Encoding and rendering options. They are needed rarely and never while typing,
 * so they live in a sheet instead of pushing the editor below the fold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    inputs: Inputs,
    maxModulePx: Int,
    onChange: ((Inputs) -> Inputs) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle("Codificación")
            EncodeFields(inputs = inputs, onChange = onChange)
            HorizontalDivider()
            SectionTitle("Imagen")
            SliderRow(
                label = "Px por módulo",
                value = inputs.modulePx.coerceIn(1, maxModulePx),
                range = 1..maxModulePx,
                onChange = { px -> onChange { it.copy(modulePx = px) } },
            )
            SliderRow(
                label = "Zona de silencio",
                value = inputs.quietZone,
                range = 0..8,
                onChange = { quiet -> onChange { it.copy(quietZone = quiet) } },
            )
            ColorRow(
                label = "Color del módulo",
                rgb = inputs.foreground,
                onChange = { rgb -> onChange { it.copy(foreground = rgb) } },
            )
            ColorRow(
                label = "Fondo",
                rgb = inputs.background,
                onChange = { rgb -> onChange { it.copy(background = rgb) } },
            )
        }
    }
}

/** Full-screen symbol with the payload and the export size, for close inspection. */
@Composable
private fun PreviewDialog(
    state: QrViewModel.UiState,
    format: OutputFormat,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    val generated = state.generated ?: return
    val bitmap = state.preview ?: return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.86f),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = remember(bitmap) {
                        BitmapPainter(bitmap.asImageBitmap(), filterQuality = FilterQuality.None)
                    },
                    contentDescription = "Código QR generado",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    contentScale = ContentScale.Fit,
                )
                Text(
                    text = generated.describe,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Datos: ${generated.usedBytes} de ${generated.capacityBytes} bytes " +
                        "(${generated.usagePercent}%)  |  ${format.label}: ${generated.pixelSize}  |  " +
                        "Verificado: ${generated.verifiedBlocks} bloques",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = generated.payload,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCopy) { Text("Copiar contenido") }
                    Button(onClick = onDismiss) { Text("Cerrar") }
                }
            }
        }
    }
}