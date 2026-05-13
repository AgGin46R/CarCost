package com.aggin.carcost.data.remote.repository

import com.aggin.carcost.data.local.database.entities.GpsTrip
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class GpsTripDto(
    val id: String,
    @SerialName("car_id") val carId: String,
    @SerialName("start_time") val startTime: Long,
    @SerialName("end_time") val endTime: Long? = null,
    @SerialName("distance_km") val distanceKm: Double = 0.0,
    @SerialName("route_json") val routeJson: String? = null,
    @SerialName("avg_speed_kmh") val avgSpeedKmh: Double? = null
)

private fun GpsTrip.toDto() = GpsTripDto(
    id = id, carId = carId, startTime = startTime,
    endTime = endTime, distanceKm = distanceKm,
    routeJson = routeJson, avgSpeedKmh = avgSpeedKmh
)

private fun GpsTripDto.toEntity() = GpsTrip(
    id = id, carId = carId, startTime = startTime,
    endTime = endTime, distanceKm = distanceKm,
    routeJson = routeJson, avgSpeedKmh = avgSpeedKmh
)

class SupabaseGpsTripRepository(private val auth: SupabaseAuthRepository) {

    suspend fun upsert(trip: GpsTrip): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { supabase.from("gps_trips").upsert(trip.toDto()); Unit }
    }

    suspend fun getByCarId(carId: String): Result<List<GpsTrip>> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("gps_trips")
                .select { filter { eq("car_id", carId) } }
                .decodeList<GpsTripDto>()
                .map { it.toEntity() }
        }
    }

    suspend fun deleteById(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { supabase.from("gps_trips").delete { filter { eq("id", id) } }; Unit }
    }
}
