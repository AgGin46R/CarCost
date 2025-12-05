package com.aggin.carcost.data.remote.repository

import com.aggin.carcost.supabase
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Репозиторий для работы с аутентификацией через Supabase
 */
class SupabaseAuthRepository {

    /**
     * Регистрация нового пользователя
     */
    suspend fun signUp(email: String, password: String): Result<UserInfo> = withContext(Dispatchers.IO) {
        try {
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }

            val user = supabase.auth.currentUserOrNull()
                ?: return@withContext Result.failure(Exception("Пользователь не найден после регистрации"))

            // ✅ Используем buildJsonObject для создания профиля
            val profile = buildJsonObject {
                put("id", user.id)
                put("email", email)
                put("display_name", null as String?)
                put("photo_url", null as String?)
                put("created_at", System.currentTimeMillis())
                put("last_login_at", System.currentTimeMillis())
            }

            supabase.from("users").insert(profile)

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Вход пользователя
     */
    suspend fun signIn(email: String, password: String): Result<UserInfo> = withContext(Dispatchers.IO) {
        try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }

            val user = supabase.auth.currentUserOrNull()
                ?: return@withContext Result.failure(Exception("Пользователь не найден"))

            // ✅ Используем buildJsonObject для обновления
            val update = buildJsonObject {
                put("last_login_at", System.currentTimeMillis())
            }

            supabase.from("users").update(update) {
                filter {
                    eq("id", user.id)
                }
            }

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Выход пользователя
     */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            supabase.auth.signOut()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Сброс пароля
     */
    suspend fun resetPassword(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.auth.resetPasswordForEmail(email)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Проверка, залогинен ли пользователь
     */
    fun isUserLoggedIn(): Boolean {
        return supabase.auth.currentUserOrNull() != null
    }

    /**
     * Получение ID текущего пользователя
     */
    fun getUserId(): String? {
        return supabase.auth.currentUserOrNull()?.id
    }

    /**
     * Получение информации о текущем пользователе
     */
    suspend fun getCurrentUser(): Result<UserInfo> = withContext(Dispatchers.IO) {
        try {
            val user = supabase.auth.currentUserOrNull()
                ?: return@withContext Result.failure(Exception("Пользователь не найден"))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ✅ ДОБАВЛЕНО: Получение email текущего пользователя
     */
    fun getCurrentUserEmail(): String? {
        return supabase.auth.currentUserOrNull()?.email
    }

    /**
     * ✅ ДОБАВЛЕНО: Получение имени текущего пользователя
     */
    fun getCurrentUserDisplayName(): String? {
        return supabase.auth.currentUserOrNull()?.userMetadata?.get("display_name") as? String
    }

    /**
     * Обновление профиля пользователя
     */
    suspend fun updateProfile(displayName: String?, photoUrl: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = getUserId()
                ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

            // ✅ Используем buildJsonObject для обновления профиля
            val update = buildJsonObject {
                displayName?.let { put("display_name", it) }
                photoUrl?.let { put("photo_url", it) }
            }

            supabase.from("users").update(update) {
                filter {
                    eq("id", userId)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Обновление email пользователя
     */
    suspend fun updateEmail(newEmail: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.auth.updateUser {
                email = newEmail
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Обновление пароля пользователя
     */
    suspend fun updatePassword(newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.auth.updateUser {
                password = newPassword
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}