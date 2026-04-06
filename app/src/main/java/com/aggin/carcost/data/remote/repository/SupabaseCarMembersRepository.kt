package com.aggin.carcost.data.remote.repository

import android.util.Log
import com.aggin.carcost.data.local.database.entities.CarMember
import com.aggin.carcost.data.local.database.entities.MemberRole
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.from
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

    /** Create an invitation record and return the invite token. */
    suspend fun createInvitation(
        carId: String,
        invitedEmail: String,
        role: MemberRole
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = UUID.randomUUID().toString()
            val dto = CarInvitationDto(
                id = UUID.randomUUID().toString(),
                carId = carId,
                invitedEmail = invitedEmail,
                token = token,
                role = role.name,
                expiresAt = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L // 7 days
            )
            supabase.from("car_invitations").insert(dto)
            Log.d(TAG, "Invitation created token=$token")
            Result.success(token)
        } catch (e: Exception) {
            Log.e(TAG, "createInvitation failed", e)
            Result.failure(e)
        }
    }

    /** Accept an invitation by token. Adds current user to car_members. */
    suspend fun acceptInvitation(token: String): Result<CarMember> = withContext(Dispatchers.IO) {
        try {
            val userId = auth.getUserId() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val email = auth.getCurrentUserEmail() ?: ""

            // Find the invitation
            val invitations = supabase.from("car_invitations")
                .select { filter { eq("token", token) } }
                .decodeList<CarInvitationDto>()

            val inv = invitations.firstOrNull()
                ?: return@withContext Result.failure(Exception("Приглашение не найдено"))

            if (inv.acceptedAt != null)
                return@withContext Result.failure(Exception("Приглашение уже использовано"))

            if (System.currentTimeMillis() > inv.expiresAt)
                return@withContext Result.failure(Exception("Срок приглашения истёк"))

            // Add to car_members
            val member = CarMemberDto(
                id = UUID.randomUUID().toString(),
                carId = inv.carId,
                userId = userId,
                email = email,
                role = inv.role
            )
            supabase.from("car_members").upsert(member, onConflict = "car_id,user_id")

            // Mark invitation as accepted
            supabase.from("car_invitations")
                .update(mapOf("accepted_at" to System.currentTimeMillis())) {
                    filter { eq("token", token) }
                }

            val result = CarMember(
                id = member.id,
                carId = member.carId,
                userId = userId,
                email = email,
                role = MemberRole.valueOf(inv.role)
            )
            Log.d(TAG, "Invitation accepted, joined car ${inv.carId} as ${inv.role}")
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "acceptInvitation failed", e)
            Result.failure(e)
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
