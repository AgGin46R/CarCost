package com.aggin.carcost

import android.app.Application
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.gotrue.auth

class App : Application() {

    companion object {
        lateinit var supabase: SupabaseClient
            private set

        private const val TAG = "CarCostApp"
    }

    override fun onCreate() {
        super.onCreate()

        try {
            initializeSupabase()
            Log.d(TAG, "Supabase initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Supabase", e)
        }
    }

    private fun initializeSupabase() {
        supabase = createSupabaseClient(
            supabaseUrl = "https://mkwwidzaovxosnhsjomy.supabase.co", // ЗАМЕНИ!
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1rd3dpZHphb3Z4b3NuaHNqb215Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjM2NDgzNTEsImV4cCI6MjA3OTIyNDM1MX0.jycoe9IJe2xUv7QXP8aafubFBzebK6tsjKr0Ca4gh_M" // ЗАМЕНИ!
        ) {
            install(Auth) {
                // Настройки аутентификации
                autoLoadFromStorage = true
                alwaysAutoRefresh = true
            }

            install(Postgrest) {
                // Настройки PostgreSQL
            }

            install(Storage) {
                // Хранилище файлов
            }

            install(Realtime) {
                // Realtime подписки (опционально)
            }
        }
    }
}

// Глобальный доступ к Supabase
val supabase: SupabaseClient
    get() = App.supabase