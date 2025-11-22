package com.aggin.carcost.data.repository

import com.aggin.carcost.data.local.database.dao.UserDao
import com.aggin.carcost.data.local.database.entities.User
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.sync.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Унифицированный репозиторий авторизации
 * Использует Supabase вместо Firebase
 */
class UnifiedAuthRepository(
    private val supabaseAuth: AuthRepository,
    private val userDao: UserDao,
    private val syncRepository: SyncRepository? = null
) {

    val currentUser: Flow<User?> = userDao.getCurrentUser()

    fun isUserLoggedIn(): Boolean {
        return supabaseAuth.isUserLoggedIn()
    }

    fun getCurrentUserId(): String? {
        return supabaseAuth.getUserId()
    }

    suspend fun signUp(
        email: String,
        password: String,
        displayName: String
    ): Result<User> {
        return try {
            // Регистрация в Supabase
            val result = supabaseAuth.signUp(email, password)

            result.fold(
                onSuccess = { userInfo ->
                    // Сохраняем пользователя локально
                    val user = User(
                        uid = userInfo.id,
                        email = email,
                        displayName = displayName,
                        createdAt = System.currentTimeMillis()
                    )

                    userDao.insertUser(user)

                    Result.success(user)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(email: String, password: String): Result<User> {
        return try {
            // Вход через Supabase
            val result = supabaseAuth.signIn(email, password)

            result.fold(
                onSuccess = { userInfo ->
                    // Сохраняем пользователя локально
                    val user = User(
                        uid = userInfo.id,
                        email = userInfo.email ?: email,
                        displayName = userInfo.userMetadata?.get("display_name") as? String,
                        lastLoginAt = System.currentTimeMillis()
                    )

                    userDao.insertUser(user)

                    // Синхронизируем данные
                    syncRepository?.fullSync()

                    Result.success(user)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        supabaseAuth.signOut()
        userDao.deleteAllUsers()
        syncRepository?.clearLocalData()
    }

    suspend fun resetPassword(email: String): Result<Unit> {
        return supabaseAuth.resetPassword(email)
    }
}