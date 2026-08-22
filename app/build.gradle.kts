import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    id("com.google.gms.google-services")
    // Tracer — сбор вылетов, зависаний и утечек памяти. Бесплатный, живёт в
    // кабинете RuStore. Плагин нужен ради выгрузки файлов сопоставления: без
    // них стек вылета в релизной сборке остаётся набором вида «y5.y.invokeSuspend».
    id("ru.ok.tracer") version "1.4.0"
}

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) localPropsFile.inputStream().use { localProps.load(it) }

/**
 * Ключ обязан быть в local.properties. Раньше здесь стояли захардкоженные
 * фолбэки — из-за них реальные ключи попали в историю git и в каждый APK.
 * Список нужных ключей — в README, раздел «Настройка local.properties».
 */
fun requiredProp(key: String): String = localProps.getProperty(key)?.takeIf { it.isNotBlank() }
    ?: throw GradleException("В local.properties отсутствует '$key' — см. README, раздел «Настройка local.properties»")

android {
    namespace = "com.aggin.carcost"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aggin.carcost"
        minSdk = 26
        targetSdk = 35
        versionCode = 106
        versionName = "5.3.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        val yandexMapKitKey = requiredProp("yandex.mapkit_key")

        buildConfigField("String", "SUPABASE_URL", "\"${requiredProp("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${requiredProp("supabase.anon_key")}\"")
        buildConfigField("String", "YANDEX_MAPKIT_KEY", "\"$yandexMapKitKey\"")
        manifestPlaceholders["yandexMapKitKey"] = yandexMapKitKey

        // VK ID: client_id публичен, client_secret тоже физически лежит в APK —
        // этого требует сам VK ID SDK. Единственная реальная защита — серверная
        // проверка токена в Edge Function vk-auth.
        // Значения по умолчанию позволяют собрать приложение до регистрации на dev.vk.com:
        // всё работает, кроме самого входа через VK.
        val vkClientId = localProps.getProperty("vk.client_id")?.takeIf { it.isNotBlank() } ?: "0"
        val vkClientSecret = localProps.getProperty("vk.client_secret") ?: ""

        // RuStore Push: без идентификатора проекта приложение собирается и
        // работает, просто уведомления идут только через Firebase.
        val rustoreProjectId = localProps.getProperty("rustore.push_project_id")
            ?.takeIf { it.isNotBlank() } ?: ""
        buildConfigField("String", "RUSTORE_PUSH_PROJECT_ID", "\"$rustoreProjectId\"")

        // MyTracker: ключ выдаётся в кабинете при добавлении приложения.
        // Пусто — аналитика просто не включается, сборка не ломается.
        val myTrackerKey = localProps.getProperty("mytracker.sdk_key")
            ?.takeIf { it.isNotBlank() } ?: ""
        buildConfigField("String", "MYTRACKER_SDK_KEY", "\"$myTrackerKey\"")

        buildConfigField("String", "VK_CLIENT_ID", "\"$vkClientId\"")
        manifestPlaceholders["VKIDClientID"] = vkClientId
        manifestPlaceholders["VKIDClientSecret"] = vkClientSecret
        manifestPlaceholders["VKIDRedirectHost"] = "vk.ru"
        manifestPlaceholders["VKIDRedirectScheme"] = "vk$vkClientId"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
        // Требование Tracer: через ресурсы-значения он передаёт в приложение
        // ключ приложения, проставленный на этапе сборки
        resValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

/**
 * Ключи Tracer.
 *
 * Оба берутся из кабинета Tracer и, как и остальные ключи проекта, лежат в
 * local.properties — файле, которого нет в git. Захардкоженные ключи однажды
 * уже утекли в историю репозитория, повторять это не будем.
 *
 * Пустые значения допустимы: без ключей приложение собирается и работает,
 * просто отчёты никуда не уходят. Это важно, чтобы проект собирался у любого,
 * кто не заводил себе Tracer.
 */
tracer {
    create("defaultConfig") {
        pluginToken = localProps.getProperty("tracer.plugin_token").orEmpty()
        appToken = localProps.getProperty("tracer.app_token").orEmpty()

        // Без этого плагин не создаёт задачу выгрузки вовсе, и стеки вылетов в
        // отчётах остаются набором вида «r0.n.a» — место в коде по ним не найти.
        // Проверяется просто: ./gradlew :app:tasks --all | grep -i upload
        uploadMapping = true

        // Символы нативных библиотек. Вылет навигатора приходил именно из
        // нативной части Яндекс-карт, и без них такой стек тоже нечитаем.
        uploadNativeSymbols = true
    }
}

dependencies {
    // Tracer: вылеты в Kotlin и в нативных библиотеках. Второе здесь не
    // формальность — сегодняшний вылет навигатора пришёл именно из нативной
    // части Яндекс-карт, и без этого модуля он бы не попал в отчёт.
    implementation(platform("ru.ok.tracer:tracer-platform:1.4.0"))
    implementation("ru.ok.tracer:tracer-crash-report")
    implementation("ru.ok.tracer:tracer-crash-report-native")
    // Модуль утечек памяти (tracer-heap-dumps) подключать НЕ надо — решение
    // осознанное, а не забытое. Снимок кучи выгружает наружу всё, что было в
    // памяти приложения: переписку, содержимое файлов из чата, данные машин.
    // Ради поиска вылетов такая цена неоправданна. Отчёт о сбое несёт только
    // модель устройства, версии и место в коде — этого достаточно.

    // MyTracker — продуктовая аналитика: запуски, экраны, удержание.
    // Версия задана диапазоном 3.3.+ — так в документации; внутри одной минорной
    // ветки обновления совместимы. Если понадобится воспроизводимая сборка,
    // зафиксируйте точную версию из отчёта ./gradlew :app:dependencies.
    implementation("com.my.tracker:mytracker-sdk:3.3.+")

    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")
    // Системный splash: без него на холодном старте видно пустое белое окно,
    // пока поднимается процесс — до того, как приложение вообще получит управление
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // === SUPABASE DEPENDENCIES ===
    implementation(platform("io.github.jan-tennert.supabase:bom:2.6.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:gotrue-kt")

    // Ktor client для Supabase (OkHttp поддерживает WebSocket для Realtime)
    implementation("io.ktor:ktor-client-okhttp:2.3.12")
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-utils:2.3.12")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    // === END SUPABASE ===

    // Firebase Cloud Messaging — мгновенные push даже когда приложение закрыто
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // RuStore Push — не замена Firebase, а дополнение для устройств без сервисов
    // Google. Работает только если на телефоне установлено приложение RuStore и
    // ему разрешена работа в фоне; на остальных устройствах доставку по-прежнему
    // обеспечивает Firebase. Оба транспорта живут одновременно, а какой из них
    // использовать для конкретного устройства — решает сервер по колонке provider
    // в user_push_tokens.
    //
    // 7.3.0 — последняя стабильная: у 7.4.0 и 8.0.0 в репозитории только
    // релиз-кандидаты. Репозиторий тот же, что у VK ID, отдельно добавлять не надо.
    implementation("ru.rustore.sdk:pushclient:7.3.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Yandex MapKit
    implementation("com.yandex.android:maps.mobile:4.33.1-full")

    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Room Database (можно оставить для офлайн кэша)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Coil
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Charts
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("com.patrykandpatrick.vico:compose:1.13.1")
    implementation("com.patrykandpatrick.vico:compose-m3:1.13.1")
    implementation("com.patrykandpatrick.vico:core:1.13.1")

    // Drag-and-drop reorderable list
    implementation("sh.calvin.reorderable:reorderable:2.4.0")

    // Shimmer loading effect
    implementation("com.valentinilk.shimmer:compose-shimmer:1.3.0")

    // Accompanist
    implementation("com.google.accompanist:accompanist-permissions:0.33.2-alpha")

    // VK ID SDK — вход через ВКонтакте (репозиторий VK объявлен в settings.gradle.kts)
    implementation("com.vk.id:vkid:2.7.1")


    // PDF Generation - iText
    implementation("com.itextpdf:itext7-core:7.2.5")
    implementation("com.itextpdf:io:7.2.5")

    // CameraX
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.camera:camera-video:$cameraxVersion")

    // Media3 (ExoPlayer) — для воспроизведения видеокружков в чате
    val media3Version = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")

    // ML Kit Text Recognition (OCR)
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // OkHttp — Edge Function vk-auth (VkAuthApi) и загрузка модели Gemma.
    // Retrofit/Gson убраны вместе с VIN-декодером: других потребителей не было.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // MediaPipe GenAI — on-device LLM inference (Gemma 2 1B)
    implementation("com.google.mediapipe:tasks-genai:0.10.22")

    // Lifecycle Service (for GPS ForegroundService)
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    // Shimmer (skeleton loading placeholders)
    implementation("com.valentinilk.shimmer:compose-shimmer:1.3.0")

    // Glance (Compose App Widget)
    implementation("androidx.glance:glance-appwidget:1.0.0")
    implementation("androidx.glance:glance-material3:1.0.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.01.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}