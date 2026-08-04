package com.aggin.carcost.presentation.screens.legal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

/**
 * Политика конфиденциальности — внутри приложения, а не по внешней ссылке.
 *
 * Изначально документ разместили на telegra.ph, но в России сервис недоступен
 * без обхода блокировок: то есть ровно та аудитория, которой политика адресована,
 * открыть её не могла. Требование RuStore при этом допускает размещение сведений
 * внутри приложения — этот вариант и надёжнее, и не зависит ни от какого
 * стороннего сервиса, и работает без сети.
 *
 * Текст продублирован в PRIVACY.md в корне проекта — оттуда его удобно взять
 * для описания карточки в магазине. При правках менять надо оба места.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Политика конфиденциальности") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Дата последнего обновления: 4 августа 2026 года",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Приложение CarCost помогает вести учёт расходов на автомобиль. " +
                    "Здесь описано, какие данные приложение собирает, зачем и что вы " +
                    "можете с ними сделать.",
                style = MaterialTheme.typography.bodyMedium
            )

            PRIVACY_SECTIONS.forEach { (heading, body) ->
                Spacer(Modifier.height(24.dp))
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(text = body, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Разделы документа.
 *
 * Вынесены списком, а не свёрстаны каждый отдельно: так правка текста не
 * превращается в правку разметки, и все разделы гарантированно выглядят
 * одинаково.
 */
private val PRIVACY_SECTIONS: List<Pair<String, String>> = listOf(
    "Кто обрабатывает данные" to
        "Разработчик приложения CarCost — Самохов Дмитрий Александрович.\n" +
        "Связь по вопросам обработки данных: mail.carcost@gmail.com",

    "Учётная запись" to
        "Адрес электронной почты, имя и фотография профиля, если вы их указали. " +
        "При входе через ВКонтакте — идентификатор профиля ВКонтакте, имя, фамилия, " +
        "фотография и адрес почты, если он открыт в вашем профиле.",

    "Данные об автомобилях" to
        "Марка, модель, год выпуска, государственный регистрационный номер, VIN, " +
        "цвет, пробег, дата и цена покупки, фотография автомобиля.",

    "Расходы" to
        "Суммы, даты, категории, описания, названия автозаправок и автосервисов, " +
        "объём топлива, показания одометра, фотографии чеков.",

    "Местоположение" to
        "Координаты сохраняются вместе с записью о расходе, если вы дали приложению " +
        "доступ к геолокации, — чтобы показывать траты на карте. Отдельно, при явном " +
        "запуске функции записи поездки, сохраняется маршрут движения.\n\n" +
        "Приложение не отслеживает ваше местоположение в фоне без вашего участия.",

    "Совместное использование" to
        "Если вы пригласили других людей в свой автомобиль, они видят его данные и " +
        "расходы, а вы — их записи. Сообщения и файлы во внутреннем чате автомобиля " +
        "доступны всем его участникам.",

    "Документы" to
        "Данные страховых полисов, документов на автомобиль, сведения о происшествиях " +
        "и приложенные к ним фотографии — всё, что вы внесли сами.",

    "Технические данные" to
        "Идентификатор устройства для доставки уведомлений.",

    "Отчёты об ошибках" to
        "Отправляются только по вашему явному действию через раздел «Сообщить об " +
        "ошибке». В отчёт входят: ваше описание проблемы, модель устройства, версия " +
        "Android, ваш адрес почты для обратной связи и журнал работы приложения за " +
        "последние два часа. Журнал может содержать технические сведения о ваших " +
        "действиях в приложении.",

    "Зачем это нужно" to
        "Данные обрабатываются исключительно для работы самого приложения: хранения " +
        "и показа ваших записей, синхронизации между устройствами, совместного " +
        "доступа к автомобилю, отправки напоминаний о плановом обслуживании и " +
        "истекающих документах, а также для разбора ошибок, о которых вы сообщили.\n\n" +
        "Данные не используются для рекламы, не передаются третьим лицам для " +
        "маркетинга и не продаются.",

    "Кому передаются данные" to
        "Supabase — хранение базы данных, файлов и учётных записей.\n\n" +
        "Google Firebase Cloud Messaging — доставка push-уведомлений.\n\n" +
        "VK ID — вход через ВКонтакте, если вы им пользуетесь.\n\n" +
        "Яндекс Карты — отображение карт и построение маршрутов.\n\n" +
        "Google ML Kit — распознавание текста на фотографии чека. Распознавание " +
        "выполняется на самом устройстве, фотография чека для этого никуда не " +
        "отправляется.",

    "Где и сколько хранятся" to
        "Серверы находятся в Европейском союзе (Стокгольм, Швеция). Данные хранятся, " +
        "пока существует ваша учётная запись. После её удаления стираются " +
        "безвозвратно, включая фотографии и файлы.",

    "Ваши права" to
        "Удалить учётную запись: Профиль → Мои данные → Удалить аккаунт. Удаляются все " +
        "ваши автомобили и связанные с ними записи, фотографии и файлы. Отменить " +
        "удаление нельзя.\n\n" +
        "Забрать данные: Профиль → Мои данные → Резервная копия. Приложение соберёт " +
        "файл со всеми вашими автомобилями и записями.\n\n" +
        "Отозвать доступы: разрешения на геолокацию, камеру и уведомления отзываются " +
        "в настройках Android в любой момент. Приложение продолжит работать, перестанут " +
        "действовать только зависящие от них функции.\n\n" +
        "Отвязать ВКонтакте: Профиль → Способы входа, если у вас есть другой способ " +
        "входа в учётную запись.",

    "Дети" to
        "Приложение не предназначено для лиц младше 14 лет и не собирает их данные " +
        "осознанно.",

    "Изменения" to
        "При изменении этого документа дата в начале страницы будет обновлена.",

    "Обратная связь" to
        "По любым вопросам об обработке данных, включая запрос на удаление: " +
        "mail.carcost@gmail.com"
)
