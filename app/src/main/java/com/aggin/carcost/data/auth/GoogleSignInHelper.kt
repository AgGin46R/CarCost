package com.aggin.carcost.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GoogleSignInHelper {

    /**
     * Web OAuth 2.0 client ID from Google Cloud Console.
     * To set up:
     * 1. Open https://console.cloud.google.com/ → APIs & Services → Credentials
     * 2. Create OAuth 2.0 Client ID → Web application
     * 3. Copy the Client ID and paste here
     * 4. Also configure this same Client ID in Supabase Dashboard → Auth → Providers → Google
     */
    private const val WEB_CLIENT_ID = "275357869761-tdmd17ql3oh7v0idrfe1770p81hbbmlu.apps.googleusercontent.com"

    /**
     * Launches the Google Sign-In bottom sheet via Credential Manager.
     * Returns the Google ID token on success, or a failure Result.
     */
    suspend fun getIdToken(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val credentialManager = CredentialManager.create(context)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)   // show all accounts, not only previously used
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context = context, request = request)
            val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            Result.success(googleCredential.idToken)
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Exception("Отменено пользователем"))
        } catch (e: NoCredentialException) {
            Result.failure(Exception("Нет аккаунтов Google на устройстве"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
