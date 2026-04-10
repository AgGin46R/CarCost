package com.aggin.carcost.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

private const val TAG = "GoogleSignInHelper"

object GoogleSignInHelper {

    private val WEB_CLIENT_ID get() = com.aggin.carcost.BuildConfig.GOOGLE_WEB_CLIENT_ID

    /**
     * Запрашивает Google ID Token через Credential Manager.
     * Должен вызываться на Main-потоке (нужен Activity контекст для UI).
     */
    suspend fun getIdToken(context: Context): Result<String> {
        // ⚠️ НЕ используем withContext(IO) — CredentialManager должен работать на Main
        val credentialManager = CredentialManager.create(context)

        return try {
            val signInOption = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            Log.d(TAG, "Requesting credential with clientId: $WEB_CLIENT_ID")
            val result = credentialManager.getCredential(context = context, request = request)
            val credential = result.credential

            Log.d(TAG, "Got credential type: ${credential.type}")

            when {
                credential is GoogleIdTokenCredential -> {
                    Log.d(TAG, "Direct GoogleIdTokenCredential — success")
                    Result.success(credential.idToken)
                }
                credential is CustomCredential &&
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                    try {
                        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        Log.d(TAG, "Parsed GoogleIdTokenCredential from CustomCredential — success")
                        Result.success(googleCredential.idToken)
                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e(TAG, "Failed to parse GoogleIdTokenCredential", e)
                        Result.failure(Exception("Ошибка обработки токена Google: ${e.message}"))
                    }
                }
                else -> {
                    Log.e(TAG, "Unexpected credential type: ${credential.type}")
                    Result.failure(Exception("Неподдерживаемый тип учётных данных: ${credential.type}"))
                }
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "User cancelled Google Sign-In")
            Result.failure(Exception("Отменено пользователем"))
        } catch (e: GetCredentialException) {
            Log.e(TAG, "GetCredentialException: ${e.type} — ${e.message}", e)
            Result.failure(Exception("Ошибка Google Sign-In: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception during Google Sign-In", e)
            Result.failure(e)
        }
    }
}
