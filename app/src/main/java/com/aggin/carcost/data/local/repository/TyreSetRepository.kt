package com.aggin.carcost.data.local.repository

import com.aggin.carcost.data.local.database.dao.CarDao
import com.aggin.carcost.data.local.database.dao.TyreSetDao
import com.aggin.carcost.data.local.database.entities.TyreSet
import kotlinx.coroutines.flow.Flow

/**
 * Комплекты шин: хранение и учёт пробега при установке и снятии.
 *
 * Вся арифметика пробега собрана здесь, а не размазана по экрану: поставить и
 * снять комплект можно из разных мест, и повтор этой логики рано или поздно
 * разошёлся бы.
 */
class TyreSetRepository(
    private val tyreSetDao: TyreSetDao,
    private val carDao: CarDao
) {

    fun getByCarId(carId: String): Flow<List<TyreSet>> = tyreSetDao.getByCarId(carId)

    suspend fun getById(id: String): TyreSet? = tyreSetDao.getById(id)

    suspend fun save(tyreSet: TyreSet) {
        tyreSetDao.upsert(tyreSet.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(tyreSet: TyreSet) = tyreSetDao.delete(tyreSet)

    suspend fun markSynced(id: String) = tyreSetDao.markSynced(id)

    /** Запись, пришедшая с сервера, подтверждена им по определению */
    suspend fun saveFromServer(tyreSet: TyreSet) {
        tyreSetDao.upsert(tyreSet.copy(syncedAt = System.currentTimeMillis()))
    }

    /**
     * Ставит комплект на машину.
     *
     * Прежний комплект снимается сам: на машине не бывает двух комплектов
     * одновременно, и заставлять человека сначала снимать старый — лишний шаг,
     * о котором он всё равно забудет, а пробег после этого разъедется.
     *
     * @return false, если машины нет или комплект уже стоит
     */
    suspend fun install(tyreSetId: String): Boolean {
        val set = tyreSetDao.getById(tyreSetId) ?: return false
        if (set.isInstalled) return false
        val car = carDao.getCarById(set.carId) ?: return false

        tyreSetDao.getInstalled(set.carId)?.let { previous ->
            if (previous.id != set.id) uninstall(previous.id)
        }

        save(
            set.copy(
                isInstalled = true,
                installedAtOdometer = car.currentOdometer
            )
        )
        return true
    }

    /**
     * Снимает комплект, добавляя пройденное к накопленному пробегу.
     *
     * @return false, если комплект не стоял
     */
    suspend fun uninstall(tyreSetId: String): Boolean {
        val set = tyreSetDao.getById(tyreSetId) ?: return false
        if (!set.isInstalled) return false
        val car = carDao.getCarById(set.carId) ?: return false

        // Пробег за этот период. Отрицательным быть не должен: одометр могли
        // поправить задним числом, и тогда честнее засчитать ноль, чем
        // вычитать из накопленного
        val driven = (car.currentOdometer - (set.installedAtOdometer ?: car.currentOdometer))
            .coerceAtLeast(0)

        save(
            set.copy(
                isInstalled = false,
                installedAtOdometer = null,
                totalKm = set.totalKm + driven
            )
        )
        return true
    }
}
