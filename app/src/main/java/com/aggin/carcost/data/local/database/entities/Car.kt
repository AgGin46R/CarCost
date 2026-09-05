package com.aggin.carcost.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
@Entity(tableName = "cars")
data class Car(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),

    // Основная информация
    val brand: String,
    val model: String,
    val year: Int,
    val licensePlate: String,

    // Дополнительная информация
    val vin: String? = null,
    val color: String? = null,
    val photoUri: String? = null,

    // Пробег
    val currentOdometer: Int, // в километрах
    val odometerUnit: OdometerUnit = OdometerUnit.KM,

    // Покупка
    val purchaseDate: Long, // timestamp
    val purchasePrice: Double? = null,
    val purchaseOdometer: Int? = null,

    // Валюта (ISO 4217)
    val currency: String = "RUB",

    /**
     * Что это за техника.
     *
     * Значение по умолчанию — автомобиль: у всех записей, заведённых до
     * появления поля, это верно, и переносить данные не потребовалось.
     */
    val vehicleType: VehicleType = VehicleType.CAR,

    // Топливо
    val fuelType: FuelType = FuelType.GASOLINE,
    val tankCapacity: Double? = null, // в литрах

    /**
     * Мощность двигателя в лошадиных силах — из ПТС.
     *
     * Пусто у всех машин, заведённых до появления поля, и это нормально:
     * без мощности налог просто не считается и не показывается. Подставлять
     * сюда среднее по модели нельзя — налог человек платит настоящий.
     */
    val enginePowerHp: Int? = null,

    /**
     * Ставка транспортного налога за лошадиную силу в рублях.
     *
     * Пусто — считаем по базовой ставке из НК. Регионы меняют ставки каждый
     * год и различаются в разы, поэтому последнее слово за владельцем:
     * он видит квитанцию, а приложение нет.
     */
    val taxRatePerHp: Double? = null,

    // Статус
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : Parcelable

enum class OdometerUnit {
    KM,  // Километры
    MI   // Мили
}

/**
 * Вид техники.
 *
 * Разделение не косметическое: у мотоцикла нет салонного фильтра и развала,
 * зато есть цепь и вилка, а «бак 45 литров» звучит для него странно.
 * Предлагать владельцу мотоцикла обслуживание автомобиля — то же самое, что
 * предлагать электромобилю замену свечей.
 */
enum class VehicleType(val labelRu: String) {
    CAR("Автомобиль"),
    MOTORCYCLE("Мотоцикл")
}

/**
 * Чем машина движется — и, как следствие, что владелец может записать.
 *
 * HYBRID означает гибрид **без розетки**: батарея заряжается только
 * рекуперацией, и зарядку в него не запишешь никогда. Значение существует
 * давно и смысла не меняет, поэтому переносить старые данные не потребовалось.
 *
 * PLUGIN_HYBRID — подключаемый: и заправки, и зарядки. Сюда же относятся
 * последовательные гибриды вроде Li Xiang и Voyah, где ДВС работает
 * генератором: с точки зрения учёта расходов они ведут себя так же.
 */
enum class FuelType {
    GASOLINE,      // Бензин
    DIESEL,        // Дизель
    ELECTRIC,      // Электро
    HYBRID,        // Гибрид (без розетки)
    PLUGIN_HYBRID, // Подключаемый гибрид
    GAS,           // Газ
    OTHER
}

/**
 * Заливает ли эта машина топливо. Определяет, показывать ли форму заправки,
 * предлагать ли ТО двигателя и считать ли литры на сотню.
 */
val FuelType.canRefuel: Boolean
    get() = this != FuelType.ELECTRIC

/**
 * Заряжается ли эта машина от сети.
 *
 * У обычного гибрида — нет: его батарея живёт рекуперацией, и розетки у него
 * попросту не существует. Дать ему кнопку зарядки значит предложить записать
 * то, чего не бывает.
 */
val FuelType.canCharge: Boolean
    get() = this == FuelType.ELECTRIC || this == FuelType.PLUGIN_HYBRID

/** Обе энергии сразу — расход по отдельности теряет смысл, см. расчёты */
val FuelType.isDualSource: Boolean
    get() = canRefuel && canCharge