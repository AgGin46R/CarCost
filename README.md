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

## 🚀 Сборка

### Требования
- Android Studio Ladybug или новее
- JDK 17
- Android SDK 35

### Шаги

```bash
# 1. Клонировать репозиторий
git clone https://github.com/your-username/CarCost.git
cd CarCost

# 2. Создать local.properties с ключами
cat >> local.properties << EOF
supabase.url=https://your-project.supabase.co
supabase.anon_key=your-anon-key
google.web_client_id=your-google-client-id
yandex.maps.api_key=your-yandex-maps-key
EOF

# 3. Добавить google-services.json для Firebase
# Положить файл в app/google-services.json

# 4. Собрать debug-версию
./gradlew assembleDebug
```

### Переменные окружения

| Ключ | Где взять |
|------|-----------|
| `supabase.url` | [supabase.com](https://supabase.com) → Settings → API |
| `supabase.anon_key` | [supabase.com](https://supabase.com) → Settings → API |
| `google.web_client_id` | Google Cloud Console → OAuth 2.0 |
| `yandex.maps.api_key` | [developer.tech.yandex.ru](https://developer.tech.yandex.ru) |

> **Примечание:** без ключей приложение соберётся с дефолтными значениями из `build.gradle.kts`, но синхронизация и карты работать не будут.

---

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

- [ ] Расход топлива L/100 км с графиком тренда
- [ ] Напоминание о техосмотре по дате
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
