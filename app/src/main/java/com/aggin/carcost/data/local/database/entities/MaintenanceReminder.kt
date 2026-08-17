package com.aggin.carcost.data.local.database.entities

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

enum class MaintenanceType(val displayName: String, val defaultInterval: Int) {
    OIL_CHANGE("Замена масла", 10000),
    OIL_FILTER("Масляный фильтр", 10000),
    AIR_FILTER("Воздушный фильтр", 20000),
    CABIN_FILTER("Салонный фильтр", 15000),
    FUEL_FILTER("Топливный фильтр", 30000),
    SPARK_PLUGS("Свечи зажигания", 30000),
    BRAKE_PADS("Тормозные колодки", 40000),
    TIMING_BELT("Ремень ГРМ", 60000),
    TRANSMISSION_FLUID("Трансмиссионное масло", 60000),
    COOLANT("Охлаждающая жидкость", 40000),
    BRAKE_FLUID("Тормозная жидкость", 40000),

    // ── Электротяга ──────────────────────────────────────────────────────────
    REDUCER_OIL("Масло редуктора", 60000),
    BATTERY_COOLANT("Охлаждающая жидкость батареи", 100000),
    BATTERY_HEALTH("Проверка состояния батареи", 20000),
    BRAKE_CALIPERS("Чистка и смазка суппортов", 20000)
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
fun maintenanceTypesFor(fuelType: FuelType): List<MaintenanceType> {
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

    return MaintenanceType.entries.filter { type ->
        when {
            type in engineOnly -> fuelType.canRefuel
            type in electricOnly -> fuelType.canCharge
            else -> true
        }
    }
}
