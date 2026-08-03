package com.aggin.carcost.data.remote.repository

import android.util.Log
import com.aggin.carcost.supabase
import io.github.jan.supabase.gotrue.OtpType
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class SupabaseUserDto(
    val id: String = "",
    val email: String = "",
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null
)

/** Строка vk_identities — привязка ВКонтакте к аккаунту */
@Serializable
data class VkIdentity(
    @SerialName("vk_user_id") val vkUserId: Long,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
) {
    val displayName: String get() = "${firstName.orEmpty()} ${lastName.orEmpty()}".trim()
}

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
     * Вход через ВКонтакте.
     *
     * У GoTrue нет провайдера VK, поэтому токен VK проверяется на сервере —
     * Edge Function `vk-auth` возвращает hashed_token магической ссылки,
     * а GoTrue по нему создаёт и сохраняет сессию (verifyEmailOtp внутри
     * сам вызывает importSession).
     *
     * @param hashedToken значение hashed_token из ответа Edge Function
     */
    suspend fun signInWithVk(hashedToken: String): Result<UserInfo> = withContext(Dispatchers.IO) {
        try {
            supabase.auth.verifyEmailOtp(
                type = OtpType.Email.MAGIC_LINK,
                tokenHash = hashedToken
            )

            val user = supabase.auth.currentUserOrNull()
                ?: return@withContext Result.failure(Exception("Пользователь не найден"))

            // Имя и фото сюда НЕ пишем намеренно.
            //
            // Раньше при каждом входе через ВК сюда уходили display_name и
            // photo_url из метаданных — то есть значения из ВКонтакте. Человек
            // менял фото в CarCost, а следующий вход через ВК возвращал вкшный
            // аватар: выглядело как «приложение не запоминает фото».
            //
            // Этими двумя полями теперь распоряжается триггер
            // sync_vk_profile_to_user: он подставляет данные из ВК, только пока
            // пользователь не выбрал своё. Здесь остаётся отметка о входе.
            val profile = buildJsonObject {
                put("id", user.id)
                put("email", user.email ?: "")
                put("last_login_at", System.currentTimeMillis())
            }

            try {
                supabase.from("users").upsert(profile)
            } catch (e: Exception) {
                Log.w("SupabaseAuth", "Profile upsert failed (non-critical): ${e.message}")
            }

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Аккаунт **заведён** через ВКонтакте.
     *
     * Это не то же самое, что «к аккаунту привязан VK»: у заведённого через VK
     * нет ни пароля, ни другого способа входа, поэтому отвязка означала бы
     * потерю доступа навсегда. Привязавший VK к своему email-аккаунту — может.
     */
    fun isVkAccount(): Boolean {
        return supabase.auth.currentUserOrNull()?.userMetadata?.get("vk_id") != null
    }

    /**
     * У аккаунта есть вход по паролю.
     *
     * У заведённых через ВКонтакте пароля нет, поэтому «сменить пароль»
     * им показывать нельзя: проверить старый пароль невозможно в принципе.
     *
     * Одного `provider` недостаточно: у созданных через vk-auth GoTrue проставляет
     * provider = "email", потому что пользователь создаётся с email — это проверено
     * на живых данных. Поэтому VK отсекается отдельно, по vk_id в user_metadata.
     */
    fun hasPasswordLogin(): Boolean {
        val user = supabase.auth.currentUserOrNull() ?: return false
        if (isVkAccount()) return false
        val provider = user.appMetadata?.get("provider")?.toString()?.trim('"')
        return provider == "email"
    }

    /**
     * Привязка ВКонтакте текущего пользователя, если она есть.
     * Читается напрямую из vk_identities — RLS отдаёт только свою строку.
     */
    suspend fun getVkLink(): VkIdentity? = withContext(Dispatchers.IO) {
        val userId = getUserId() ?: return@withContext null
        try {
            supabase.from("vk_identities")
                .select { filter { eq("user_id", userId) } }
                .decodeSingleOrNull<VkIdentity>()
        } catch (e: Exception) {
            Log.w("SupabaseAuth", "Failed to read vk link: ${e.message}")
            null
        }
    }

    /**
     * Отвязывает ВКонтакте. Разрешено политикой vk_identities_self_delete.
     *
     * Вызывающий обязан убедиться, что у пользователя останется способ войти —
     * см. [isVkAccount].
     */
    suspend fun unlinkVk(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = getUserId()
                ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

            supabase.from("vk_identities").delete {
                filter { eq("user_id", userId) }
            }
            Result.success(Unit)
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
     * Отправляет на почту код восстановления пароля.
     *
     * `redirectUrl = null` намеренно: тогда в запрос не уходит `redirect_to`, и
     * не нужно ни заводить адрес в списке разрешённых на сервере, ни ловить
     * deep link в приложении. Восстановление идёт кодом, а не ссылкой —
     * подробности в [verifyPasswordResetCode].
     *
     * Ответ всегда успешный, даже если такой почты нет: иначе форма
     * восстановления превращается в проверку «есть ли у вас аккаунт с этим
     * адресом», и любой желающий может перебором собрать список ваших
     * пользователей.
     */
    suspend fun sendPasswordResetCode(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.auth.resetPasswordForEmail(email.trim(), redirectUrl = null)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("SupabaseAuth", "resetPasswordForEmail failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Проверяет код из письма. При успехе появляется сессия — с ней можно
     * задать новый пароль через [updatePassword].
     *
     * Почему код, а не ссылка. Ссылка требует deep link, записи адреса в
     * список разрешённых и обработки возврата в приложение. Но главное — она
     * работает только если письмо открыто на том же телефоне: с почты на
     * компьютере ссылка `carcost://` не сделает ничего. Код читается откуда
     * угодно и вводится руками.
     *
     * ВАЖНО: чтобы код попал в письмо, шаблон «Reset Password» на сервере
     * должен содержать {{ .Token }}. В стандартном шаблоне только ссылка.
     */
    suspend fun verifyPasswordResetCode(email: String, code: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                supabase.auth.verifyEmailOtp(
                    type = OtpType.Email.RECOVERY,
                    email = email.trim(),
                    token = code.trim()
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Log.w("SupabaseAuth", "recovery otp failed: ${e.message}")
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
    /**
     * @param clearPhoto  если true — явно обнуляет photo_url в БД (нельзя передать null через ?.let)
     */
    suspend fun updateProfile(
        displayName: String? = null,
        photoUrl: String? = null,
        clearPhoto: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = getUserId()
                ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

            val update = buildJsonObject {
                displayName?.let { put("display_name", it) }
                when {
                    clearPhoto       -> put("photo_url", null as String?)
                    photoUrl != null -> put("photo_url", photoUrl)
                }
            }

            supabase.from("users").update(update) {
                filter { eq("id", userId) }
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