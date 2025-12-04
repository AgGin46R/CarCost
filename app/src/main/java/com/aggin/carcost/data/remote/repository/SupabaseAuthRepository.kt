package com.aggin.carcost.data.remote.repository

import com.aggin.carcost.supabase
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

            // Создаем профиль пользователя в таблице users
            supabase.from("users").insert(
                mapOf(
                    "id" to user.id,
                    "email" to email,
                    "created_at" to System.currentTimeMillis(),
                    "last_login_at" to System.currentTimeMillis()
                )
            )

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

            // Обновляем время последнего входа
            supabase.from("users").update(
                mapOf("last_login_at" to System.currentTimeMillis())
            ) {
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
            // Логируем ошибку, но не бросаем исключение
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
     * Обновление профиля пользователя
     */
    suspend fun updateProfile(displayName: String?, photoUrl: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = getUserId()
                ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

            val updates = mutableMapOf<String, Any?>()
            displayName?.let { updates["display_name"] = it }
            photoUrl?.let { updates["photo_url"] = it }

            if (updates.isNotEmpty()) {
                supabase.from("users").update(updates) {
                    filter {
                        eq("id", userId)
                    }
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