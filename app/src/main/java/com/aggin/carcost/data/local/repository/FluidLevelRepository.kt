package com.aggin.carcost.data.local.repository

import com.aggin.carcost.data.local.database.dao.FluidLevelDao
import com.aggin.carcost.data.local.database.entities.FluidLevel
import com.aggin.carcost.data.local.database.entities.FluidType
import kotlinx.coroutines.flow.Flow

class FluidLevelRepository(private val dao: FluidLevelDao) {

    fun getFluidLevelsByCarId(carId: String): Flow<List<FluidLevel>> =
        dao.getLatestFluidLevels(carId)

    suspend fun getFluidLevelsByCarIdSync(carId: String): List<FluidLevel> =
        dao.getLatestFluidLevelsSync(carId)

    suspend fun getLatestFluidLevel(carId: String, type: FluidType): FluidLevel? =
        dao.getLatestFluidLevel(carId, type)

    suspend fun upsert(fluidLevel: FluidLevel) =
        dao.insert(fluidLevel)

    suspend fun deleteById(id: String) =
        dao.deleteById(id)

    suspend fun deleteByCarId(carId: String) =
        dao.deleteByCarId(carId)
}
