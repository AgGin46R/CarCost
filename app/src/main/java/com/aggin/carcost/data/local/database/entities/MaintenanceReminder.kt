package com.aggin.carcost.data.local.database.entities

import androidx.annotation.StringRes
import com.aggin.carcost.R
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
@Entity(
    tableName = "maintenance_reminders",
    foreignKeys = [
        ForeignKey(
            entity = Car::class,
            parentColumns = ["id"],
            childColumns = ["carId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("carId")]
)
data class MaintenanceReminder(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(), // ✅ Изменено на String UUID

    val carId: String, // ✅ Изменено на String
    val type: MaintenanceType,

    // Когда последний раз меняли
    val lastChangeOdometer: Int,
    val lastChangeDate: Long = System.currentTimeMillis(),

    // Интервал замены (в км)
    val intervalKm: Int,

    // Следующая замена
    val nextChangeOdometer: Int,

    // Активность напоминания
    val isActive: Boolean = true,

    // Интервал по дням (опционально, для напоминаний по дате)
    val intervalDays: Int? = null,
    // Дата следующего ТО (null = только пробег)
    val nextChangeDate: Long? = null,

    // Дополнительная информация
    val notes: String? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : Parcelable

/**
 * Виды планового обслуживания.
 *
 * Хранится ключ подписи, а не сама подпись: названия работ показываются на
 * экранах, в уведомлениях и в выгрузке, и достать текст в этих трёх местах
 * можно только имея контекст. Ключ у всех троих общий.
 *
 * Ключи те же, что у ServiceType в Labels.kt: «Замена масла» — одна работа, и
 * переводить её дважды значит однажды получить два разных перевода.
 */
enum class MaintenanceType(
    @StringRes val displayNameRes: Int,
    val defaultInterval: Int
) {
    OIL_CHANGE(R.string.service_oil_change, 10000),
    OIL_FILTER(R.string.service_oil_filter, 10000),
    AIR_FILTER(R.string.service_air_filter, 20000),
    CABIN_FILTER(R.string.service_cabin_filter, 15000),
    FUEL_FILTER(R.string.service_fuel_filter, 30000),
    SPARK_PLUGS(R.string.service_spark_plugs, 30000),
    BRAKE_PADS(R.string.service_brake_pads, 40000),
    TIMING_BELT(R.string.service_timing_belt, 60000),
    TRANSMISSION_FLUID(R.string.service_transmission_fluid, 60000),
    COOLANT(R.string.service_coolant, 40000),
    BRAKE_FLUID(R.string.service_brake_fluid, 40000),

    // ── Электротяга ──────────────────────────────────────────────────────────
    REDUCER_OIL(R.string.service_reducer_oil, 60000),
    BATTERY_COOLANT(R.string.service_battery_coolant, 100000),
    BATTERY_HEALTH(R.string.service_battery_health, 20000),
    BRAKE_CALIPERS(R.string.service_brake_calipers, 20000),

    // ── Мотоцикл ─────────────────────────────────────────────────────────────
    CHAIN_LUBE(R.string.service_chain_lube, 500),
    CHAIN_REPLACE(R.string.service_chain_replace, 25000),
    FORK_OIL(R.string.service_fork_oil, 20000),
    VALVE_CLEARANCE(R.string.service_valve_clearance, 24000)
}

/**
 * Какие виды обслуживания предлагать владельцу этой машины.
 *
 * У электромобиля нет ни масла в двигателе, ни свечей, ни ремня ГРМ — показывать
 * их значит засорять список тем, чего у человека физически не существует.
 *
 * Обратное тоже верно: чистка суппортов попадает в список электромобиля не для
 * полноты. На электротяге почти всё торможение рекуперативное, колодки не
 * изнашиваются годами — и именно поэтому суппорты закисают. Владельцу это
 * неочевидно, и напоминание здесь полезнее, чем про сами колодки.
 */
fun maintenanceTypesFor(
    fuelType: FuelType,
    vehicleType: VehicleType = VehicleType.CAR
): List<MaintenanceType> {
    val engineOnly = setOf(
        MaintenanceType.OIL_CHANGE,
        MaintenanceType.OIL_FILTER,
        MaintenanceType.AIR_FILTER,
        MaintenanceType.FUEL_FILTER,
        MaintenanceType.SPARK_PLUGS,
        MaintenanceType.TIMING_BELT
    )
    val electricOnly = setOf(
        MaintenanceType.REDUCER_OIL,
        MaintenanceType.BATTERY_COOLANT,
        MaintenanceType.BATTERY_HEALTH,
        MaintenanceType.BRAKE_CALIPERS
    )

    // У мотоцикла нет ни салонного фильтра, ни развала-схождения: одно некуда
    // ставить, второе нечему делать на двух колёсах
    val carOnly = setOf(
        MaintenanceType.CABIN_FILTER
    )
    val motorcycleOnly = setOf(
        MaintenanceType.CHAIN_LUBE,
        MaintenanceType.CHAIN_REPLACE,
        MaintenanceType.FORK_OIL,
        MaintenanceType.VALVE_CLEARANCE
    )

    return MaintenanceType.entries.filter { type ->
        when {
            type in engineOnly -> fuelType.canRefuel
            type in electricOnly -> fuelType.canCharge
            type in carOnly -> vehicleType == VehicleType.CAR
            type in motorcycleOnly -> vehicleType == VehicleType.MOTORCYCLE
            else -> true
        }
    }
}
