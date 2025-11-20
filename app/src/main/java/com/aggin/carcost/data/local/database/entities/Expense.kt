package com.aggin.carcost.data.local.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = Car::class,
            parentColumns = ["id"],
            childColumns = ["carId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("carId"), Index("date")]
)
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Связь с автомобилем
    val carId: Long,

    // Основная информация
    val category: ExpenseCategory,
    val amount: Double,
    val currency: String = "RUB",
    val date: Long,
    val odometer: Int,

    // Описание и чек
    val title: String? = null,
    val description: String? = null,
    val receiptPhotoUri: String? = null,
    val location: String? = null,

    // ДОБАВЬ ЭТИ ПОЛЯ ДЛЯ ГЕОЛОКАЦИИ
    val latitude: Double? = null,
    val longitude: Double? = null,

    // Специфичные поля для заправки
    val fuelLiters: Double? = null,
    val fuelType: String? = null,
    val isFullTank: Boolean = false,

    // Специфичные поля для обслуживания
    val serviceType: ServiceType? = null,
    val nextServiceOdometer: Int? = null,
    val nextServiceDate: Long? = null,
    val workshopName: String? = null,

    // Мета-информация
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : Parcelable

enum class ExpenseCategory {
    FUEL,           // Топливо
    MAINTENANCE,    // Обслуживание (ТО)
    REPAIR,         // Ремонт
    INSURANCE,      // Страховка
    TAX,            // Налоги
    PARKING,        // Парковка
    TOLL,           // Платная дорога
    WASH,           // Мойка
    FINE,           // Штраф
    ACCESSORIES,    // Аксессуары
    OTHER           // Прочее
}

enum class ServiceType {
    OIL_CHANGE,          // Замена масла
    OIL_FILTER,          // Масляный фильтр
    AIR_FILTER,          // Воздушный фильтр
    FUEL_FILTER,         // Топливный фильтр
    CABIN_FILTER,        // Салонный фильтр
    SPARK_PLUGS,         // Свечи зажигания
    BRAKE_PADS,          // Тормозные колодки
    BRAKE_FLUID,         // Тормозная жидкость
    COOLANT,             // Охлаждающая жидкость
    TRANSMISSION_FLUID,  // Трансмиссионное масло
    TIMING_BELT,         // Ремень ГРМ
    TIRES,               // Шины
    BATTERY,             // Аккумулятор
    ALIGNMENT,           // Развал-схождение
    BALANCING,           // Балансировка
    INSPECTION,          // Техосмотр
    FULL_SERVICE,        // Полное ТО
    OTHER
}