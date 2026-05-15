package com.aggin.carcost.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class AchievementType(val title: String, val description: String, val icon: String) {
    FIRST_EXPENSE("Первый расход", "Добавьте первый расход", "🎉"),
    EXPENSES_10("10 расходов", "Добавьте 10 расходов", "📝"),
    EXPENSES_50("Полтинник", "Добавьте 50 расходов", "🏅"),
    EXPENSES_100("Сотня записей", "Добавьте 100 расходов", "💯"),
    ECO_DRIVER("Эко-водитель", "Расход топлива ниже среднего 3 месяца подряд", "🌿"),
    BUDGET_MASTER("Мастер бюджета", "Не превысьте бюджет 3 месяца подряд", "💰"),
    REGULAR_MAINTENANCE("Педант ТО", "Пройдите 5 плановых ТО вовремя", "🔧"),
    FIRST_DOCUMENT("Архивариус", "Добавьте первый документ", "📄"),
    TRIP_TRACKER("GPS-трекер", "Запишите первую поездку по GPS", "📍"),
    SAVINGS_GOAL_COMPLETE("Цель достигнута", "Достигните первой цели накопления", "🏆"),
    YEAR_OWNER("Год с нами", "Используйте приложение год", "🎂"),
    // Новые достижения
    MULTI_CAR("Автопарк", "Добавьте 2 и более автомобиля", "🚘"),
    FUEL_VETERAN("Завсегдатай АЗС", "Заправьтесь 20 раз", "⛽"),
    NIGHT_DRIVER("Ночной водитель", "Добавьте расход после 23:00", "🌙"),
    PHOTO_COLLECTOR("Фотограф", "Прикрепите чек к 10 расходам", "📸"),
    WORKSHOP_REGULAR("Постоянный клиент", "Посетите один автосервис 5+ раз", "🔩"),
    HIGH_MILEAGE("Стотысячник", "Пробег авто достиг 100 000 км", "💫")
}

@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val type: AchievementType,
    val unlockedAt: Long = System.currentTimeMillis(),
    val metadata: String? = null
)
