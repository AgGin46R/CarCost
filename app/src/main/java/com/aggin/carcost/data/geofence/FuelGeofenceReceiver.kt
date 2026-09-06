package com.aggin.carcost.data.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aggin.carcost.R
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.settings.SettingsManager
import com.aggin.carcost.data.notifications.NotificationHelper
import com.aggin.carcost.domain.fuel.NearbyStationFinder
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Срабатывание геозоны заправки.
 *
 * Уведомление шлётся не на въезде и не сразу на выезде, а по связке
 * «постояли внутри — выехали». Въезд ничего не значит: мимо заправки ездят
 * каждый день. Простой без выезда — тоже: человек может обедать в кафе рядом.
 * А вот постоял пять минут и уехал — это заправка.
 */
class FuelGeofenceReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_GEOFENCE = "com.aggin.carcost.FUEL_GEOFENCE"

        private const val TAG = "FuelGeofence"
        private const val PREFS = "fuel_geofence"
        private const val NOTIFICATION_ID = 7400

        /**
         * Сколько времени после простоя выезд ещё считается заправкой.
         *
         * Полчаса: система сообщает о выезде не мгновенно, а заправка редко
         * занимает дольше.
         */
        private const val DWELL_VALID_MS = 30 * 60 * 1000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w(TAG, "Ошибка геозоны: ${event.errorCode}")
            return
        }

        val ids = event.triggeringGeofences?.map { it.requestId }.orEmpty()
        if (ids.isEmpty()) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_DWELL -> {
                // Просто запоминаем, что тут стояли. Уведомление — на выезде
                prefs.edit().apply {
                    ids.forEach { putLong(it, System.currentTimeMillis()) }
                }.apply()
            }

            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                val now = System.currentTimeMillis()
                val dwelled = ids.firstOrNull { id ->
                    val at = prefs.getLong(id, 0L)
                    at > 0 && now - at <= DWELL_VALID_MS
                } ?: return

                prefs.edit().remove(dwelled).apply()
                notifyFillUp(context, dwelled)
            }
        }
    }

    /**
     * Предлагает записать заправку.
     *
     * Приёмник живёт считаные секунды, поэтому работа уходит в отдельную
     * корутину с goAsync: без него процесс могли убить на середине чтения базы.
     */
    private fun notifyFillUp(context: Context, geofenceId: String) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Проверяем только свой переключатель геозон. Переключатель
                // уведомлений о топливе проверяется централизованно, в
                // NotificationHelper — две проверки одного и того же в разных
                // местах однажды разойдутся
                if (!SettingsManager(context).geofenceFuelFlow.first()) return@launch

                val db = AppDatabase.getDatabase(context)
                val car = db.carDao().getAllActiveCarsSync().firstOrNull() ?: return@launch

                // Идентификатор зоны — это «Название@широта,долгота»
                val name = geofenceId.substringBefore('@')
                if (name.isBlank()) return@launch

                // Та же проверка, что и у подсказки по концу поездки: если
                // заправку уже записали руками, напоминать не о чем
                val expenses = db.expenseDao().getExpensesByCarIdSync(car.id)
                if (NearbyStationFinder.recentlyRecorded(expenses)) return@launch

                NotificationHelper.sendGenericNotification(
                    kind = com.aggin.carcost.data.local.settings.SettingsManager.NotifKind.FUEL,
                    context = context,
                    notificationId = NOTIFICATION_ID,
                    title = context.getString(R.string.fuelhint_title),
                    body = context.getString(R.string.fuelhint_body, name),
                    carId = car.id,
                    navType = NotificationHelper.NAV_TYPE_ADD_FUEL,
                    navExtra = name
                )
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось предложить запись заправки", e)
            } finally {
                pending.finish()
            }
        }
    }
}
