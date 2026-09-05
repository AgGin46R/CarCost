package com.aggin.carcost.data.sync

import android.content.Context
import android.util.Log
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.notifications.ActiveChatTracker
import com.aggin.carcost.data.notifications.NotificationHelper
import com.aggin.carcost.data.remote.repository.CarDto
import com.aggin.carcost.data.remote.repository.ExpenseDto
import com.aggin.carcost.data.remote.repository.CarInvitationDto
import com.aggin.carcost.data.remote.repository.ChatMessageDto
import com.aggin.carcost.data.remote.repository.MaintenanceReminderDto
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarRepository
import com.aggin.carcost.data.remote.repository.toChatMessage
import com.aggin.carcost.data.remote.repository.toExpense
import com.aggin.carcost.data.remote.repository.toCar
import com.aggin.carcost.data.remote.repository.toMaintenanceReminder
import com.aggin.carcost.supabase
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.math.abs

class RealtimeSyncManager(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val TAG = "RealtimeSync"
    private val json = Json { ignoreUnknownKeys = true }
    private val carRepo = SupabaseCarRepository(SupabaseAuthRepository())

    // Перехватывает SocketException/IOException, которые бросает Supabase Realtime при обрыве
    // TCP-соединения (сеть упала, телефон ушёл в сон). Без этого хэндлера exception
    // попадает в SupervisorJob без обработчика → FATAL EXCEPTION → краш.
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        when (throwable) {
            is java.net.SocketException,
            is java.io.IOException,
            is java.net.ConnectException -> {
                Log.w(TAG, "Network error in Realtime scope (caught, no crash): ${throwable.message}")
            }
            else -> {
                Log.e(TAG, "Unexpected error in Realtime scope: ${throwable.message}", throwable)
            }
        }
        // Сбрасываем канал — reconnectIfNeeded() при следующем форегрануде переподключится
        activeChannel = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    private var activeChannel: RealtimeChannel? = null

    /**
     * Область жизни ОДНОГО подключения канала.
     *
     * Тринадцать сборщиков событий раньше запускались в общий `scope`, который
     * живёт всё время работы процесса, а их Job нигде не сохранялись.
     * `connectChannel()` вызывается при каждом выходе приложения на передний
     * план, на каждое обновление токена (примерно раз в час) и после любой
     * сетевой ошибки — и каждый раз добавлял ещё тринадцать. За день с плохой
     * связью их набирались сотни.
     *
     * Это не только память: пока старые каналы ещё отдают события, одна вставка
     * расхода обрабатывалась несколько раз — запись писалась в базу повторно, и
     * пользователь получал пачку одинаковых уведомлений о трате совладельца.
     *
     * Теперь каждое подключение получает свою дочернюю область, и отключение
     * канала гасит ровно его сборщиков.
     */
    private var channelScope: CoroutineScope? = null

    /**
     * Ensures a car with the given [carId] exists in local Room DB.
     * If not, fetches it from Supabase (via RLS — works for shared cars too) and inserts it.
     * This prevents FOREIGN KEY constraint failures when Realtime delivers a record
     * whose car hasn't been synced locally yet.
     */
    /** Returns true если машина есть или успешно загружена. False = вставку нужно пропустить. */
    private suspend fun ensureCarExists(carId: String): Boolean {
        if (db.carDao().getCarById(carId) != null) return true
        Log.d(TAG, "Car $carId not in local DB — fetching from Supabase")
        var success = false
        carRepo.fetchSharedCar(carId).onSuccess { car ->
            db.carDao().insertCar(car)
            Log.d(TAG, "✅ Car $carId fetched and cached locally")
            success = true
        }.onFailure {
            Log.w(TAG, "Could not fetch car $carId — skipping insert: ${it.message}")
        }
        return success
    }

    /**
     * Observes the Supabase auth state. (Re)starts the WebSocket channel whenever
     * the user is authenticated and the channel is not already SUBSCRIBED.
     */
    fun start() {
        scope.launch {
            supabase.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        // Канал пересоздаётся при КАЖДОМ обновлении сессии, а не только
                        // когда он отвалился.
                        //
                        // Realtime запоминает токен в момент подключения и проверяет по
                        // нему права на каждую строку. Токен живёт час; когда он
                        // истекает, сессия обновляется и сюда снова приходит
                        // Authenticated — но канал всё ещё SUBSCRIBED, и прежняя
                        // проверка `if (!channelOk)` пропускала переподключение. Сокет
                        // оставался со старым токеном.
                        //
                        // Дальше Realtime просто переставал слать события: соединение
                        // открыто, счётчики подключений растут, ошибок нет, а строки не
                        // приходят. Поймано на чате: свои сообщения видны (они пишутся
                        // локально), чужие появляются только после перезахода в чат,
                        // потому что экран подтягивает их отдельным запросом.
                        //
                        // sessionStatus — StateFlow, и Authenticated приходит сюда при
                        // входе и при обновлении токена, то есть примерно раз в час.
                        // Переподключение раз в час дешевле, чем молча потерянные
                        // сообщения; сверять токены вручную не стали, чтобы не зависеть
                        // от деталей API библиотеки.
                        Log.d(TAG, "Сессия обновлена — переподключаю Realtime (было: ${activeChannel?.status?.value})")
                        // Отключение делает сам safeConnect под блокировкой —
                        // здесь оно только открывало бы окно для гонки
                        safeConnect()
                    }
                    is SessionStatus.NotAuthenticated -> {
                        Log.d(TAG, "User signed out — stopping Realtime channel")
                        disconnectChannel()
                    }
                    else -> Unit
                }
            }
        }
    }

    /**
     * Call this when the app comes to foreground to ensure the Realtime channel
     * is active. Silently ignored if already SUBSCRIBED.
     */
    fun reconnectIfNeeded() {
        scope.launch {
            val session = supabase.auth.sessionStatus.value
            if (session !is SessionStatus.Authenticated) return@launch
            val channelOk = activeChannel?.status?.value == RealtimeChannel.Status.SUBSCRIBED
            if (!channelOk) {
                Log.d(TAG, "Foreground reconnect — channel was: ${activeChannel?.status?.value}")
                safeConnect()
            } else {
                Log.d(TAG, "Foreground check — channel already SUBSCRIBED")
            }
        }
    }

    /** Обёртка над connectChannel — ловит SocketException и другие сетевые ошибки,
     *  которые иначе крашат приложение через DefaultDispatcher-worker */
    /**
     * Подключения выполняются строго по одному.
     *
     * Триггеров два, и они срабатывают почти одновременно: start() — от появления
     * сессии, reconnectIfNeeded() — от выхода приложения на передний план. При
     * запуске приложения оба случались в пределах пяти миллисекунд, и каждый
     * создавал свой канал с одним и тем же именем carcost-sync. Оба
     * присоединялись, второй затирал привязки первого — и на сервере не
     * оставалось ни одной подписки на изменения таблиц.
     *
     * Снаружи это выглядело неотличимо от исправной работы: сокет жив,
     * сердцебиения идут, ошибок нет. А события не приходили вообще — ни в чат,
     * ни по расходам, ни по напоминаниям. Поймано по журналу устройства: две
     * строки «Realtime channel subscribed» подряд с разницей в миллисекунду.
     */
    private val connectMutex = Mutex()

    /** Когда в последний раз успешно начинали подключение — для защиты от дублей */
    @Volatile
    private var lastConnectAt = 0L

    private suspend fun safeConnect() = connectMutex.withLock {
        // Повторная проверка уже под блокировкой: пока мы ждали очереди,
        // конкурент мог успешно подключиться, и второй канал только всё сломает.
        // Проверка по статусу канала ненадёжна: subscribe() возвращает управление
        // сразу, а SUBSCRIBED выставляется позже, когда сервер подтвердит join.
        // Второй инициатор успевал заглянуть в промежуточный статус и подключиться
        // повторно — в журнале это выглядело как две строки «channel subscribed»
        // из одного потока в одну миллисекунду.
        //
        // Поэтому решает время: два запроса на подключение в пределах пары секунд
        // — это один и тот же запуск приложения (start() от появления сессии и
        // reconnectIfNeeded() от выхода на передний план срабатывают с разницей
        // в миллисекунды), а не два разных события.
        val sinceLast = System.currentTimeMillis() - lastConnectAt
        if (activeChannel != null && sinceLast < CONNECT_DEBOUNCE_MS) {
            Log.d(TAG, "Подключение уже выполнено ${sinceLast} мс назад — повторное не требуется")
            return@withLock
        }
        if (activeChannel?.status?.value == RealtimeChannel.Status.SUBSCRIBED) {
            Log.d(TAG, "Канал уже подписан — повторное подключение не требуется")
            return@withLock
        }
        lastConnectAt = System.currentTimeMillis()
        // Отключение — тоже под блокировкой, вместе с подключением.
        //
        // Раньше disconnectChannel() вызывался у обоих инициаторов ДО safeConnect,
        // то есть снаружи мьютекса. Второй успевал обнулить activeChannel, пока
        // первый ещё подключался, — проверка выше видела null и пропускала его
        // дальше. Каналов снова создавалось два, и подписки на сервере опять
        // затирались. Гонка не исчезала, а лишь смещалась на шаг.
        disconnectChannel()
        try {
            connectChannel()
        } catch (e: java.net.SocketException) {
            Log.w(TAG, "SocketException during Realtime connect — will retry on next auth event: ${e.message}")
            activeChannel = null
        } catch (e: Exception) {
            Log.w(TAG, "Realtime connect failed: ${e.message}")
            activeChannel = null
        }
    }

    fun stop() {
        scope.launch { disconnectChannel() }
    }

    // -------------------------------------------------------------------------

    private suspend fun connectChannel() {
        try {
            val ch = supabase.channel("carcost-sync")
            activeChannel = ch

            // Своя область на это подключение: при отключении гасятся ровно
            // её сборщики, а не копятся поверх старых
            val chScope = CoroutineScope(
                SupervisorJob(scope.coroutineContext[kotlinx.coroutines.Job]) +
                    Dispatchers.IO + exceptionHandler
            )
            channelScope = chScope

            // ── Expenses ─────────────────────────────────────────────────────

            ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "expenses"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(ExpenseDto.serializer(), change.record)
                    if (!ensureCarExists(dto.carId)) return@onEach
                    com.aggin.carcost.data.local.repository.ExpenseRepository(db.expenseDao())
                        .saveFromServer(dto.toExpense())
                    raiseOdometer(dto.carId)
                    Log.d(TAG, "📥 Expense inserted: ${dto.id}")
                    maybeNotifyExpense(dto, isUpdate = false)
                } catch (e: Exception) { Log.e(TAG, "Error handling expense insert", e) }
            }.catch { Log.w(TAG, "Expense insert flow error: ${it.message}") }.launchIn(chScope)

            ch.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "expenses"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(ExpenseDto.serializer(), change.record)
                    if (!ensureCarExists(dto.carId)) return@onEach
                    com.aggin.carcost.data.local.repository.ExpenseRepository(db.expenseDao())
                        .saveFromServer(dto.toExpense())
                    raiseOdometer(dto.carId)
                    Log.d(TAG, "✏️ Expense updated: ${dto.id}")
                    maybeNotifyExpense(dto, isUpdate = true)
                } catch (e: Exception) { Log.e(TAG, "Error handling expense update", e) }
            }.catch { Log.w(TAG, "Expense update flow error: ${it.message}") }.launchIn(chScope)

            ch.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
                table = "expenses"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(ExpenseDto.serializer(), change.oldRecord)
                    db.expenseDao().deleteExpenseById(dto.id)
                    Log.d(TAG, "🗑️ Expense deleted: ${dto.id}")
                } catch (e: Exception) { Log.e(TAG, "Error handling expense delete", e) }
            }.catch { Log.w(TAG, "Expense delete flow error: ${it.message}") }.launchIn(chScope)

            // ── Cars ──────────────────────────────────────────────────────────

            ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "cars"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(CarDto.serializer(), change.record)
                    db.carDao().upsertCar(dto.toCar())
                    Log.d(TAG, "🚗 Car synced: ${dto.id}")
                } catch (e: Exception) { Log.e(TAG, "Error handling car insert", e) }
            }.catch { Log.w(TAG, "Car insert flow error: ${it.message}") }.launchIn(chScope)

            ch.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "cars"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(CarDto.serializer(), change.record)
                    // Через репозиторий, а не напрямую в DAO: там пробег
                    // сливается по наибольшему, иначе прилетевшее с сервера
                    // старое значение опустит верное локальное
                    com.aggin.carcost.data.local.repository.CarRepository(db.carDao())
                        .saveFromServer(dto.toCar())
                    Log.d(TAG, "🚗 Car updated: ${dto.id}")
                } catch (e: Exception) { Log.e(TAG, "Error handling car update", e) }
            }.catch { Log.w(TAG, "Car update flow error: ${it.message}") }.launchIn(chScope)

            // ── Maintenance Reminders ─────────────────────────────────────────

            ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "maintenance_reminders"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(MaintenanceReminderDto.serializer(), change.record)
                    if (!ensureCarExists(dto.carId)) return@onEach
                    db.maintenanceReminderDao().insertReminder(dto.toMaintenanceReminder())
                    Log.d(TAG, "🔧 Reminder inserted: ${dto.id}")
                    maybeNotifyReminder(dto, isUpdate = false)
                } catch (e: Exception) { Log.e(TAG, "Error handling reminder insert", e) }
            }.catch { Log.w(TAG, "Reminder insert flow error: ${it.message}") }.launchIn(chScope)

            ch.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "maintenance_reminders"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(MaintenanceReminderDto.serializer(), change.record)
                    if (!ensureCarExists(dto.carId)) return@onEach
                    db.maintenanceReminderDao().insertReminder(dto.toMaintenanceReminder())
                    Log.d(TAG, "🔧 Reminder updated: ${dto.id}")
                    maybeNotifyReminder(dto, isUpdate = true)
                } catch (e: Exception) { Log.e(TAG, "Error handling reminder update", e) }
            }.catch { Log.w(TAG, "Reminder update flow error: ${it.message}") }.launchIn(chScope)

            // ── Chat Messages ─────────────────────────────────────────────────

            ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "chat_messages"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(ChatMessageDto.serializer(), change.record)
                    if (!ensureCarExists(dto.carId)) return@onEach
                    db.chatMessageDao().insert(dto.toChatMessage())
                    Log.d(TAG, "💬 Chat message received: ${dto.id}")
                    maybeNotifyChat(dto)
                } catch (e: Exception) { Log.e(TAG, "Error handling chat message", e) }
            }.catch { Log.w(TAG, "Chat insert flow error: ${it.message}") }.launchIn(chScope)

            ch.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "chat_messages"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(ChatMessageDto.serializer(), change.record)
                    val message = dto.toChatMessage()
                    // Правка меняет только текст. Через REPLACE строка сообщения
                    // удалялась и вставлялась заново, унося каскадом все реакции
                    // на него: кто-то поправил опечатку — реакции исчезли у всех.
                    if (db.chatMessageDao().getById(message.id) != null) {
                        db.chatMessageDao().updateContent(message.id, message.message, message.isEdited)
                    } else {
                        db.chatMessageDao().insert(message)
                    }
                    Log.d(TAG, "✏️ Chat message updated: ${dto.id}")
                } catch (e: Exception) { Log.e(TAG, "Error handling chat update", e) }
            }.catch { Log.w(TAG, "Chat update flow error: ${it.message}") }.launchIn(chScope)

            ch.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
                table = "chat_messages"
            }.onEach { change ->
                try {
                    // oldRecord with DEFAULT replica identity only contains PK.
                    // Use a minimal DTO to avoid deserialization failures on other required fields.
                    val id = change.oldRecord["id"]?.toString()?.trim('"') ?: return@onEach
                    db.chatMessageDao().deleteById(id)
                    Log.d(TAG, "🗑️ Chat message deleted: $id")
                } catch (e: Exception) { Log.e(TAG, "Error handling chat delete", e) }
            }.catch { Log.w(TAG, "Chat delete flow error: ${it.message}") }.launchIn(chScope)

            // ── Chat reactions ────────────────────────────────────────────────
            ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "chat_reactions"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(
                        com.aggin.carcost.data.remote.repository.ChatReactionDto.serializer(),
                        change.record
                    )
                    // Only insert if the parent message exists locally; Room FK would fail otherwise.
                    if (db.chatMessageDao().getById(dto.messageId) != null) {
                        db.chatReactionDao().insert(
                            com.aggin.carcost.data.local.database.entities.ChatReaction(
                                id = dto.id,
                                messageId = dto.messageId,
                                userId = dto.userId,
                                userEmail = dto.userEmail,
                                emoji = dto.emoji,
                                createdAt = dto.createdAt
                            )
                        )
                        Log.d(TAG, "👍 Reaction inserted: ${dto.emoji} on ${dto.messageId}")
                    }
                } catch (e: Exception) { Log.e(TAG, "Error handling reaction insert", e) }
            }.catch { Log.w(TAG, "Reaction insert flow error: ${it.message}") }.launchIn(chScope)

            ch.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
                table = "chat_reactions"
            }.onEach { change ->
                try {
                    val id = change.oldRecord["id"]?.toString()?.trim('"') ?: return@onEach
                    db.chatReactionDao().deleteById(id)
                    Log.d(TAG, "👎 Reaction deleted: $id")
                } catch (e: Exception) { Log.e(TAG, "Error handling reaction delete", e) }
            }.catch { Log.w(TAG, "Reaction delete flow error: ${it.message}") }.launchIn(chScope)

            // ── Car Invitations ───────────────────────────────────────────────

            val currentEmail = try { supabase.auth.currentUserOrNull()?.email } catch (e: Exception) { null }
            if (currentEmail != null) {
                ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "car_invitations"
                    filter = "invited_email=eq.$currentEmail"
                }.onEach { change ->
                    try {
                        val dto = json.decodeFromJsonElement(CarInvitationDto.serializer(), change.record)
                        // Look up car name for the notification
                        val car = db.carDao().getCarById(dto.carId)
                        val carName = if (car != null) "${car.brand} ${car.model}" else "авто"
                        val notifId = INVITATION_NOTIF_BASE + (abs(dto.id.hashCode()) % NOTIF_RANGE)
                        NotificationHelper.sendInvitationNotification(context, notifId, carName)
                        Log.d(TAG, "📨 Invitation received for car ${dto.carId}")
                    } catch (e: Exception) { Log.e(TAG, "Error handling invitation insert", e) }
                }.catch { Log.w(TAG, "Invitation insert flow error: ${it.message}") }.launchIn(chScope)
            }

            // Пауза перед subscribe() — не «на всякий случай», а из-за гонки.
            //
            // postgresChangeFlow регистрирует привязку к таблице не при создании
            // потока, а когда поток НАЧИНАЕТ собираться. launchIn лишь запускает
            // корутину и сразу возвращает управление, поэтому следующая строка
            // отправляла join на сервер раньше, чем корутины успевали стартовать.
            // Канал присоединялся пустым — без единой подписки на изменения.
            //
            // Проверено на сервере: таблица realtime.subscription была пуста при
            // живых подключениях. Клиент подключался, но не подписывался ни на
            // что, и сервер честно ничего не отдавал. Отсюда чат, который не
            // обновляется в реальном времени вообще, а не только через час.
            kotlinx.coroutines.delay(SUBSCRIBE_DELAY_MS)

            ch.subscribe()
            Log.d(TAG, "✅ Realtime channel subscribed")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect Realtime channel", e)
            activeChannel = null
        }
    }

    private suspend fun disconnectChannel() {
        try {
            activeChannel?.unsubscribe()
            activeChannel?.let { supabase.realtime.removeChannel(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error disconnecting Realtime channel: ${e.message}")
        } finally {
            // Сборщики старого канала обязаны умереть вместе с ним, иначе
            // события обрабатываются столько раз, сколько было переподключений
            channelScope?.cancel()
            channelScope = null
            activeChannel = null
        }
    }

    // ── Notification helpers ─────────────────────────────────────────────────

    /**
     * Shows a push notification for an expense change if it was made
     * by a DIFFERENT user (i.e. a member of a shared car).
     */
    private suspend fun maybeNotifyExpense(dto: ExpenseDto, isUpdate: Boolean) {
        val currentUserId = supabase.auth.currentUserOrNull()?.id ?: return
        if (dto.userId == currentUserId) return   // my own action — skip

        val car = db.carDao().getCarById(dto.carId) ?: return
        val carName = "${car.brand} ${car.model}"
        val actorEmail = db.carMemberDao().getEmailByUserId(dto.userId)
        val categoryName = NotificationHelper.categoryDisplayName(context, dto.category)
        val notifId = EXPENSE_NOTIF_BASE + (abs(dto.id.hashCode()) % NOTIF_RANGE)

        NotificationHelper.sendSharedExpenseNotification(
            context = context,
            notificationId = notifId,
            carName = carName,
            categoryName = categoryName,
            amount = dto.amount,
            actorEmail = actorEmail,
            isUpdate = isUpdate,
            carId = dto.carId
        )
        Log.d(TAG, "🔔 Sent expense notification for ${dto.id}")
    }

    /**
     * Shows a push notification for a maintenance reminder change if it was made
     * by a DIFFERENT user.
     */
    private suspend fun maybeNotifyReminder(dto: MaintenanceReminderDto, isUpdate: Boolean) {
        val currentUserId = supabase.auth.currentUserOrNull()?.id ?: return
        if (dto.userId == currentUserId) return   // my own action — skip

        val car = db.carDao().getCarById(dto.carId) ?: return
        val carName = "${car.brand} ${car.model}"
        val actorEmail = db.carMemberDao().getEmailByUserId(dto.userId)
        val typeName = NotificationHelper.reminderTypeDisplayName(context, dto.type)
        val notifId = REMINDER_NOTIF_BASE + (abs(dto.id.hashCode()) % NOTIF_RANGE)

        NotificationHelper.sendSharedReminderNotification(
            context = context,
            notificationId = notifId,
            carName = carName,
            reminderTypeName = typeName,
            actorEmail = actorEmail,
            isUpdate = isUpdate,
            carId = dto.carId
        )
        Log.d(TAG, "🔔 Sent reminder notification for ${dto.id}")
    }

    /**
     * Подтягивает пробег автомобиля к записи, пришедшей от совладельца.
     *
     * Пробег указан в каждом расходе, но на карточку автомобиля он попадал
     * только с экрана добавления на этом же устройстве. Заправка совладельца
     * приходила, а пробег на карточке оставался прежним.
     */
    private suspend fun raiseOdometer(carId: String) {
        try {
            com.aggin.carcost.data.local.repository.CarRepository(db.carDao())
                .refreshOdometerFromExpenses(carId, db.expenseDao())
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось обновить пробег $carId: ${e.message}")
        }
    }

    private suspend fun maybeNotifyChat(dto: ChatMessageDto) {
        val currentUserId = supabase.auth.currentUserOrNull()?.id ?: return
        if (dto.userId == currentUserId) return  // own message — skip
        if (ActiveChatTracker.activeCarId == dto.carId) return  // user is in this chat — skip

        val car = db.carDao().getCarById(dto.carId) ?: return
        val carName = "${car.brand} ${car.model}"
        val sender = dto.userEmail.substringBefore("@")
        val notifId = CHAT_NOTIF_BASE + (abs(dto.id.hashCode()) % NOTIF_RANGE)

        NotificationHelper.sendChatNotification(
            context = context,
            notificationId = notifId,
            carName = carName,
            senderName = sender,
            message = dto.message,
            carId = dto.carId
        )
        Log.d(TAG, "🔔 Sent chat notification from $sender")
    }

    companion object {
        /**
         * Сколько ждать после launchIn, прежде чем присоединять канал.
         *
         * Нужно, чтобы корутины успели начать сбор потоков и зарегистрировать
         * привязки к таблицам — иначе join уходит на сервер пустым. Значение с
         * запасом: старт корутин занимает миллисекунды, а задержка происходит
         * один раз при подключении и человеку незаметна.
         */
        private const val SUBSCRIBE_DELAY_MS    = 300L

        /**
         * Окно, в пределах которого повторный запрос на подключение считается
         * дублем того же самого. Пять секунд с запасом перекрывают разброс между
         * появлением сессии и выходом приложения на передний план; настоящее
         * переподключение — после разрыва связи или обновления токена — случается
         * заметно реже и в это окно не попадает.
         */
        private const val CONNECT_DEBOUNCE_MS   = 5_000L

        private const val EXPENSE_NOTIF_BASE    = 20_000
        private const val REMINDER_NOTIF_BASE  = 30_000
        private const val CHAT_NOTIF_BASE      = 40_000
        private const val INVITATION_NOTIF_BASE = 50_000
        private const val NOTIF_RANGE           = 9_000
    }
}
