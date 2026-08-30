package com.aggin.carcost.presentation.components

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Разрешено ли приложению показывать уведомления.
 *
 * Проверяется именно системный флаг, а не наличие разрешения: пользователь может
 * отключить уведомления в настройках Android и без отзыва permission, и тогда
 * ни одно напоминание не придёт.
 */
fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

/** Открывает системные настройки уведомлений приложения */
fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            // Если экрана настроек уведомлений нет — открываем страницу приложения
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }
}

/**
 * Предупреждение о выключенных уведомлениях с кнопкой включения.
 *
 * Почему это понадобилось. Разрешение запрашивалось РОВНО ОДИН РАЗ за всю жизнь
 * установки — на последнем шаге онбординга. Кто тогда нажал «Запретить» (а на
 * незнакомом экране это обычная реакция), не спрашивался больше никогда. При
 * этом переключатели в профиле продолжали включаться и выключаться как ни в чём
 * не бывало, создавая полную уверенность, что напоминания о ТО работают. Они не
 * приходили, и понять почему было невозможно.
 *
 * Правильный момент для запроса — не старт приложения, а тот, когда человек
 * сам говорит «хочу напоминания»: включает переключатель или видит это
 * предупреждение.
 *
 * Состояние перечитывается при возврате на экран: разрешение могли выдать в
 * системных настройках, и предупреждение обязано исчезнуть само.
 */
@Composable
fun NotificationsDisabledWarning(
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.components_uvedomleniya_vyklyucheny_v_nastroykah) +
        stringResource(R.string.components_napominaniya_o_to_dokumentah_i_strahovke)
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(notificationsEnabled(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = notificationsEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // На Android 13+ можно попросить прямо в приложении. Если система откажет
    // показывать диалог (пользователь отказывал дважды), остаётся отправить
    // в настройки — молчать в этом случае нельзя, иначе кнопка «ничего не делает»
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        enabled = granted || notificationsEnabled(context)
        if (!enabled) openNotificationSettings(context)
    }

    if (enabled) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.NotificationsOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            TextButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        // До Android 13 разрешения нет — уведомления выключены
                        // вручную, и включить их можно только в настройках
                        openNotificationSettings(context)
                    }
                }
            ) { Text(stringResource(R.string.components_vklyuchit)) }
        }
    }
}
