package com.aggin.carcost.shared.domain.gamification

data class SharedDriverScore(
    val total: Int,
    val maintenanceScore: Int,
    val budgetScore: Int,
    val fuelScore: Int,
    val regularityScore: Int,
    val level: String
)

object SharedDriverScoreCalculator {

    fun scoreToLevel(score: Int): String = when {
        score >= 90 -> "Мастер"
        score >= 75 -> "Опытный"
        score >= 55 -> "Средний"
        score >= 35 -> "Новичок"
        else -> "Требует внимания"
    }

    /**
     * Simple weighted score combining sub-scores.
     * Platform-specific implementations pass pre-calculated sub-scores here.
     */
    fun combine(
        maintenanceScore: Int,
        budgetScore: Int,
        fuelScore: Int,
        regularityScore: Int
    ): SharedDriverScore {
        val total = (maintenanceScore * 0.35 + budgetScore * 0.25 +
                fuelScore * 0.20 + regularityScore * 0.20).toInt().coerceIn(0, 100)
        return SharedDriverScore(
            total = total,
            maintenanceScore = maintenanceScore,
            budgetScore = budgetScore,
            fuelScore = fuelScore,
            regularityScore = regularityScore,
            level = scoreToLevel(total)
        )
    }
}
