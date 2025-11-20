package com.aggin.carcost.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aggin.carcost.data.local.database.entities.ExpenseTag

@Composable
fun TagSelector(
    availableTags: List<ExpenseTag>,
    selectedTags: List<ExpenseTag>,
    onTagSelected: (ExpenseTag) -> Unit,
    onTagRemoved: (ExpenseTag) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showTagPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Теги",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            if (availableTags.isNotEmpty() && enabled) {
                TextButton(onClick = { showTagPicker = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Добавить")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedTags.isEmpty()) {
            Text(
                text = "Теги не выбраны",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(selectedTags, key = { it.id }) { tag ->
                    TagChip(
                        tag = tag,
                        onRemove = { if (enabled) onTagRemoved(tag) },
                        enabled = enabled
                    )
                }
            }
        }
    }

    if (showTagPicker) {
        TagPickerDialog(
            availableTags = availableTags,
            selectedTags = selectedTags,
            onTagSelected = onTagSelected,
            onDismiss = { showTagPicker = false }
        )
    }
}

@Composable
fun TagChip(
    tag: ExpenseTag,
    onRemove: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(android.graphics.Color.parseColor(tag.color)).copy(alpha = 0.2f),
        modifier = Modifier.border(
            width = 1.dp,
            color = Color(android.graphics.Color.parseColor(tag.color)),
            shape = RoundedCornerShape(16.dp)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(android.graphics.Color.parseColor(tag.color)))
            )
            Text(
                text = tag.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (enabled) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Удалить",
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = onRemove),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun TagPickerDialog(
    availableTags: List<ExpenseTag>,
    selectedTags: List<ExpenseTag>,
    onTagSelected: (ExpenseTag) -> Unit,
    onDismiss: () -> Unit
) {
    val unselectedTags = availableTags.filter { tag ->
        selectedTags.none { it.id == tag.id }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите тег") },
        text = {
            if (unselectedTags.isEmpty()) {
                Text(
                    "Все теги уже добавлены",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    unselectedTags.forEach { tag ->
                        SelectableTagItem(
                            tag = tag,
                            onClick = {
                                onTagSelected(tag)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

@Composable
fun SelectableTagItem(
    tag: ExpenseTag,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(android.graphics.Color.parseColor(tag.color)))
            )
            Text(
                text = tag.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
        }
    }
}