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
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(), // ✅ Изменено на String UUID

    // Связь с автомобилем
    val carId: String, // ✅ Изменено на String

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

    // Геолокация
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
    val maintenanceParts: String? = null, // список запчастей/работ

    /**
     * Кто внёс запись. Нужен для разбивки «кто сколько заплатил» в общей машине.
     *
     * Сервер хранил автора с самого начала, но при загрузке на телефон поле
     * выбрасывалось, и локально узнать плательщика было нельзя.
     *
     * Nullable: у записей, созданных до появления этого поля, автор неизвестен.
     * Подставлять туда текущего пользователя нельзя — это приписало бы ему
     * чужие траты в общей машине.
     */
    val userId: String? = null,

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