package com.aggin.carcost.data.local.database.dao

import androidx.room.*
import com.aggin.carcost.data.local.database.entities.CarMember
import com.aggin.carcost.data.local.database.entities.MemberRole
import kotlinx.coroutines.flow.Flow

@Dao
interface CarMemberDao {
    @Query("SELECT * FROM car_members WHERE carId = :carId ORDER BY joinedAt ASC")
    fun getMembersByCarId(carId: String): Flow<List<CarMember>>

    @Query("SELECT role FROM car_members WHERE carId = :carId AND userId = :userId LIMIT 1")
    suspend fun getRoleForUser(carId: String, userId: String): MemberRole?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(member: CarMember)

    @Delete
    suspend fun delete(member: CarMember)

    @Query("DELETE FROM car_members WHERE carId = :carId AND userId = :userId")
    suspend fun removeMember(carId: String, userId: String)

    @Query("DELETE FROM car_members WHERE carId = :carId AND userId LIKE 'pending_%'")
    suspend fun deletePendingMembers(carId: String)
}
