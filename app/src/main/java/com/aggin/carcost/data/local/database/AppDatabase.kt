package com.aggin.carcost.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aggin.carcost.data.local.database.dao.CarDao
import com.aggin.carcost.data.local.database.dao.UserDao
import com.aggin.carcost.data.local.database.dao.ExpenseDao
import com.aggin.carcost.data.local.database.dao.MaintenanceReminderDao
import com.aggin.carcost.data.local.database.dao.ExpenseTagDao
import com.aggin.carcost.data.local.database.dao.PlannedExpenseDao
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import com.aggin.carcost.data.local.database.entities.User
import com.aggin.carcost.data.local.database.entities.ExpenseTag
import com.aggin.carcost.data.local.database.entities.ExpenseTagCrossRef
import com.aggin.carcost.data.local.database.entities.PlannedExpense

// Миграция с версии 7 на версию 8
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Создаём таблицу expense_tags
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS expense_tags (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                color TEXT NOT NULL,
                userId TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """)

        // Создаём таблицу expense_tag_cross_ref
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS expense_tag_cross_ref (
                expenseId INTEGER NOT NULL,
                tagId INTEGER NOT NULL,
                PRIMARY KEY(expenseId, tagId),
                FOREIGN KEY(expenseId) REFERENCES expenses(id) ON DELETE CASCADE,
                FOREIGN KEY(tagId) REFERENCES expense_tags(id) ON DELETE CASCADE
            )
        """)

        // Создаём индексы для оптимизации запросов
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_expense_tag_cross_ref_expenseId 
            ON expense_tag_cross_ref(expenseId)
        """)

        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_expense_tag_cross_ref_tagId 
            ON expense_tag_cross_ref(tagId)
        """)
    }
}

// Миграция с версии 10 на версию 11 - добавление таблицы planned_expenses
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS planned_expenses (
                id TEXT PRIMARY KEY NOT NULL,
                carId TEXT NOT NULL,
                userId TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT,
                category TEXT NOT NULL,
                estimatedAmount REAL,
                actualAmount REAL,
                targetDate INTEGER,
                completedDate INTEGER,
                priority TEXT NOT NULL,
                status TEXT NOT NULL,
                targetOdometer INTEGER,
                notes TEXT,
                shopUrl TEXT,
                linkedExpenseId TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                isSynced INTEGER NOT NULL DEFAULT 0
            )
        """)

        // Создаём индекс для carId для быстрого поиска
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_planned_expenses_carId 
            ON planned_expenses(carId)
        """)

        // Создаём индекс для userId
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_planned_expenses_userId 
            ON planned_expenses(userId)
        """)

        // Создаём индекс для status
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_planned_expenses_status 
            ON planned_expenses(status)
        """)
    }
}

// Миграция с версии 11 на версию 12 - исправление схемы planned_expenses
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Создаём временную таблицу с правильной схемой (без DEFAULT)
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS planned_expenses_new (
                id TEXT PRIMARY KEY NOT NULL,
                carId TEXT NOT NULL,
                userId TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT,
                category TEXT NOT NULL,
                estimatedAmount REAL,
                actualAmount REAL,
                targetDate INTEGER,
                completedDate INTEGER,
                priority TEXT NOT NULL,
                status TEXT NOT NULL,
                targetOdometer INTEGER,
                notes TEXT,
                shopUrl TEXT,
                linkedExpenseId TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                isSynced INTEGER NOT NULL
            )
        """)

        // Копируем данные из старой таблицы в новую
        database.execSQL("""
            INSERT INTO planned_expenses_new 
            SELECT * FROM planned_expenses
        """)

        // Удаляем старую таблицу
        database.execSQL("DROP TABLE planned_expenses")

        // Переименовываем новую таблицу
        database.execSQL("ALTER TABLE planned_expenses_new RENAME TO planned_expenses")

        // Создаём индексы заново
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_planned_expenses_carId 
            ON planned_expenses(carId)
        """)

        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_planned_expenses_userId 
            ON planned_expenses(userId)
        """)

        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_planned_expenses_status 
            ON planned_expenses(status)
        """)
    }
}

@Database(
    entities = [
        Car::class,
        Expense::class,
        MaintenanceReminder::class,
        User::class,
        ExpenseTag::class,
        ExpenseTagCrossRef::class,
        PlannedExpense::class
    ],
    version = 12,  // Увеличиваем версию
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun carDao(): CarDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun maintenanceReminderDao(): MaintenanceReminderDao
    abstract fun userDao(): UserDao
    abstract fun expenseTagDao(): ExpenseTagDao
    abstract fun plannedExpenseDao(): PlannedExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "carcost_database"
                )
                    .addMigrations(MIGRATION_7_8, MIGRATION_10_11, MIGRATION_11_12)  // Добавляем новую миграцию
                    .fallbackToDestructiveMigration()  // На случай других изменений
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}