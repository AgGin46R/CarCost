package com.aggin.carcost.data.remote.api

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class NhtsaVinResponse(
    @SerializedName("Results") val results: List<NhtsaVinResult> = emptyList()
)

data class NhtsaVinResult(
    @SerializedName("Value") val value: String? = null,
    @SerializedName("ValueId") val valueId: String? = null,
    @SerializedName("Variable") val variable: String? = null,
    @SerializedName("VariableId") val variableId: Int? = null
)

interface NhtsaApiService {
    @GET("vehicles/DecodeVinValues/{vin}")
    suspend fun decodeVin(
        @Path("vin") vin: String,
        @Query("format") format: String = "json"
    ): NhtsaVinResponse

    companion object {
        private const val BASE_URL = "https://vpic.nhtsa.dot.gov/api/"

        fun create(): NhtsaApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(NhtsaApiService::class.java)
        }
    }
}

// Helper to extract useful fields from NHTSA response
fun NhtsaVinResponse.toVinInfo(): Map<String, String> {
    val result = results.firstOrNull() ?: return emptyMap()
    return mapOf(
        "make" to (results.find { it.variable == "Make" }?.value ?: ""),
        "model" to (results.find { it.variable == "Model" }?.value ?: ""),
        "year" to (results.find { it.variable == "Model Year" }?.value ?: ""),
        "engine" to (results.find { it.variable == "Displacement (L)" }?.value?.let { "$it L" } ?: ""),
        "fuelType" to (results.find { it.variable == "Fuel Type - Primary" }?.value ?: ""),
        "country" to (results.find { it.variable == "Plant Country" }?.value ?: ""),
        "vehicleType" to (results.find { it.variable == "Vehicle Type" }?.value ?: "")
    ).filter { it.value.isNotEmpty() }
}
