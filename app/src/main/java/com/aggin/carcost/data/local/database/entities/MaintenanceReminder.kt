package com.aggin.carcost.data.local.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
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
    BRAKE_FLUID("Тормозная жидкость", 40000)
}
