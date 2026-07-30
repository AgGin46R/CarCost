<div align="center">

<img src="image_main.png" alt="CarCost Banner" width="100%"/>

# 🚗 CarCost

**Умный трекер расходов на автомобиль**

[![Version](https://img.shields.io/badge/version-3.0.0-blue.svg)](https://github.com/AgGin_46R/CarCost/releases)
[![Min SDK](https://img.shields.io/badge/minSDK-26-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.12-4285F4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

[Возможности](#-возможности) · [Стек](#-технологии) · [Сборка](#-сборка) · [Архитектура](#-архитектура)

</div>

---

## 📋 О проекте

**CarCost** — полнофункциональное Android-приложение для учёта и анализа всех расходов на автомобиль. Заправки, ТО, страховка, штрафы, запчасти — всё в одном месте с синхронизацией между устройствами, чатом с совладельцами и умными напоминаниями.

> Целевая аудитория: частные автовладельцы и семьи с несколькими автомобилями.

---

## ✨ Возможности

### 💰 Учёт расходов
- Добавление расходов по 11 категориям (топливо, ТО, ремонт, страховка, штрафы и др.)
- OCR-сканирование чеков с автозаполнением суммы и категории
- Геотеги — привязка расхода к местоположению на карте
- Планируемые расходы с отслеживанием статуса
- Быстрый ввод: суммы-подсказки и автоопределение категории по описанию

### 📊 Аналитика
- Детальная аналитика по категориям, месяцам и автомобилям
- Прогноз расходов на следующий месяц
- Сравнение нескольких автомобилей
- Бюджеты по категориям с уведомлениями о превышении
- Калькулятор TCO (Total Cost of Ownership) с графиком амортизации
- Экспорт отчётов в PDF и CSV

### 🔧 Обслуживание
- Дашборд ТО с расчётом км до следующей замены
- Умные напоминания по пробегу и дате (масло, фильтры, колодки и др.)
- История сервисного обслуживания с таймлайном
- Хранилище документов (ОСАГО, СТС, паспорт авто)

### 🗺️ Карта и GPS
- Карта расходов с фильтрацией по категориям (Яндекс.Карты)
- Запись GPS-маршрутов с трекингом пробега
- Визуализация поездки на карте

### 🔑 Вход
- Email + пароль
- Вход через Google (Credential Manager)
- Вход через ВКонтакте (VK ID)

### 👥 Совместное использование
- Добавление совладельцев по email-приглашению
- Чат участников с фото, голосовыми сообщениями и реакциями
- Синхронизация расходов в реальном времени

### 🔔 Уведомления
- Push-уведомления о приближении ТО
- Напоминания об истечении страховки и документов
- Еженедельный дайджест расходов
- Предупреждение о выходе за рамки бюджета
- Напоминание о заправке по остатку топлива

### 🎨 Кастомизация
- 5 цветовых акцент-схем (синий, зелёный, фиолетовый, оранжевый, бирюзовый)
- Тёмная / светлая / системная тема
- Домашний виджет с расходами за месяц и ближайшим ТО

---

## 🛠 Технологии

| Слой | Библиотека |
|------|-----------|
| **UI** | Jetpack Compose + Material3 |
| **Навигация** | Navigation Compose |
| **Состояние** | ViewModel + StateFlow |
| **Локальная БД** | Room 2.6 (33 миграции) |
| **Облако** | Supabase (PostgreSQL, Auth, Storage, Realtime) |
| **Push** | Firebase Cloud Messaging |
| **Карты** | Yandex MapKit 4.33 |
| **OCR** | ML Kit Text Recognition |
| **Камера** | CameraX 1.3 |
| **Сеть** | Ktor (OkHttp) + Retrofit |
| **Фоновые задачи** | WorkManager 2.10 |
| **Изображения** | Coil 2.7 |
| **Графики** | Vico 1.13 + MPAndroidChart |
| **Виджет** | Glance AppWidget |
| **Шиммер** | Compose Shimmer |

---

## ⚙️ Настройка local.properties

Ключи не хранятся в репозитории — сборка **упадёт с внятной ошибкой**, если какого-то из них нет.
Создайте `local.properties` в корне проекта (файл в `.gitignore`):

```properties
sdk.dir=C:\\путь\\к\\Android\\Sdk

supabase.url=https://<project-ref>.supabase.co
supabase.anon_key=<anon key из Supabase → Settings → API>
google.web_client_id=<Web client ID из Google Cloud Console>
yandex.mapkit_key=<ключ Yandex MapKit>

# VK ID — App ID и «Защищённый ключ» с dev.vk.com.
# Без них приложение собирается и работает, не работает только вход через VK.
vk.client_id=0
vk.client_secret=
```

**Настройка входа через ВКонтакте:**

1. `dev.vk.com` → создать приложение, платформа Android
2. Package name: `com.aggin.carcost`
3. SHA-256 отпечатки **обоих** keystore — debug и release
   (только debug → в release-сборке вход молча не работает):
   ```bash
   keytool -list -v -alias androiddebugkey -keystore "$USERPROFILE/.android/debug.keystore" -storepass android -keypass android
   ```
4. Доверенный Redirect URI: `vk<client_id>://vk.ru/blank.html` (домен **vk.ru**)
5. Разрешить scope `email`
6. Развернуть серверную часть — см. `supabase/vk_identities_setup.sql`

## 🏗 Архитектура

```
app/
├── data/
│   ├── local/
│   │   ├── database/         # Room entities, DAOs, migrations
│   │   └── settings/         # DataStore preferences
│   ├── remote/
│   │   └── repository/       # Supabase repositories
│   └── notifications/        # WorkManager workers
├── domain/                   # Бизнес-логика (калькуляторы, классификаторы)
└── presentation/
    ├── components/           # Переиспользуемые Composable
    ├── navigation/           # NavGraph + Screen sealed class
    ├── screens/              # 35+ экранов (screen/ + viewmodel)
    ├── widget/               # Glance AppWidget
    └── ui/theme/             # Material3 темы, AccentScheme
```

**Паттерн:** MVVM + Repository  
**UI:** Unidirectional Data Flow (UiState → Composable → Event → ViewModel)  
**БД:** Room с 33 миграциями, версия 33  
**Синхронизация:** Supabase Realtime + BackgroundSyncWorker

---

## 📱 Скриншоты

<div align="center">

| Главный экран | Аналитика | Дашборд ТО |
|:---:|:---:|:---:|
| *coming soon* | *coming soon* | *coming soon* |

| Карта расходов | Чат | Добавить расход |
|:---:|:---:|:---:|
| *coming soon* | *coming soon* | *coming soon* |

</div>

---

## 🗺 Дорожная карта

- [x] Расход топлива L/100 км с графиком тренда
- [x] Напоминание о ТО по дате
- [ ] Сезонная замена шин с историей
- [ ] Калькулятор транспортного налога
- [ ] Проверка штрафов ГИБДД
- [ ] Повторяющиеся расходы (страховка, абонементы)
- [ ] Годовой отчёт (Year in Review)

---

## 🤝 Contributing

Pull requests приветствуются. Для крупных изменений сначала откройте issue для обсуждения.

1. Fork репозитория
2. Создать ветку (`git checkout -b feature/amazing-feature`)
3. Commit изменений (`git commit -m 'Add amazing feature'`)
4. Push в ветку (`git push origin feature/amazing-feature`)
5. Открыть Pull Request

---

## 📄 Лицензия

Распространяется под лицензией MIT. Подробнее см. [`LICENSE`](LICENSE).

---

<div align="center">

Сделано с ❤️ для автолюбителей России

</div>
