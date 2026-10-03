package com.disruptorh.qrgenerator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.disruptorh.qrgenerator.core.ContentType
import com.disruptorh.qrgenerator.core.Ecc
import com.disruptorh.qrgenerator.core.Inputs
import com.disruptorh.qrgenerator.core.WifiAuth

/** Header for a group of controls inside the settings sheet. */
@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * One labelled text input. [field] is the identifier the core reports in a
 * validation error, so the exact widget that is wrong is the one marked.
 */
@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    field: String = "",
    errorField: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    password: Boolean = false,
    hint: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine && minLines == 1,
        minLines = minLines,
        isError = field.isNotEmpty() && field == errorField,
        visualTransformation = if (password) PasswordVisualTransformation() else
            VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
        supportingText = hint?.let { note -> { Text(note) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceField(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = labelOf(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(labelOf(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Int,
    range: IntRange,
    steps: Int = 0,
    valueLabel: String = value.toString(),
    onChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueLabel, style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = steps,
        )
    }
}

/** Colour swatch that opens a small palette, for the module and background colours. */
@Composable
fun ColorRow(label: String, rgb: Int, onChange: (Int) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = hex(rgb),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(end = 8.dp),
            )
            Swatch(rgb, onClick = { picking = true })
        }
    }
    if (picking) {
        ColorPickerDialog(rgb = rgb, onChange = onChange, onDismiss = { picking = false })
    }
}

@Composable
private fun Swatch(rgb: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(Color(rgb), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun ColorPickerDialog(rgb: Int, onChange: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(hex(rgb)) }
    val presets = listOf(
        0x000000, 0xFFFFFF, 0x1A73E8, 0xD93025,
        0x188038, 0xE37400, 0x7B1FA2, 0x00ACC1,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (preset in presets) {
                        Swatch(preset, onClick = {
                            text = hex(preset)
                            onChange(preset)
                        })
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { entry ->
                        text = entry.uppercase()
                        parseHex(entry)?.let { onChange(it) }
                    },
                    label = { Text("Hexadecimal") },
                    singleLine = true,
                    isError = parseHex(text) == null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Listo") }
        },
    )
}

private fun hex(rgb: Int): String {
    val digits = "0123456789ABCDEF"
    val out = StringBuilder(6)
    for (shift in intArrayOf(20, 16, 12, 8, 4, 0)) out.append(digits[(rgb shr shift) and 0xF])
    return out.toString()
}

private fun parseHex(text: String): Int? {
    val digits = text.removePrefix("#")
    if (digits.length != 6) return null
    val value = digits.toIntOrNull(16) ?: return null
    return value and 0xFFFFFF
}

/** Content types as a chip row. Lazy so the nine types cost nothing to lay out. */
@Composable
fun TypeSelector(selected: ContentType, onSelect: (ContentType) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ContentType.entries) { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onSelect(type) },
                label = { Text(type.label) },
            )
        }
    }
}

/** The per-type field editor. Mirrors the desktop window's panels one for one. */
@Composable
fun ContentFields(
    inputs: Inputs,
    errorField: String,
    onChange: ((Inputs) -> Inputs) -> Unit,
) {
    when (inputs.type) {
        ContentType.text -> Field(
            label = "Texto",
            value = inputs.text,
            onChange = { text -> onChange { it.copy(text = text) } },
            field = "body",
            errorField = errorField,
            singleLine = false,
            minLines = 4,
        )

        ContentType.url -> Field(
            label = "URL",
            value = inputs.url,
            onChange = { url -> onChange { it.copy(url = url) } },
            field = "url",
            errorField = errorField,
            keyboardType = KeyboardType.Uri,
            capitalization = KeyboardCapitalization.None,
        )

        ContentType.wifi -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(
                label = "Red (SSID)",
                value = inputs.ssid,
                onChange = { ssid -> onChange { it.copy(ssid = ssid) } },
                field = "ssid",
                errorField = errorField,
                capitalization = KeyboardCapitalization.None,
            )
            ChoiceField(
                label = "Seguridad",
                options = WifiAuth.entries,
                selected = inputs.wifiAuth,
                labelOf = { it.label },
                onSelect = { auth -> onChange { it.copy(wifiAuth = auth) } },
            )
            if (inputs.wifiAuth != WifiAuth.nopass) {
                Field(
                    label = "Contraseña",
                    value = inputs.wifiPassword,
                    onChange = { pass -> onChange { it.copy(wifiPassword = pass) } },
                    field = "password",
                    errorField = errorField,
                    password = true,
                    capitalization = KeyboardCapitalization.None,
                )
            }
            SwitchRow(
                label = "Red oculta",
                checked = inputs.wifiHidden,
                onChange = { hidden -> onChange { it.copy(wifiHidden = hidden) } },
            )
        }

        ContentType.vcard -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(
                label = "Nombre",
                value = inputs.fullName,
                onChange = { name -> onChange { it.copy(fullName = name) } },
                field = "full_name",
                errorField = errorField,
            )
            Field(
                label = "Organización",
                value = inputs.organization,
                onChange = { org -> onChange { it.copy(organization = org) } },
            )
            Field(
                label = "Cargo",
                value = inputs.title,
                onChange = { title -> onChange { it.copy(title = title) } },
            )
            RepeatedFields(
                label = "Teléfono",
                // The core reports one vCard error identifier, so a bad phone has
                // no field of its own to light up.
                field = "",
                errorField = errorField,
                values = inputs.phones,
                keyboardType = KeyboardType.Phone,
                onChange = { phones -> onChange { it.copy(phones = phones) } },
            )
            RepeatedFields(
                label = "Correo",
                field = "emails",
                errorField = errorField,
                values = inputs.emails,
                keyboardType = KeyboardType.Email,
                onChange = { emails -> onChange { it.copy(emails = emails) } },
            )
            Field(
                label = "Web",
                value = inputs.contactUrl,
                onChange = { url -> onChange { it.copy(contactUrl = url) } },
                keyboardType = KeyboardType.Uri,
                capitalization = KeyboardCapitalization.None,
            )
            Field(
                label = "Dirección",
                value = inputs.address,
                onChange = { address -> onChange { it.copy(address = address) } },
                singleLine = false,
                minLines = 2,
            )
        }

        ContentType.email -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(
                label = "Destinatario",
                value = inputs.emailAddress,
                onChange = { address -> onChange { it.copy(emailAddress = address) } },
                field = "address",
                errorField = errorField,
                keyboardType = KeyboardType.Email,
                capitalization = KeyboardCapitalization.None,
            )
            Field(
                label = "Asunto",
                value = inputs.emailSubject,
                onChange = { subject -> onChange { it.copy(emailSubject = subject) } },
            )
            Field(
                label = "Mensaje",
                value = inputs.emailBody,
                onChange = { body -> onChange { it.copy(emailBody = body) } },
                field = "body",
                errorField = errorField,
                singleLine = false,
                minLines = 3,
            )
        }

        ContentType.sms -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(
                label = "Número",
                value = inputs.smsNumber,
                onChange = { number -> onChange { it.copy(smsNumber = number) } },
                field = "number",
                errorField = errorField,
                keyboardType = KeyboardType.Phone,
            )
            Field(
                label = "Mensaje",
                value = inputs.smsMessage,
                onChange = { message -> onChange { it.copy(smsMessage = message) } },
                field = "message",
                errorField = errorField,
                singleLine = false,
                minLines = 3,
            )
        }

        ContentType.phone -> Field(
            label = "Número",
            value = inputs.phoneNumber,
            onChange = { number -> onChange { it.copy(phoneNumber = number) } },
            field = "number",
            errorField = errorField,
            keyboardType = KeyboardType.Phone,
        )

        ContentType.geo -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(
                label = "Latitud",
                value = inputs.latitude,
                onChange = { value -> onChange { it.copy(latitude = value) } },
                field = "latitude",
                errorField = errorField,
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
                hint = "Entre -90 y 90",
            )
            Field(
                label = "Longitud",
                value = inputs.longitude,
                onChange = { value -> onChange { it.copy(longitude = value) } },
                field = "longitude",
                errorField = errorField,
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
                hint = "Entre -180 y 180",
            )
            Field(
                label = "Altitud (m)",
                value = inputs.altitude,
                onChange = { value -> onChange { it.copy(altitude = value) } },
                field = "altitude",
                errorField = errorField,
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
                hint = "Opcional. Se escribe como geo:lat,lon,alt",
            )
        }

        ContentType.event -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(
                label = "Título",
                value = inputs.eventSummary,
                onChange = { value -> onChange { it.copy(eventSummary = value) } },
                field = "summary",
                errorField = errorField,
            )
            Field(
                label = "Inicio",
                value = inputs.eventStart,
                onChange = { value -> onChange { it.copy(eventStart = value) } },
                hint = "AAAA-MM-DDTHH:MM",
            )
            Field(
                label = "Fin",
                value = inputs.eventEnd,
                onChange = { value -> onChange { it.copy(eventEnd = value) } },
            )
            Field(
                label = "Lugar",
                value = inputs.eventLocation,
                onChange = { value -> onChange { it.copy(eventLocation = value) } },
            )
            Field(
                label = "Descripción",
                value = inputs.eventDescription,
                onChange = { value -> onChange { it.copy(eventDescription = value) } },
                field = "description",
                errorField = errorField,
                singleLine = false,
                minLines = 2,
            )
        }
    }
}

@Composable
private fun RepeatedFields(
    label: String,
    field: String,
    errorField: String,
    values: List<String>,
    keyboardType: KeyboardType,
    onChange: (List<String>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEachIndexed { index, value ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(
                    label = "$label ${index + 1}",
                    value = value,
                    onChange = { updated ->
                        onChange(values.toMutableList().also { it[index] = updated })
                    },
                    modifier = Modifier.weight(1f),
                    field = field,
                    errorField = errorField,
                    keyboardType = keyboardType,
                    capitalization = KeyboardCapitalization.None,
                )
                IconButton(onClick = {
                    onChange(values.filterIndexed { position, _ -> position != index })
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Quitar $label")
                }
            }
        }
        if (values.size < 3) {
            TextButton(onClick = { onChange(values + "") }) {
                Text("Añadir ${label.lowercase()}")
            }
        }
    }
}

@Composable
fun EncodeFields(inputs: Inputs, onChange: ((Inputs) -> Inputs) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceField(
            label = "Corrección de errores",
            options = Ecc.entries,
            selected = inputs.ecc,
            labelOf = { it.label },
            onSelect = { ecc -> onChange { it.copy(ecc = ecc) } },
        )
        SwitchRow(
            label = "Reforzar si cabe en la versión",
            checked = inputs.boostEcc,
            onChange = { boost -> onChange { it.copy(boostEcc = boost) } },
        )
        ChoiceField(
            label = "Máscara",
            options = MASK_OPTIONS,
            selected = inputs.mask,
            labelOf = { if (it < 0) "Automática" else it.toString() },
            onSelect = { mask -> onChange { it.copy(mask = mask) } },
        )
        SliderRow(
            label = "Versión mínima",
            value = inputs.minVersion,
            range = 1..40,
            valueLabel = inputs.minVersion.toString(),
            // The core rejects an inverted range, so the pair is kept ordered here.
            onChange = { min ->
                onChange { it.copy(minVersion = min, maxVersion = maxOf(min, it.maxVersion)) }
            },
        )
        SliderRow(
            label = "Versión máxima",
            value = inputs.maxVersion,
            range = 1..40,
            valueLabel = inputs.maxVersion.toString(),
            onChange = { max ->
                onChange { it.copy(maxVersion = max, minVersion = minOf(max, it.minVersion)) }
            },
        )
    }
}

private val MASK_OPTIONS = listOf(-1, 0, 1, 2, 3, 4, 5, 6, 7)