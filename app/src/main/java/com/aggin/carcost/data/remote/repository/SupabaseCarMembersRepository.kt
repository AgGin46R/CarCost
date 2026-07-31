package com.aggin.carcost.data.remote.repository

import android.util.Log
import com.aggin.carcost.data.local.database.entities.CarMember
import com.aggin.carcost.data.local.database.entities.MemberRole
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CarMemberDto(
    val id: String,
    @SerialName("car_id") val carId: String,
    @SerialName("user_id") val userId: String,
    val email: String,
    val role: String,
    @SerialName("joined_at") val joinedAt: Long = System.currentTimeMillis()
)

@Serializable
data class CarInvitationDto(
    val id: String,
    @SerialName("car_id") val carId: String,
    @SerialName("invited_email") val invitedEmail: String,
    val token: String,
    val role: String,
    @SerialName("expires_at") val expiresAt: Long,
    @SerialName("accepted_at") val acceptedAt: Long? = null
)

class SupabaseCarMembersRepository(private val auth: SupabaseAuthRepository) {

    private val TAG = "SupabaseCarMembers"

    /** Ensure current user is registered as OWNER for this car. Safe to call multiple times. */
    suspend fun ensureOwner(carId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = auth.getUserId() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val email = auth.getCurrentUserEmail() ?: ""

            // Check if already registered to avoid any duplicate
            val existing = supabase.from("car_members")
                .select { filter { eq("car_id", carId); eq("user_id", userId) } }
                .decodeList<CarMemberDto>()

            if (existing.isNotEmpty()) {
                Log.d(TAG, "Owner already registered for car $carId, skipping")
                return@withContext Result.success(Unit)
            }

            val dto = CarMemberDto(
                id = UUID.randomUUID().toString(),
                carId = carId,
                userId = userId,
                email = email,
                role = MemberRole.OWNER.name
            )
            // onConflict = "car_id,user_id" ensures DB-level duplicate protection too
            supabase.from("car_members").upsert(dto, onConflict = "car_id,user_id")
            Log.d(TAG, "Owner registered for car $carId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "ensureOwner failed", e)
            Result.failure(e)
        }
    }

    companion object InviteCode {
        /**
         * Без 0/O, 1/I/L и прочих пар, которые путают при диктовке и наборе.
         * 32 символа на позицию, 8 позиций — порядка 10^12 комбинаций.
         */
        private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
        private const val LENGTH = 8

        fun generate(): String = (1..LENGTH)
            .map { ALPHABET.random() }
            .joinToString("")

        /** «K7M2-P9XQ» — так код читается и диктуется заметно легче */
        fun format(code: String): String =
            if (code.length == LENGTH) "${code.take(4)}-${code.drop(4)}" else code
    }

    /**
     * Создаёт приглашение и возвращает короткий код.
     *
     * Раньше токеном был UUID: он годился для ссылки, но ссылку `carcost://`
     * мессенджеры не делают кликабельной, а 36 символов руками не ввести.
     * Восьми символов из однозначного алфавита хватает — сервер принимает их
     * в любом регистре и с любыми разделителями.
     */
    suspend fun createInvitation(
        carId: String,
        invitedEmail: String,
        role: MemberRole
    ): Result<String> = withContext(Dispatchers.IO) {
        // Столбец token уникален: коллизия провалит вставку, просто пробуем снова
        repeat(5) { attempt ->
            val code = InviteCode.generate()
            try {
                val dto = CarInvitationDto(
                    id = UUID.randomUUID().toString(),
                    carId = carId,
                    invitedEmail = invitedEmail,
                    token = code,
                    role = role.name,
                    expiresAt = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L // 7 дней
                )
                supabase.from("car_invitations").insert(dto)
                Log.d(TAG, "Invitation created, attempt ${attempt + 1}")
                return@withContext Result.success(code)
            } catch (e: Exception) {
                val duplicate = e.message?.contains("23505") == true ||
                    e.message?.contains("duplicate key") == true
                if (!duplicate) {
                    Log.e(TAG, "createInvitation failed", e)
                    return@withContext Result.failure(e)
                }
                Log.w(TAG, "Код уже занят, генерирую новый")
            }
        }
        Result.failure(Exception("Не удалось создать код приглашения, попробуйте ещё раз"))
    }

    @Serializable
    private data class AcceptInvitationParams(@SerialName("p_token") val token: String)

    @Serializable
    private data class AcceptInvitationResult(
        @SerialName("car_id") val carId: String,
        val role: String
    )

    /**
     * Принимает приглашение по токену через функцию accept_invitation.
     *
     * Раньше это делалось тремя запросами из клиента, и под RLS оно не работало
     * для приглашений по ссылке: политика на car_invitations отдаёт строки только
     * при `invited_email = auth.email()`, а у ссылки email пустой — поиск по токену
     * возвращал пустоту и пользователь видел «Приглашение не найдено».
     *
     * Функция на сервере объявлена SECURITY DEFINER: там токен и есть удостоверение,
     * поэтому присоединиться может и тот, кого пригласили ссылкой, и вошедший через
     * VK без почты.
     */
    suspend fun acceptInvitation(token: String): Result<CarMember> = withContext(Dispatchers.IO) {
        try {
            val userId = auth.getUserId() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val email = auth.getCurrentUserEmail() ?: ""

            val response = supabase.postgrest
                .rpc("accept_invitation", AcceptInvitationParams(token))
                .decodeAs<AcceptInvitationResult>()

            val result = CarMember(
                id = UUID.randomUUID().toString(),
                carId = response.carId,
                userId = userId,
                email = email,
                role = MemberRole.valueOf(response.role)
            )
            Log.d(TAG, "Invitation accepted, joined car ${response.carId} as ${response.role}")
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "acceptInvitation failed", e)
            // Функция бросает конкретные метки, чтобы пользователь видел причину,
            // а не общий текст «что-то пошло не так»
            val message = e.message.orEmpty()
            val readable = when {
                "invitation_not_found" in message -> "Приглашение не найдено"
                "invitation_already_used" in message -> "Приглашение уже использовано"
                "invitation_expired" in message -> "Срок приглашения истёк"
                "not_authenticated" in message -> "Сначала войдите в аккаунт"
                else -> "Не удалось принять приглашение"
            }
            Result.failure(Exception(readable))
        }
    }

    /** Sync members from Supabase into local Room list. */
    suspend fun getMembersByCarId(carId: String): Result<List<CarMember>> = withContext(Dispatchers.IO) {
        try {
            val dtos = supabase.from("car_members")
                .select { filter { eq("car_id", carId) } }
                .decodeList<CarMemberDto>()
            Result.success(dtos.map {
                CarMember(
                    id = it.id,
                    carId = it.carId,
                    userId = it.userId,
                    email = it.email,
                    role = MemberRole.valueOf(it.role),
                    joinedAt = it.joinedAt
                )
            })
        } catch (e: Exception) {
            Log.e(TAG, "getMembersByCarId failed", e)
            Result.failure(e)
        }
    }

    /** Get pending invitations addressed to the current user's email. */
    suspend fun getPendingInvitationsForMe(): Result<List<CarInvitationDto>> = withContext(Dispatchers.IO) {
        try {
            val email = auth.getCurrentUserEmail() ?: return@withContext Result.success(emptyList())
            val now = System.currentTimeMillis()
            val invitations = supabase.from("car_invitations")
                .select {
                    filter {
                        eq("invited_email", email)
                        gt("expires_at", now)
                    }
                }
                .decodeList<CarInvitationDto>()
                .filter { it.acceptedAt == null }
            Result.success(invitations)
        } catch (e: Exception) {
            Log.e(TAG, "getPendingInvitationsForMe failed", e)
            Result.failure(e)
        }
    }

    /** Returns all car_ids where the current user is a member (owner or driver). */
    suspend fun getMyMemberCarIds(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val userId = auth.getUserId() ?: return@withContext Result.success(emptyList())
            val dtos = supabase.from("car_members")
                .select { filter { eq("user_id", userId) } }
                .decodeList<CarMemberDto>()
            Result.success(dtos.map { it.carId })
        } catch (e: Exception) {
            Log.e(TAG, "getMyMemberCarIds failed", e)
            Result.failure(e)
        }
    }

    /** Returns all memberships (with roles) for the current user across all cars. */
    suspend fun getMyMemberships(): Result<List<CarMember>> = withContext(Dispatchers.IO) {
        try {
            val userId = auth.getUserId() ?: return@withContext Result.success(emptyList())
            val dtos = supabase.from("car_members")
                .select { filter { eq("user_id", userId) } }
                .decodeList<CarMemberDto>()
            Result.success(dtos.map {
                CarMember(
                    id = it.id,
                    carId = it.carId,
                    userId = it.userId,
                    email = it.email,
                    role = MemberRole.valueOf(it.role),
                    joinedAt = it.joinedAt
                )
            })
        } catch (e: Exception) {
            Log.e(TAG, "getMyMemberships failed", e)
            Result.failure(e)
        }
    }

    /** Get the current user's role for a specific car directly from Supabase. */
    suspend fun getMyRoleForCar(carId: String): Result<MemberRole?> = withContext(Dispatchers.IO) {
        try {
            val userId = auth.getUserId() ?: return@withContext Result.success(null)
            val dtos = supabase.from("car_members")
                .select { filter { eq("car_id", carId); eq("user_id", userId) } }
                .decodeList<CarMemberDto>()
            val role = dtos.firstOrNull()?.let { MemberRole.valueOf(it.role) }
            Result.success(role)
        } catch (e: Exception) {
            Log.e(TAG, "getMyRoleForCar failed", e)
            Result.failure(e)
        }
    }

    /** Remove a member from Supabase. */
    suspend fun removeMember(carId: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.from("car_members").delete {
                filter {
                    eq("car_id", carId)
                    eq("user_id", userId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "removeMember failed", e)
            Result.failure(e)
        }
    }
}
