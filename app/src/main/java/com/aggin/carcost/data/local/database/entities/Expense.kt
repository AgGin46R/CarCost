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

    /**
     * Киловатт-часы у зарядки — то же, чем литры являются для заправки.
     *
     * Отдельным полем, а не переиспользованием fuelLiters: у подключаемого
     * гибрида бывают и заправки, и зарядки, и складывать литры с киловатт-часами
     * в одну колонку значит однажды получить бессмысленный расход.
     *
     * Признак полного бака (isFullTank) у зарядки означает заряд до 100%: расчёт
     * расхода в обоих случаях строится на отрезках между полными заправками.
     */
    val energyKwh: Double? = null,

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
    /**
     * Когда запись последний раз подтверждена сервером: отправлена туда или
     * получена оттуда.
     *
     * Без этого признака синхронизация не могла отличить «расход ещё не уехал
     * на сервер» от «расход был на сервере, и его удалил совладелец». Оба
     * случая выглядят одинаково — записи нет в ответе сервера, — и обработка
     * была одна: отправить. В результате удалённый совладельцем расход
     * возвращался обратно, причём не только на своём телефоне, но и у него.
     *
     * null означает «сервер этой записи никогда не видел».
     */
    val syncedAt: Long? = null,

    val updatedAt: Long = System.currentTimeMillis()
) : Parcelable

enum class ExpenseCategory {
    FUEL,           // Топливо
    /**
     * Зарядка от сети.
     *
     * Отдельно от топлива намеренно: у владельца подключаемого гибрида главный
     * вопрос — сколько ушло на бензин, а сколько на электричество. В одной
     * категории эти суммы слиплись бы в круговой диаграмме и в бюджете.
     */
    CHARGING,       // Зарядка
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

    // ── Электротяга ──────────────────────────────────────────────────────────
    REDUCER_OIL,         // Масло редуктора
    BATTERY_COOLANT,     // Охлаждающая жидкость батареи
    BATTERY_HEALTH,      // Проверка состояния батареи
    BRAKE_CALIPERS,      // Чистка и смазка суппортов

    // ── Мотоцикл ─────────────────────────────────────────────────────────────
    CHAIN_LUBE,          // Смазка цепи
    CHAIN_REPLACE,       // Замена цепи и звёзд
    FORK_OIL,            // Масло в вилке
    VALVE_CLEARANCE,     // Регулировка клапанов

    OTHER
}

/**
 * Категории расходов, осмысленные для этой машины.
 *
 * У электромобиля нет заправок, у гибрида без розетки — зарядок. Предлагать
 * записать то, чего не бывает, значит однажды получить запись «12 литров» у
 * электромобиля и испорченный расчёт расхода.
 */
fun expenseCategoriesFor(fuelType: FuelType): List<ExpenseCategory> =
    ExpenseCategory.entries.filter { category ->
        when (category) {
            ExpenseCategory.FUEL -> fuelType.canRefuel
            ExpenseCategory.CHARGING -> fuelType.canCharge
            else -> true
        }
    }

/**
 * Виды работ, осмысленные для этой машины.
 *
 * Список тот же по смыслу, что и у напоминаний (`maintenanceTypesFor`), но
 * перечисления разные: здесь то, что человек выбирает при записи выполненной
 * работы, там — то, о чём приложение напоминает заранее.
 */
fun serviceTypesFor(
    fuelType: FuelType,
    vehicleType: VehicleType = VehicleType.CAR
): List<ServiceType> {
    val engineOnly = setOf(
        ServiceType.OIL_CHANGE,
        ServiceType.OIL_FILTER,
        ServiceType.AIR_FILTER,
        ServiceType.FUEL_FILTER,
        ServiceType.SPARK_PLUGS,
        ServiceType.TIMING_BELT
    )
    val electricOnly = setOf(
        ServiceType.REDUCER_OIL,
        ServiceType.BATTERY_COOLANT,
        ServiceType.BATTERY_HEALTH,
        ServiceType.BRAKE_CALIPERS
    )

    val carOnly = setOf(ServiceType.CABIN_FILTER, ServiceType.ALIGNMENT)
    val motorcycleOnly = setOf(
        ServiceType.CHAIN_LUBE,
        ServiceType.CHAIN_REPLACE,
        ServiceType.FORK_OIL,
        ServiceType.VALVE_CLEARANCE
    )

    return ServiceType.entries.filter { type ->
        when {
            type in engineOnly -> fuelType.canRefuel
            type in electricOnly -> fuelType.canCharge
            type in carOnly -> vehicleType == VehicleType.CAR
            type in motorcycleOnly -> vehicleType == VehicleType.MOTORCYCLE
            else -> true
        }
    }
}