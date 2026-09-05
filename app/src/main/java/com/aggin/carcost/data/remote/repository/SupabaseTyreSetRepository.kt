package com.aggin.carcost.data.remote.repository

import com.aggin.carcost.data.local.database.entities.TyreSeason
import com.aggin.carcost.data.local.database.entities.TyreSet
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class TyreSetDto(
    val id: String,
    @SerialName("car_id") val carId: String,
    val name: String,
    val season: String,
    val size: String? = null,
    @SerialName("purchase_date") val purchaseDate: Long? = null,
    @SerialName("purchase_price") val purchasePrice: Double? = null,
    @SerialName("total_km") val totalKm: Int = 0,
    @SerialName("installed_at_odometer") val installedAtOdometer: Int? = null,
    @SerialName("is_installed") val isInstalled: Boolean = false,
    @SerialName("storage_location") val storageLocation: String? = null,
    val notes: String? = null,
    @SerialName("photo_uri") val photoUri: String? = null,
    @SerialName("expected_life_km") val expectedLifeKm: Int? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long
)

private fun TyreSet.toDto() = TyreSetDto(
    id = id,
    carId = carId,
    name = name,
    season = season.name,
    size = size,
    purchaseDate = purchaseDate,
    purchasePrice = purchasePrice,
    totalKm = totalKm,
    installedAtOdometer = installedAtOdometer,
    isInstalled = isInstalled,
    storageLocation = storageLocation,
    notes = notes,
    photoUri = photoUri,
    expectedLifeKm = expectedLifeKm,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun TyreSetDto.toEntity() = TyreSet(
    id = id,
    carId = carId,
    name = name,
    // Незнакомое значение сезона не должно ронять загрузку всего списка —
    // приложение на сервере может быть новее установленного
    season = try { TyreSeason.valueOf(season) } catch (e: Exception) { TyreSeason.SUMMER },
    size = size,
    purchaseDate = purchaseDate,
    purchasePrice = purchasePrice,
    totalKm = totalKm,
    installedAtOdometer = installedAtOdometer,
    isInstalled = isInstalled,
    storageLocation = storageLocation,
    notes = notes,
    photoUri = photoUri,
    expectedLifeKm = expectedLifeKm,
    createdAt = createdAt,
    updatedAt = updatedAt
)

class SupabaseTyreSetRepository(private val auth: SupabaseAuthRepository) {

    suspend fun upsert(tyreSet: TyreSet): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("tyre_sets").upsert(tyreSet.toDto())
            Unit
        }
    }

    suspend fun getByCarId(carId: String): Result<List<TyreSet>> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("tyre_sets")
                .select { filter { eq("car_id", carId) } }
                .decodeList<TyreSetDto>()
                .map { it.toEntity() }
        }
    }

    suspend fun deleteById(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("tyre_sets").delete { filter { eq("id", id) } }
            Unit
        }
    }
}
