package com.app.reserve.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.reserve.core.data.ImportSummary
import com.app.reserve.core.database.TransactionType
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.SheetHandle
import com.app.reserve.core.designsystem.radius_md
import com.app.reserve.core.designsystem.spacing_lg
import com.app.reserve.core.designsystem.spacing_md
import com.app.reserve.core.designsystem.spacing_sm
import com.app.reserve.core.designsystem.spacing_xl
import com.app.reserve.core.domain.SmartField
import com.app.reserve.core.domain.SmartImportAnalysis
import com.app.reserve.core.domain.SmartImportMapping
import com.app.reserve.core.domain.SmartImporter
import kotlinx.coroutines.launch

/** Preview + editable mapping for the on-device universal importer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartImportSheet(
    analysis: SmartImportAnalysis,
    onDismiss: () -> Unit,
    onImport: suspend (List<com.app.reserve.core.data.ImportedTransaction>) -> ImportSummary,
    onResult: (String) -> Unit
) {
    val colors = LocalReserveColors.current
    val scope = rememberCoroutineScope()
    var mapping by remember(analysis) { mutableStateOf(analysis.mapping.copy()) }
    var defaultType by remember(analysis) { mutableStateOf(analysis.mapping.defaultType) }
    var amountIsMinor by remember(analysis) { mutableStateOf(analysis.mapping.amountIsMinor) }
    var busy by remember { mutableStateOf(false) }

    val previewRows = remember(analysis, mapping, defaultType, amountIsMinor) {
        SmartImporter.toTransactions(
            analysis = analysis,
            mapping = mapping,
            defaultType = defaultType,
            amountIsMinor = amountIsMinor
        )
    }
    val canImport = !busy && previewRows.isNotEmpty() && mapping.date >= 0 && mapping.amount >= 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        contentColor = colors.textBase,
        contentWindowInsets = { WindowInsets.ime },
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            SheetHandle()
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Import transactions",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textBase
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${analysis.format} · ${analysis.rows.size} rows found · ${previewRows.size} recognised",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textLighter
            )
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Column mapping", style = MaterialTheme.typography.labelMedium, color = colors.textLight)
                Spacer(Modifier.height(8.dp))
                MappingRow(SmartField.DATE, mapping.date, analysis.headers) { mapping = mapping.copy(date = it) }
                MappingRow(SmartField.AMOUNT, mapping.amount, analysis.headers) { mapping = mapping.copy(amount = it) }
                MappingRow(SmartField.TYPE, mapping.type, analysis.headers) { mapping = mapping.copy(type = it) }
                MappingRow(SmartField.CATEGORY, mapping.category, analysis.headers) { mapping = mapping.copy(category = it) }
                MappingRow(SmartField.ACCOUNT, mapping.account, analysis.headers) { mapping = mapping.copy(account = it) }
                MappingRow(SmartField.TARGET_ACCOUNT, mapping.targetAccount, analysis.headers) {
                    mapping = mapping.copy(targetAccount = it)
                }
                MappingRow(SmartField.NOTE, mapping.note, analysis.headers) { mapping = mapping.copy(note = it) }

                Spacer(Modifier.height(12.dp))
                Text("If no type column", style = MaterialTheme.typography.labelMedium, color = colors.textLight)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionType.entries.forEach { option ->
                        val selected = defaultType == option
                        Surface(
                            onClick = { defaultType = option },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(radius_md),
                            color = if (selected) colors.primaryContainer else colors.fillLight,
                            border = BorderStroke(1.dp, if (selected) colors.primaryDefault else colors.strokeFaint)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = option.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) colors.primaryDefault else colors.textLight
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = amountIsMinor, onCheckedChange = { amountIsMinor = it })
                    Text(
                        text = "Amounts are already in minor units",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textLight
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("Preview", style = MaterialTheme.typography.labelMedium, color = colors.textLight)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Column {
                        analysis.headers.forEach { header ->
                            Text(
                                text = header,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textLighter,
                                modifier = Modifier.width(130.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    analysis.rows.take(6).forEach { row ->
                        Column {
                            analysis.headers.indices.forEach { index ->
                                Text(
                                    text = row.getOrNull(index).orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textBase,
                                    modifier = Modifier.width(130.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(spacing_lg))
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        val summary = onImport(previewRows)
                        busy = false
                        onResult(
                            if (summary.skipped < 0) {
                                "No transactions could be imported."
                            } else {
                                "Imported ${summary.imported} transactions" +
                                    if (summary.skipped > 0) ", skipped ${summary.skipped} duplicates or invalid rows." else "."
                            }
                        )
                        onDismiss()
                    }
                },
                enabled = canImport,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(radius_md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryDefault,
                    contentColor = colors.textReverse
                )
            ) {
                Text(if (busy) "Importing…" else "Import ${previewRows.size} transactions")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = colors.textLight)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MappingRow(
    field: SmartField,
    selected: Int,
    headers: List<String>,
    onSelect: (Int) -> Unit
) {
    val colors = LocalReserveColors.current
    var expanded by remember { mutableStateOf(false) }
    val label = if (selected in headers.indices) headers[selected] else "Not used"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(field.label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(radius_md),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primaryDefault,
                unfocusedBorderColor = colors.strokeFaint,
                focusedTextColor = colors.textBase,
                unfocusedTextColor = colors.textBase,
                focusedLabelColor = colors.textLight,
                unfocusedLabelColor = colors.textLighter
            )
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Not used") },
                onClick = { onSelect(-1); expanded = false }
            )
            headers.forEachIndexed { index, header ->
                DropdownMenuItem(
                    text = { Text(header) },
                    onClick = { onSelect(index); expanded = false }
                )
            }
        }
    }
}
