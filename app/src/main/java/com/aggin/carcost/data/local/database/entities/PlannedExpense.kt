package com.aggin.carcost.data.local.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class PlannedExpensePriority {
    LOW,      // Низкий приоритет
    MEDIUM,   // Средний приоритет
    HIGH,     // Высокий приоритет
    URGENT    // Срочно
}

enum class PlannedExpenseStatus {
    PLANNED,     // Запланировано
    IN_PROGRESS, // В процессе
    COMPLETED,   // Выполнено
    CANCELLED    // Отменено
}

@Entity(
    tableName = "planned_expenses",
    indices = [
        Index(value = ["carId"]),
        Index(value = ["userId"]),
        Index(value = ["status"])
    ]
)
data class PlannedExpense(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val carId: String,
    val userId: String,

    // Основная информация
    val title: String,                              // Название (например, "Купить амортизаторы")
    val description: String? = null,                // Дополнительное описание
    val category: ExpenseCategory,                  // Категория

    // Финансовые данные
    val estimatedAmount: Double? = null,            // Ориентировочная стоимость
    val actualAmount: Double? = null,               // Фактическая стоимость (когда выполнено)

    // Сроки
    val targetDate: Long? = null,                   // Планируемая дата выполнения
    val completedDate: Long? = null,                // Фактическая дата выполнения

    // Статус и приоритет
    val priority: PlannedExpensePriority = PlannedExpensePriority.MEDIUM,
    val status: PlannedExpenseStatus = PlannedExpenseStatus.PLANNED,

    // Пробег
    val targetOdometer: Int? = null,                // При каком пробеге планируется

    // Заметки
    val notes: String? = null,                      // Дополнительные заметки
    val shopUrl: String? = null,                    // Ссылка на магазин/товар

    // Связь с реальным расходом
    val linkedExpenseId: String? = null,            // ID созданного расхода

    // Метаданные
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // Синхронизация
    val isSynced: Boolean = false
)