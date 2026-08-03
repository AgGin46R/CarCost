package com.aggin.carcost.data.remote.repository

import android.util.Log
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "SupabaseAccount"

/**
 * Что именно исчезнет вместе с аккаунтом.
 *
 * Нужно ровно затем, чтобы предупреждение перед удалением называло числа, а не
 * общие слова. «Удалятся 4 автомобиля и 18 расходов, 2 человека потеряют доступ
 * к 3 общим машинам» — это то, на что можно осознанно нажать «Удалить»;
 * «все ваши данные будут удалены» — нет.
 */
@Serializable
data class AccountDeletionSummary(
    @SerialName("owned_cars") val ownedCars: Int = 0,
    @SerialName("expenses") val expenses: Int = 0,
    /** Сколько из моих машин используют другие люди */
    @SerialName("shared_cars") val sharedCars: Int = 0,
    /** Сколько человек, кроме меня, лишатся истории по этим машинам */
    @SerialName("other_participants") val otherParticipants: Int = 0
) {
    val touchesOtherPeople: Boolean get() = otherParticipants > 0
}

class SupabaseAccountRepository {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Считается на сервере функцией `account_deletion_summary`: участников чужих
     * машин клиент через RLS не видит, а тянуть id всех расходов ради одного
     * числа бессмысленно.
     */
    suspend fun getDeletionSummary(): Result<AccountDeletionSummary> = withContext(Dispatchers.IO) {
        try {
            val raw = supabase.postgrest.rpc("account_deletion_summary").data
            Result.success(json.decodeFromString(AccountDeletionSummary.serializer(), raw))
        } catch (e: Exception) {
            Log.e(TAG, "account_deletion_summary failed", e)
            Result.failure(e)
        }
    }
}
