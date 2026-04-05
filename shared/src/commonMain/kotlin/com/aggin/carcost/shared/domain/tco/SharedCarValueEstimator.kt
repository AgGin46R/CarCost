package com.aggin.carcost.shared.domain.tco

/**
 * Platform-independent car value estimation using standard depreciation curves.
 */
object SharedCarValueEstimator {

    // Standard annual depreciation rates by age
    private val depreciationByYear = mapOf(
        1 to 0.20,  // 20% in first year
        2 to 0.15,
        3 to 0.12,
        4 to 0.10,
        5 to 0.08
    )
    private val defaultDepreciation = 0.06  // 6% per year after 5 years

    /**
     * Estimates current market value given purchase price and age in years.
     */
    fun estimateCurrentValue(purchasePrice: Double, ageYears: Int): Double {
        var value = purchasePrice
        for (year in 1..ageYears) {
            val rate = depreciationByYear[year] ?: defaultDepreciation
            value *= (1.0 - rate)
        }
        return value.coerceAtLeast(purchasePrice * 0.05)  // Min 5% of purchase price
    }

    /**
     * Calculates total cost of ownership per kilometer.
     */
    fun costPerKm(totalExpenses: Double, totalKm: Int): Double? {
        if (totalKm <= 0) return null
        return totalExpenses / totalKm
    }

    /**
     * Calculates monthly ownership cost.
     */
    fun monthlyOwnershipCost(purchasePrice: Double, totalExpenses: Double, monthsOwned: Int): Double? {
        if (monthsOwned <= 0) return null
        return totalExpenses / monthsOwned
    }
}
