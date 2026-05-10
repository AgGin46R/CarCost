package com.aggin.carcost.data.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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

    private fun Context.findActivity(): Activity {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        error("Activity не найдена в контексте")
    }

    suspend fun getIdToken(context: Context): Result<String> {
        val activity = try {
            context.findActivity()
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось получить Activity", e)
            return Result.failure(Exception("Внутренняя ошибка: нет Activity"))
        }

        val credentialManager = CredentialManager.create(activity)

        return try {
            val signInOption = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            Log.d(TAG, "Requesting credential, clientId=$WEB_CLIENT_ID")
            // Ключевое исправление — передаём activity, а не context
            val result = credentialManager.getCredential(context = activity, request = request)
            val credential = result.credential

            Log.d(TAG, "Got credential type: ${credential.type}")

            when {
                credential is GoogleIdTokenCredential -> {
                    Result.success(credential.idToken)
                }
                credential is CustomCredential &&
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                    try {
                        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        Result.success(googleCredential.idToken)
                    } catch (e: GoogleIdTokenParsingException) {
                        Result.failure(Exception("Ошибка обработки токена: ${e.message}"))
                    }
                }
                else -> {
                    Result.failure(Exception("Неподдерживаемый тип: ${credential.type}"))
                }
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Exception("Отменено пользователем"))
        } catch (e: GetCredentialException) {
            Log.e(TAG, "GetCredentialException: ${e.type} — ${e.message}", e)
            Result.failure(Exception("Ошибка Google Sign-In: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception", e)
            Result.failure(e)
        }
    }
}