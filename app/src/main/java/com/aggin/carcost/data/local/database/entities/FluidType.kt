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
    TRANSMISSION("Трансмиссионное масло", "⚙️", 365),
    BATTERY_COOLANT("Охлаждающая жидкость батареи", "🔋", 365)
}

/**
 * Жидкости, которые есть у этой машины.
 *
 * У электромобиля нет моторного масла, а руль электрический — жидкости ГУР тоже
 * нет. Зато есть контур охлаждения батареи, которого нет у остальных.
 */
fun fluidTypesFor(fuelType: FuelType): List<FluidType> =
    FluidType.entries.filter { fluid ->
        when (fluid) {
            FluidType.ENGINE_OIL, FluidType.POWER_STEERING -> fuelType.canRefuel
            FluidType.BATTERY_COOLANT -> fuelType.canCharge
            else -> true
        }
    }
