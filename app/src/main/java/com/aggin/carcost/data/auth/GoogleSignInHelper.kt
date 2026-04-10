package com.aggin.carcost.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GoogleSignInHelper {

    private val WEB_CLIENT_ID get() = com.aggin.carcost.BuildConfig.GOOGLE_WEB_CLIENT_ID

    /**
     * Показывает полноценный аккаунт-пикер Google.
     * Сначала пробует GetSignInWithGoogleOption (стандартный пикер, работает всегда).
     * При ошибке — fallback на GetGoogleIdOption (One Tap).
     */
    suspend fun getIdToken(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val credentialManager = CredentialManager.create(context)

        // Попытка 1: GetSignInWithGoogleOption — полный пикер аккаунтов
        try {
            val signInOption = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
            val result = credentialManager.getCredential(context = context, request = request)
            val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
            return@withContext Result.success(credential.idToken)
        } catch (e: GetCredentialCancellationException) {
            return@withContext Result.failure(Exception("Отменено пользователем"))
        } catch (_: NoCredentialException) {
            // нет аккаунтов — fallback ниже
        } catch (_: Exception) {
            // другая ошибка — пробуем fallback
        }

        // Попытка 2: GetGoogleIdOption (One Tap) как fallback
        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(context = context, request = request)
            val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
            Result.success(credential.idToken)
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Exception("Отменено пользователем"))
        } catch (e: NoCredentialException) {
            Result.failure(Exception("Нет аккаунтов Google на устройстве"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
