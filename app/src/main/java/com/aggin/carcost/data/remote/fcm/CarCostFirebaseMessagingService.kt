package com.aggin.carcost.data.remote.fcm

import android.util.Log
import com.aggin.carcost.data.remote.push.PushMessageHandler
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "CarCostFCM"

/**
 * Получает FCM data-сообщения даже когда приложение убито.
 *
 * Edge Function отправляет data-only сообщения (без поля notification) с высоким
 * приоритетом. Это гарантирует, что onMessageReceived() вызывается независимо от
 * состояния приложения.
 *
 * Разбор сообщения и показ уведомления — в [PushMessageHandler]: тот же код
 * используется приёмником RuStore, формат сообщения у обоих транспортов общий.
 */
class CarCostFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token refreshed")
        scope.launch {
            FcmTokenManager.registerCurrentToken()
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        PushMessageHandler.handle(applicationContext, message.data, source = "FCM")
    }
}
