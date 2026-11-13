package com.example.projeto_movimenta

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.projeto_movimenta.ui.theme.Layout

@Composable
internal fun ProductField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    error: String? = null,
    enabled: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    transformation: VisualTransformation = VisualTransformation.None,
    @DrawableRes icon: Int? = null,
) {
    OutlinedTextField(
        value, onChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) },
        singleLine = true, enabled = enabled, isError = error != null,
        supportingText = error?.let { { Text(it) } },
        leadingIcon = icon?.let { { AppIcon(it) } },
        shape = MaterialTheme.shapes.small,
        colors = fieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = transformation,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> OptionPicker(
    label: String,
    value: String,
    options: List<Pair<T, String>>,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectable = enabled && options.isNotEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded && selectable,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value, {}, modifier = Modifier.fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = selectable),
            readOnly = true, singleLine = true, enabled = selectable,
            label = { Text(label) },
            leadingIcon = icon?.let { { AppIcon(it) } },
            shape = MaterialTheme.shapes.small,
            colors = fieldColors(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && selectable) },
        )
        ExposedDropdownMenu(expanded && selectable, { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    expanded = false
                    onSelect(id)
                })
            }
        }
    }
}

@Composable
internal fun MeasurePicker(boxes: Boolean, enabled: Boolean, onSelect: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 320.dp).fillMaxWidth()) {
        listOf("Unidades", "Caixas").forEachIndexed { index, label ->
            val isBox = index == 1
            SegmentedButton(
                selected = boxes == isBox,
                onClick = { onSelect(isBox) },
                shape = SegmentedButtonDefaults.itemShape(index, 2, MaterialTheme.shapes.small),
                enabled = enabled,
                modifier = Modifier.heightIn(min = Layout.target),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                ),
            ) { Text(label, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
internal fun DateField(label: String, digits: String, error: String?, enabled: Boolean, onChange: (String) -> Unit) {
    ProductField(label, digits, { onChange(DateInput.digits(it)) }, error, enabled, KeyboardType.Number, DateTransformation, R.drawable.ic_mv_calendar)
}

@Composable
private fun fieldColors(): TextFieldColors {
    val theme = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = theme.surface, unfocusedContainerColor = theme.surface,
        disabledContainerColor = theme.surfaceVariant, errorContainerColor = theme.surface,
        focusedLeadingIconColor = theme.primary, unfocusedLeadingIconColor = theme.primary,
        errorLeadingIconColor = theme.error,
    )
}

private object DateTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val length = text.length
        return TransformedText(AnnotatedString(DateInput.formatted(text.text)), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = DateInput.originalToTransformed(offset, length)
            override fun transformedToOriginal(offset: Int) = DateInput.transformedToOriginal(offset, length)
        })
    }
}
