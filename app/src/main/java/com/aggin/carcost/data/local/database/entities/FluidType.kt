package com.aggin.carcost.data.local.database.entities

enum class FluidType(
    val labelRu: String,
    val emoji: String,
    val checkIntervalDays: Int
) {
    ENGINE_OIL("Моторное масло", "🛢️", 90),
    BRAKE_FLUID("Тормозная жидкость", "🔴", 365),
    COOLANT("Охлаждающая жидкость", "🌡️", 365),
    WINDSHIELD_WASHER("Омывайка", "🧴", 30),
    POWER_STEERING("Жидкость ГУР", "🔧", 365),
    TRANSMISSION("Трансмиссионное масло", "⚙️", 365)
}
