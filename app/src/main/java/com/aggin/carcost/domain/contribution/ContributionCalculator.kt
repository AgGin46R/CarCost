package com.aggin.carcost.domain.contribution

import com.aggin.carcost.data.local.database.entities.Expense

/**
 * Кто сколько внёс в общий автомобиль.
 *
 * Считается по полю автора расхода. У записей, созданных до появления этого
 * поля, автор неизвестен — они собираются в отдельную строку, а не растворяются
 * в чужих долях: приписать человеку чужие траты хуже, чем честно сказать
 * «неизвестно».
 */
object ContributionCalculator {

    /** Вклад одного человека */
    data class Contribution(
        /** `null` — автор записи неизвестен */
        val userId: String?,
        val amount: Double,
        /** Доля от общей суммы, 0..1 */
        val share: Double,
        /**
         * Насколько больше (или меньше) внесено по сравнению с равным делением.
         * `null` для строки «автор неизвестен»: её не с чем сравнивать.
         */
        val deviationFromEqual: Double?
    )

    data class Result(
        val contributions: List<Contribution>,
        val total: Double,
        /** Сколько человек с известным авторством участвовали */
        val knownContributors: Int
    ) {
        /**
         * У одиночной машины разбивка бессмысленна: там всегда «вы — 100 %».
         * Пустой результат тоже сюда попадает.
         */
        val isWorthShowing: Boolean get() = knownContributors >= 2
    }

    fun calculate(expenses: List<Expense>): Result {
        if (expenses.isEmpty()) {
            return Result(emptyList(), total = 0.0, knownContributors = 0)
        }

        val byAuthor = expenses.groupBy { it.userId?.takeIf { id -> id.isNotBlank() } }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        val total = byAuthor.values.sum()
        if (total <= 0.0) {
            return Result(emptyList(), total = 0.0, knownContributors = 0)
        }

        val knownAuthors = byAuthor.keys.filterNotNull()

        // Равная доля считается только по известным авторам. Иначе строка
        // «неизвестно» разбавляла бы знаменатель и занижала отклонение у всех.
        val equalShare = if (knownAuthors.isNotEmpty()) {
            byAuthor.filterKeys { it != null }.values.sum() / knownAuthors.size
        } else null

        val contributions = byAuthor
            .map { (userId, amount) ->
                Contribution(
                    userId = userId,
                    amount = amount,
                    share = amount / total,
                    deviationFromEqual = if (userId != null && equalShare != null) {
                        amount - equalShare
                    } else null
                )
            }
            // Известные авторы вперёд и по убыванию суммы; «неизвестно» — всегда
            // последней строкой, это примечание, а не участник
            .sortedWith(compareBy({ it.userId == null }, { -it.amount }))

        return Result(
            contributions = contributions,
            total = total,
            knownContributors = knownAuthors.size
        )
    }
}
