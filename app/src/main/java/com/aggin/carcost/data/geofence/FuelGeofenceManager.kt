package com.aggin.carcost.data.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.reference.FuelStations
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Геозоны вокруг заправок, на которых человек уже бывал.
 *
 * Зачем это поверх подсказки по концу поездки: та работает только у тех, кто
 * записывает поездки по GPS вручную. Геозона срабатывает сама, без запущенного
 * приложения, — но именно поэтому требует фонового местоположения и включается
 * отдельным переключателем, а не по умолчанию.
 *
 * Справочника АЗС здесь по-прежнему нет. Зоны строятся только вокруг мест из
 * собственной истории заправок.
 */
object FuelGeofenceManager {

    private const val TAG = "FuelGeofence"

    /**
     * Сколько зон держим.
     *
     * У системы жёсткий предел в сто геозон на приложение, но дело не в нём:
     * заправки из глубины истории — это чужой город и поездка трёхлетней
     * давности, и зона там будет только тратить батарею.
     */
    private const val MAX_GEOFENCES = 20

    /** Радиус зоны. Заправка с заездом и выездом крупнее, чем кажется */
    private const val RADIUS_M = 180f

    /**
     * Сколько нужно простоять внутри, чтобы это считалось заездом.
     *
     * Без этого зона срабатывала бы на каждый проезд мимо по соседней улице —
     * а заправка занимает минуты.
     */
    private const val LOITERING_DELAY_MS = 5 * 60 * 1000

    /** Заправка попадает в зоны, только если бывали там хотя бы дважды */
    private const val MIN_VISITS = 2

    data class Station(val id: String, val name: String, val lat: Double, val lon: Double)

    /**
     * Заправки, вокруг которых имеет смысл ставить зоны.
     *
     * Координаты усредняются по всем визитам: одна запись могла быть сделана
     * уже по дороге домой, и точка уехала бы на километр.
     */
    suspend fun stationsFor(context: Context, carId: String): List<Station> =
        withContext(Dispatchers.IO) {
            val expenses = AppDatabase.getDatabase(context).expenseDao()
                .getExpensesByCarIdSync(carId)
                .filter { it.category == ExpenseCategory.FUEL }

            expenses.mapNotNull { e ->
                val lat = e.latitude ?: return@mapNotNull null
                val lon = e.longitude ?: return@mapNotNull null
                val name = FuelStations.normalize(e.location) ?: return@mapNotNull null
                Triple(name, lat, lon)
            }
                // Одна сеть — несколько точек в городе, поэтому группируем не
                // по названию, а по названию вместе с округлёнными координатами
                .groupBy { (name, lat, lon) ->
                    "$name@${"%.3f".format(lat)},${"%.3f".format(lon)}"
                }
                .filter { it.value.size >= MIN_VISITS }
                .map { (key, visits) ->
                    Station(
                        id = key,
                        name = visits.first().first,
                        lat = visits.map { it.second }.average(),
                        lon = visits.map { it.third }.average()
                    )
                }
                .sortedByDescending { station ->
                    expenses.count { e ->
                        e.latitude != null && e.longitude != null &&
                            com.aggin.carcost.domain.fuel.NearbyStationFinder.distanceMeters(
                                station.lat, station.lon, e.latitude, e.longitude
                            ) <= RADIUS_M
                    }
                }
                .take(MAX_GEOFENCES)
        }

    /** Есть ли всё нужное, чтобы зоны вообще работали */
    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine) return false

        // До Android 10 фоновое местоположение отдельным разрешением не было
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true

        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Перестраивает зоны под текущую историю заправок.
     *
     * Вызывается при включении переключателя и после запуска приложения:
     * система забывает геозоны при перезагрузке телефона, и без пересборки
     * функция тихо переставала работать.
     *
     * @return сколько зон поставлено. Ноль — либо нет прав, либо нет заправок
     *   с координатами, о которых стоит знать
     */
    @SuppressLint("MissingPermission")
    suspend fun refresh(context: Context, carId: String): Int {
        if (!hasPermission(context)) {
            Log.d(TAG, "Нет разрешения на фоновое местоположение — зоны не ставим")
            return 0
        }

        val client = LocationServices.getGeofencingClient(context)
        removeAll(context)

        val stations = stationsFor(context, carId)
        if (stations.isEmpty()) return 0

        val geofences = stations.map { station ->
            Geofence.Builder()
                .setRequestId(station.id)
                .setCircularRegion(station.lat, station.lon, RADIUS_M)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setLoiteringDelay(LOITERING_DELAY_MS)
                .setTransitionTypes(
                    Geofence.GEOFENCE_TRANSITION_DWELL or Geofence.GEOFENCE_TRANSITION_EXIT
                )
                .build()
        }

        val request = GeofencingRequest.Builder()
            // Не INITIAL_TRIGGER_DWELL: иначе включение переключателя дома
            // рядом с заправкой сразу выдало бы уведомление о заправке,
            // которой не было
            .setInitialTrigger(0)
            .addGeofences(geofences)
            .build()

        return try {
            client.addGeofences(request, pendingIntent(context))
            Log.d(TAG, "Поставлено зон: ${geofences.size}")
            geofences.size
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось поставить зоны", e)
            0
        }
    }

    /** Снимает все зоны — при выключении переключателя или отзыве разрешения */
    fun disable(context: Context) {
        removeAll(context)
    }

    private fun removeAll(context: Context) {
        try {
            LocationServices.getGeofencingClient(context)
                .removeGeofences(pendingIntent(context))
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось снять зоны", e)
        }
    }

    /**
     * Один и тот же PendingIntent на все операции.
     *
     * Снятие зон работает только по тому же intent, по которому они ставились,
     * а совпадение определяется действием и классом приёмника — не тем, храним
     * ли мы ссылку. Поэтому строим заново каждый раз: кешировать значило бы,
     * что после перезапуска процесса снять старые зоны уже нечем.
     */
    private fun pendingIntent(context: Context): PendingIntent {
        val app = context.applicationContext
        val intent = Intent(app, FuelGeofenceReceiver::class.java)
            .setAction(FuelGeofenceReceiver.ACTION_GEOFENCE)
        // MUTABLE обязателен: сервисы Google дописывают в этот интент данные о
        // сработавшей зоне, а в IMMUTABLE дописать нечего — приёмник получал бы
        // пустое событие
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(app, 0, intent, flags)
    }
}
