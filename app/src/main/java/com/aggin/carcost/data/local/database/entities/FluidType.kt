package com.aggin.carcost.data.local.database.entities

import androidx.annotation.StringRes
import com.aggin.carcost.R

enum class FluidType(
    @StringRes val labelRes: Int,
    val emoji: String,
    val checkIntervalDays: Int
) {
    ENGINE_OIL(R.string.fluid_motornoe_maslo, "🛢️", 90),
    BRAKE_FLUID(R.string.fluid_tormoznaya_zhidkost, "🔴", 365),
    COOLANT(R.string.fluid_ohlazhdayuschaya_zhidkost, "🌡️", 365),
    WINDSHIELD_WASHER(R.string.fluid_omyvayka, "🧴", 30),
    POWER_STEERING(R.string.fluid_zhidkost_gur, "🔧", 365),
    TRANSMISSION(R.string.fluid_transmissionnoe_maslo, "⚙️", 365),
    BATTERY_COOLANT(R.string.fluid_ohlazhdayuschaya_zhidkost_batarei, "🔋", 365)
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
