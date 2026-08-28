package com.aggin.carcost.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp

/**
 * Поле с подсказками из справочника.
 *
 * Подсказки показываются рядом, пока поле в фокусе, и исчезают, как только
 * набранное совпало с одним из вариантов, — иначе строка чипов висит под
 * заполненным полем и мешает добраться до следующего.
 *
 * Ввод не ограничивается списком: чего нет в справочнике, дописывается руками.
 * Справочник у нас неполный по определению, и превращать его в жёсткий выбор
 * значит не дать человеку внести собственную машину.
 */
@Composable
fun SuggestField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }

    val visible = if (!focused || !enabled) {
        emptyList()
    } else {
        suggestions.filterNot { it.equals(value.trim(), ignoreCase = true) }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            singleLine = true,
            enabled = enabled
        )

        if (visible.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                visible.forEach { suggestion ->
                    AssistChip(
                        onClick = { onValueChange(suggestion) },
                        label = {
                            Text(suggestion, style = MaterialTheme.typography.labelMedium)
                        }
                    )
                }
            }
        }
    }
}
