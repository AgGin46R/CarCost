package com.aggin.carcost.data.auth

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class GoogleAuthHelper(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val oneTapClient: SignInClient = Identity.getSignInClient(context)

    // Используйте ваш Web Client ID из google-services.json
    private val webClientId = "699802957399-jpa3792ti3rrq90ieuii2vrnm859de9u.apps.googleusercontent.com"

    /**
     * Начало процесса входа через Google One Tap
     * @return IntentSender для запуска One Tap UI
     */
    suspend fun signInWithGoogle(): IntentSender? {
        return try {
            val signInRequest = BeginSignInRequest.builder()
                .setGoogleIdTokenRequestOptions(
                    BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                        .setSupported(true)
                        .setServerClientId(webClientId)
                        .setFilterByAuthorizedAccounts(false) // Показывать все аккаунты
                        .build()
                )
                .setAutoSelectEnabled(true) // Автоматический выбор если один аккаунт
                .build()

            val result = oneTapClient.beginSignIn(signInRequest).await()
            result.pendingIntent.intentSender
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Обработка результата от Google Sign-In
     * @param intent Intent с данными от Google
     * @return ID токен для использования в AuthRepository
     */
    suspend fun handleSignInResult(intent: Intent): GoogleSignInResult {
        return try {
            val credential = oneTapClient.getSignInCredentialFromIntent(intent)
            val idToken = credential.googleIdToken

            if (idToken == null) {
                return GoogleSignInResult.Error("Не удалось получить токен Google")
            }

            // Авторизация в Firebase
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()

            val user = authResult.user
            if (user != null) {
                GoogleSignInResult.Success(
                    uid = user.uid,
                    email = user.email ?: "",
                    displayName = user.displayName ?: "",
                    photoUrl = user.photoUrl?.toString(),
                )
            } else {
                GoogleSignInResult.Error("Пользователь не найден")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            GoogleSignInResult.Error(e.localizedMessage ?: "Ошибка входа через Google")
        }
    }

    /**
     * Выход из Google аккаунта
     */
    suspend fun signOut() {
        try {
            oneTapClient.signOut().await()
            auth.signOut()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

sealed class GoogleSignInResult {
    data class Success(
        val uid: String,
        val email: String,
        val displayName: String,
        val photoUrl: String?
    ) : GoogleSignInResult()

    data class Error(val message: String) : GoogleSignInResult()
    object Cancelled : GoogleSignInResult()
}