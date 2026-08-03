package com.aggin.carcost.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * Ввод кода приглашения.
 *
 * Лежит в общих компонентах, потому что вызывается из двух мест: из «Участников»
 * (там же, где владелец берёт свой код) и из профиля. Профиль — обязательная
 * точка входа: присоединяются к ЧУЖОЙ машине, а экран «Участники» открывается
 * только изнутри машины, в которой уже состоишь. Без профиля человек без
 * автомобилей не смог бы принять приглашение вовсе.
 *
 * Сам приём делает экран AcceptInvite — он уже разбирает ответы сервера
 * (не найдено / использовано / истекло), поэтому здесь только собираем код.
 */
@Composable
fun JoinByCodeDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }
    // Сервер принимает любой регистр и разделители, но подсказываем привычный вид
    val cleaned = code.filter { it.isLetterOrDigit() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
        title = { Text("Присоединиться по коду") },
        text = {
            Column {
                Text("Введите код, который прислал владелец автомобиля.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 12) code = it.uppercase() },
                    label = { Text("Код приглашения") },
                    placeholder = { Text("K7M2-P9XQ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = cleaned.length >= 6,
                onClick = { onSubmit(cleaned) }
            ) { Text("Присоединиться") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
