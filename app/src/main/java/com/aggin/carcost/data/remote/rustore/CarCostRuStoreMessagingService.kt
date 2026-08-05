package com.aggin.carcost.data.remote.rustore

import android.util.Log
import com.aggin.carcost.data.remote.push.PushMessageHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.rustore.sdk.pushclient.messaging.model.RemoteMessage
import ru.rustore.sdk.pushclient.messaging.service.RuStoreMessagingService

private const val TAG = "CarCostRuStore"

/**
 * Приёмник пуш-уведомлений RuStore.
 *
 * Зеркало [com.aggin.carcost.data.remote.fcm.CarCostFirebaseMessagingService]:
 * разбор сообщения общий, в [PushMessageHandler], потому что формат полей задаёт
 * наша же Edge Function и он одинаков для обоих транспортов.
 *
 * Работает только на устройствах, где установлено приложение RuStore и ему
 * разрешена работа в фоне. Это осознанное дополнение к Firebase, а не замена:
 * у кого есть сервисы Google — доставка идёт через них.
 */
class CarCostRuStoreMessagingService : RuStoreMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        Log.d(TAG, "Токен RuStore обновлён")
        scope.launch {
            RuStorePushTokenManager.registerCurrentToken()
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        PushMessageHandler.handle(applicationContext, message.data, source = "RuStore")
    }
}
