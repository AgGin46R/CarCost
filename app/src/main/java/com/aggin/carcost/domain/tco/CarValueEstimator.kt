package com.aggin.carcost.domain.tco

import java.time.LocalDate

object CarValueEstimator {

    // Annual depreciation rates by age bracket
    // Year 1: ~20%, Year 2-3: ~15%, Year 4+: ~10%
    fun estimateCurrentValue(purchasePrice: Double, purchaseYear: Int): Double {
        val currentYear = LocalDate.now().year
        val age = (currentYear - purchaseYear).coerceAtLeast(0)
        if (age == 0) return purchasePrice

        var value = purchasePrice
        for (y in 1..age) {
            val rate = when (y) {
                1 -> 0.20
                2, 3 -> 0.15
                else -> 0.10
            }
            value *= (1.0 - rate)
        }
        return value.coerceAtLeast(purchasePrice * 0.05) // floor at 5% of purchase price
    }

    fun depreciationPercent(purchasePrice: Double, currentValue: Double): Int {
        if (purchasePrice <= 0) return 0
        return ((1.0 - currentValue / purchasePrice) * 100).toInt().coerceIn(0, 100)
    }
}
