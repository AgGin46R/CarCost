package com.aggin.carcost.presentation.screens.car_detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseTag
import com.aggin.carcost.presentation.common.displayName
import com.aggin.carcost.presentation.common.emoji
import com.aggin.carcost.presentation.common.formatDateLong
import com.aggin.carcost.presentation.screens.chat.ZoomableImage
import com.aggin.carcost.util.CurrencyUtils

/**
 * Карточка расхода.
 *
 * Появилась из-за прикреплённых чеков: сфотографировать чек приложение
 * позволяло давно, а посмотреть его потом было нельзя нигде — ни в списке, ни
 * при редактировании. Снимок уходил в хранилище и исчезал из виду навсегда.
 *
 * Заодно решает вторую задачу: нажатие на расход вело сразу в форму правки, и
 * чтобы просто посмотреть подробности, приходилось открывать редактирование и
 * выходить из него, рискуя случайно что-нибудь изменить.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailSheet(
    expense: Expense,
    tags: List<ExpenseTag>,
    currency: String,
    fuelConsumptionL100km: Double?,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    var fullscreenReceipt by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(expense.category.emoji(), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = CurrencyUtils.format(expense.amount, currency),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = expense.category.displayName(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            DetailRow("Дата", formatDateLong(expense.date))
            DetailRow("Пробег", "${expense.odometer} км")

            expense.fuelLiters?.let { DetailRow("Заправлено", "%.2f л".format(it)) }
            expense.energyKwh?.let { DetailRow("Заряжено", "%.1f кВт·ч".format(it)) }
            expense.fuelType?.takeIf { it.isNotBlank() }?.let { DetailRow("Марка топлива", it) }
            if (expense.isFullTank) DetailRow("Заправка", "до полного")
            fuelConsumptionL100km?.let { DetailRow("Расход", "%.2f л/100 км".format(it)) }

            expense.serviceType?.let { DetailRow("Работа", it.displayName()) }
            expense.workshopName?.takeIf { it.isNotBlank() }?.let { DetailRow("Сервис", it) }
            expense.maintenanceParts?.takeIf { it.isNotBlank() }?.let { DetailRow("Запчасти", it) }
            expense.location?.takeIf { it.isNotBlank() }?.let { DetailRow("Место", it) }
            expense.description?.takeIf { it.isNotBlank() }?.let { DetailRow("Описание", it) }

            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Метки",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { tag ->
                        AssistChip(onClick = {}, label = { Text(tag.name) }, enabled = false)
                    }
                }
            }

            // Прикреплённый чек — ради него карточка и появилась
            expense.receiptPhotoUri?.takeIf { it.isNotBlank() }?.let { uri ->
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Чек",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = uri,
                    contentDescription = "Чек",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { fullscreenReceipt = uri },
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Нажмите, чтобы открыть и приблизить",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Изменить")
            }
        }
    }

    fullscreenReceipt?.let { uri ->
        Dialog(
            onDismissRequest = { fullscreenReceipt = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            // Тот же просмотр, что и в чате: щипок, двойное касание, перетаскивание.
            // Мелкий шрифт чека иначе не прочитать
            ZoomableImage(url = uri, onDismiss = { fullscreenReceipt = null })
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
