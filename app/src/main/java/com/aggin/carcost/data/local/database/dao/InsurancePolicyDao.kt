package com.aggin.carcost.data.local.database.dao

import androidx.room.*
import com.aggin.carcost.data.local.database.entities.InsurancePolicy
import kotlinx.coroutines.flow.Flow

@Dao
interface InsurancePolicyDao {

    @Query("SELECT * FROM insurance_policies WHERE carId = :carId ORDER BY endDate ASC")
    fun getPoliciesForCar(carId: String): Flow<List<InsurancePolicy>>

    @Query("SELECT * FROM insurance_policies WHERE carId = :carId ORDER BY endDate ASC")
    suspend fun getPoliciesForCarSync(carId: String): List<InsurancePolicy>

    @Query("SELECT * FROM insurance_policies WHERE carId = :carId AND endDate >= :now ORDER BY endDate ASC")
    fun getActivePoliciesForCar(carId: String, now: Long = System.currentTimeMillis()): Flow<List<InsurancePolicy>>

    @Query("SELECT * FROM insurance_policies WHERE endDate >= :from AND endDate <= :to")
    suspend fun getPoliciesExpiringBetween(from: Long, to: Long): List<InsurancePolicy>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(policy: InsurancePolicy)

    @Update
    suspend fun update(policy: InsurancePolicy)

    @Delete
    suspend fun delete(policy: InsurancePolicy)

    @Query("SELECT * FROM insurance_policies WHERE id = :id")
    suspend fun getById(id: String): InsurancePolicy?
}
