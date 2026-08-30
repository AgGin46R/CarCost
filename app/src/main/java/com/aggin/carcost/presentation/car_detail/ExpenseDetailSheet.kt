package com.aggin.carcost.presentation.screens.car_detail

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
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

            DetailRow(stringResource(R.string.cardetail_data), formatDateLong(expense.date))
            DetailRow(stringResource(R.string.home_probeg), stringResource(R.string.home_km, expense.odometer))

            expense.fuelLiters?.let { DetailRow(stringResource(R.string.cardetail_zapravleno), stringResource(R.string.cardetail_2f_l).format(it)) }
            expense.energyKwh?.let { DetailRow(stringResource(R.string.cardetail_zaryazheno), stringResource(R.string.cardetail_1f_kvt_ch).format(it)) }
            expense.fuelType?.takeIf { it.isNotBlank() }?.let { DetailRow(stringResource(R.string.cardetail_marka_topliva), it) }
            if (expense.isFullTank) DetailRow(stringResource(R.string.cardetail_zapravka), stringResource(R.string.cardetail_do_polnogo))
            fuelConsumptionL100km?.let { DetailRow(stringResource(R.string.cardetail_rashod), stringResource(R.string.cardetail_2f_l_100_km).format(it)) }

            expense.serviceType?.let { DetailRow(stringResource(R.string.cardetail_rabota), it.displayName()) }
            expense.workshopName?.takeIf { it.isNotBlank() }?.let { DetailRow(stringResource(R.string.cardetail_servis), it) }
            expense.maintenanceParts?.takeIf { it.isNotBlank() }?.let { DetailRow(stringResource(R.string.cardetail_zapchasti), it) }
            expense.location?.takeIf { it.isNotBlank() }?.let { DetailRow(stringResource(R.string.cardetail_mesto), it) }
            expense.description?.takeIf { it.isNotBlank() }?.let { DetailRow(stringResource(R.string.cardetail_opisanie), it) }

            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.cardetail_metki),
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
                        text = stringResource(R.string.cardetail_chek),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = uri,
                    contentDescription = stringResource(R.string.cardetail_chek),
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
                    text = stringResource(R.string.cardetail_nazhmite_chtoby_otkryt_i_priblizit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_edit))
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
