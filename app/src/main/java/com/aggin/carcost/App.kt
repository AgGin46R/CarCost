package com.aggin.carcost // Убедитесь, что здесь указан ваш правильный пакет

import android.app.Application
import com.yandex.mapkit.MapKitFactory

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        // Устанавливаем API-ключ здесь. Это нужно сделать один раз при запуске приложения.
        MapKitFactory.setApiKey("9f9cb0c7-777a-4085-b75f-20758abb5abf")
    }
}