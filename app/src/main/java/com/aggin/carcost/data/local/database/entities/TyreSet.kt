package com.aggin.carcost.data.local.database.entities

import androidx.annotation.StringRes
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aggin.carcost.R
import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * Комплект шин.
 *
 * Шины — единственная крупная трата, которая живёт дольше одной записи о
 * расходе: комплект покупают раз в несколько лет, ставят и снимают дважды в
 * год, и вопрос «сколько эта резина уже отходила» до сих пор ответа в
 * приложении не имел. Расход о покупке есть, но он говорит, сколько заплатили,
 * а не сколько проехали.
 *
 * Пробег накапливается по периодам установки, а не считается от даты покупки:
 * комплект может пролежать сезон в гараже, и это не износ.
 */
@Serializable
@Entity(
    tableName = "tyre_sets",
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
data class TyreSet(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val carId: String,

    /** Как человек его называет: «Зимняя Nokian», «Липучка на дисках» */
    val name: String,
    val season: TyreSeason = TyreSeason.SUMMER,
    /** Размер строкой, как написано на боковине: «205/55 R16 91V» */
    val size: String? = null,

    val purchaseDate: Long? = null,
    val purchasePrice: Double? = null,

    /**
     * Накопленный пробег за все снятые периоды.
     *
     * Пробег текущего периода сюда не входит — он вычисляется на лету от
     * [installedAtOdometer] до нынешнего пробега машины. Иначе значение
     * пришлось бы обновлять при каждой поездке.
     */
    val totalKm: Int = 0,

    /** Пробег машины в момент установки. null, когда комплект снят */
    val installedAtOdometer: Int? = null,
    val isInstalled: Boolean = false,

    /** Где лежит снятый комплект: «гараж», «балкон», «сезонное хранение у дилера» */
    val storageLocation: String? = null,
    val notes: String? = null,
    val photoUri: String? = null,

    /**
     * Ожидаемый ресурс в километрах. Пусто — значит не оцениваем износ:
     * выдуманная цифра здесь хуже её отсутствия.
     */
    val expectedLifeKm: Int? = null,

    val syncedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Полный пробег комплекта с учётом текущей установки.
     *
     * @param currentOdometer нынешний пробег машины
     */
    fun kmWith(currentOdometer: Int): Int {
        val installed = installedAtOdometer ?: return totalKm
        if (!isInstalled) return totalKm
        // Одометр мог откатиться назад из-за правки задним числом — тогда
        // текущий период считаем нулевым, а не отрицательным
        val current = (currentOdometer - installed).coerceAtLeast(0)
        return totalKm + current
    }

    /**
     * Износ долей от ожидаемого ресурса, либо null.
     *
     * null означает «ресурс не задан» — это не то же самое, что «новые».
     */
    fun wearFraction(currentOdometer: Int): Float? {
        val life = expectedLifeKm ?: return null
        if (life <= 0) return null
        return (kmWith(currentOdometer).toFloat() / life).coerceIn(0f, 1f)
    }
}

/** Сезонность комплекта */
enum class TyreSeason(@StringRes val labelRes: Int) {
    SUMMER(R.string.tyres_season_summer),
    WINTER(R.string.tyres_season_winter),
    ALL_SEASON(R.string.tyres_season_all)
}
