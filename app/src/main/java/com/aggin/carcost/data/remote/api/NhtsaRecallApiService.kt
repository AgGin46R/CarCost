package com.aggin.carcost.data.remote.api

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class NhtsaRecallResponse(
    @SerializedName("results") val results: List<NhtsaRecall>? = null,
    @SerializedName("Count") val count: Int = 0
)

data class NhtsaRecall(
    @SerializedName("NHTSACampaignNumber") val campaignNumber: String? = null,
    @SerializedName("Component") val component: String? = null,
    @SerializedName("Summary") val summary: String? = null,
    @SerializedName("Consequence") val consequence: String? = null,
    @SerializedName("Remedy") val remedy: String? = null,
    @SerializedName("ReportReceivedDate") val reportDate: String? = null,
    @SerializedName("MfgCampaignNumber") val mfgCampaignNumber: String? = null
)

interface NhtsaRecallApiService {
    @GET("complaints/complaintsByVehicle")
    suspend fun getRecalls(
        @Query("make") make: String,
        @Query("model") model: String,
        @Query("modelYear") year: Int,
        @Query("format") format: String = "json"
    ): NhtsaRecallResponse

    companion object {
        private const val BASE_URL = "https://api.nhtsa.gov/"

        fun create(): NhtsaRecallApiService {
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
                .create(NhtsaRecallApiService::class.java)
        }
    }
}
