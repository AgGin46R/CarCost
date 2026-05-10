package com.aggin.carcost.domain

import java.util.Calendar

/**
 * Calculates a vehicle depreciation curve based on Russian used-car market averages.
 *
 * Depreciation rates applied annually from purchase:
 *   Year 1:  -20%
 *   Year 2:  -15%
 *   Year 3:  -12%
 *   Year 4+: -10% per year
 *
 * Returns a list of [DepreciationPoint] covering [yearsForward] years after purchase.
 */
object DepreciationCalculator {

    private val annualRates = listOf(0.20, 0.15, 0.12) // year 1, 2, 3
    private const val DEFAULT_RATE = 0.10              // year 4+

    data class DepreciationPoint(
        /** Calendar year label (e.g. 2020, 2021 …). */
        val year: Int,
        /** Years after purchase (0 = purchase year). */
        val yearIndex: Int,
        /** Estimated market value in purchase currency. */
        val value: Double,
        /** Cumulative depreciation as a fraction 0..1 */
        val depreciatedFraction: Double
    )

    /**
     * @param purchasePrice  Original purchase price.
     * @param purchaseDate   Unix timestamp of purchase (milliseconds).
     * @param yearsForward   How many years to project forward from purchase.
     * @return               List of depreciation points, index 0 = purchase year.
     */
    fun calculate(
        purchasePrice: Double,
        purchaseDate: Long,
        yearsForward: Int = 10
    ): List<DepreciationPoint> {
        if (purchasePrice <= 0) return emptyList()

        val purchaseYear = Calendar.getInstance().apply { timeInMillis = purchaseDate }
            .get(Calendar.YEAR)
        val points = mutableListOf<DepreciationPoint>()
        var value = purchasePrice

        for (i in 0..yearsForward) {
            points.add(
                DepreciationPoint(
                    year = purchaseYear + i,
                    yearIndex = i,
                    value = value,
                    depreciatedFraction = 1.0 - (value / purchasePrice)
                )
            )
            val rate = annualRates.getOrElse(i) { DEFAULT_RATE }
            value *= (1.0 - rate)
            if (value < purchasePrice * 0.05) value = purchasePrice * 0.05 // floor at 5%
        }

        return points
    }

    /**
     * Net depreciation in currency units for a given period.
     */
    fun netDepreciation(points: List<DepreciationPoint>): Double {
        if (points.size < 2) return 0.0
        return points.first().value - points.last().value
    }

    /**
     * Estimate current value using purchase price, purchase date and today's date.
     * If [marketValue] is provided (user override), returns that instead.
     */
    fun currentEstimate(
        purchasePrice: Double,
        purchaseDate: Long,
        marketValue: Double? = null
    ): Double {
        if (marketValue != null && marketValue > 0) return marketValue
        val todayYear = Calendar.getInstance().get(Calendar.YEAR)
        val purchaseYear = Calendar.getInstance().apply { timeInMillis = purchaseDate }
            .get(Calendar.YEAR)
        val yearsOwned = (todayYear - purchaseYear).coerceAtLeast(0)
        val points = calculate(purchasePrice, purchaseDate, yearsOwned)
        return points.lastOrNull()?.value ?: purchasePrice
    }
}
