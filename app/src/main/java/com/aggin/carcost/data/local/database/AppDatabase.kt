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
import com.aggin.carcost.data.local.database.dao.CarDocumentDao
import com.aggin.carcost.data.local.database.dao.CategoryBudgetDao
import com.aggin.carcost.data.local.database.dao.AiInsightDao
import com.aggin.carcost.data.local.database.dao.FuelPriceDao
import com.aggin.carcost.data.local.database.dao.AchievementDao
import com.aggin.carcost.data.local.database.dao.SavingsGoalDao
import com.aggin.carcost.data.local.database.dao.CarMemberDao
import com.aggin.carcost.data.local.database.dao.GpsTripDao
import com.aggin.carcost.data.local.database.dao.VinCacheDao
import com.aggin.carcost.data.local.database.dao.ChatMessageDao
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import com.aggin.carcost.data.local.database.entities.User
import com.aggin.carcost.data.local.database.entities.ExpenseTag
import com.aggin.carcost.data.local.database.entities.ExpenseTagCrossRef
import com.aggin.carcost.data.local.database.entities.PlannedExpense
import com.aggin.carcost.data.local.database.entities.CarDocument
import com.aggin.carcost.data.local.database.entities.CategoryBudget
import com.aggin.carcost.data.local.database.entities.AiInsight
import com.aggin.carcost.data.local.database.entities.FuelPrice
import com.aggin.carcost.data.local.database.entities.Achievement
import com.aggin.carcost.data.local.database.entities.SavingsGoal
import com.aggin.carcost.data.local.database.entities.CarMember
import com.aggin.carcost.data.local.database.entities.GpsTrip
import com.aggin.carcost.data.local.database.entities.VinCache
import com.aggin.carcost.data.local.database.entities.ChatMessage

// Миграция с версии 7 на версию 8 - СТАРАЯ ВЕРСИЯ (с ошибкой)
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Создаём таблицу expense_tags (СТАРАЯ - с INTEGER)
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS expense_tags (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                color TEXT NOT NULL,
                userId TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """)

        // Создаём таблицу expense_tag_cross_ref (СТАРАЯ - с INTEGER)
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS expense_tag_cross_ref (
                expenseId INTEGER NOT NULL,
                tagId INTEGER NOT NULL,
                PRIMARY KEY(expenseId, tagId),
                FOREIGN KEY(expenseId) REFERENCES expenses(id) ON DELETE CASCADE,
                FOREIGN KEY(tagId) REFERENCES expense_tags(id) ON DELETE CASCADE
            )
        """)

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

// Миграция с версии 10 на версию 11
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

// Миграция с версии 11 на версию 12
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
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

        database.execSQL("""
            INSERT INTO planned_expenses_new 
            SELECT * FROM planned_expenses
        """)
        database.execSQL("DROP TABLE planned_expenses")
        database.execSQL("ALTER TABLE planned_expenses_new RENAME TO planned_expenses")

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

// Миграция 13 → 14: добавление хранилища документов
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS car_documents (
                id TEXT PRIMARY KEY NOT NULL,
                carId TEXT NOT NULL,
                type TEXT NOT NULL,
                title TEXT NOT NULL,
                fileUri TEXT,
                expiryDate INTEGER,
                notes TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(carId) REFERENCES cars(id) ON DELETE CASCADE
            )
        """)
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_car_documents_carId ON car_documents(carId)
        """)
    }
}

// Миграция 14 → 15: добавление бюджетов по категориям
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS category_budgets (
                id TEXT PRIMARY KEY NOT NULL,
                carId TEXT NOT NULL,
                category TEXT NOT NULL,
                monthlyLimit REAL NOT NULL,
                month INTEGER NOT NULL,
                year INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(carId) REFERENCES cars(id) ON DELETE CASCADE
            )
        """)
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_category_budgets_carId ON category_budgets(carId)
        """)
    }
}

// Миграция 15 → 16: AI-инсайты
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS ai_insights (
                id TEXT PRIMARY KEY NOT NULL,
                carId TEXT NOT NULL,
                type TEXT NOT NULL,
                title TEXT NOT NULL,
                body TEXT NOT NULL,
                severity TEXT NOT NULL DEFAULT 'INFO',
                createdAt INTEGER NOT NULL,
                isRead INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(carId) REFERENCES cars(id) ON DELETE CASCADE
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_insights_carId ON ai_insights(carId)")
    }
}

// Миграция 16 → 17: цены топлива
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS fuel_prices (
                id TEXT PRIMARY KEY NOT NULL,
                stationName TEXT NOT NULL,
                fuelType TEXT NOT NULL,
                pricePerLiter REAL NOT NULL,
                latitude REAL,
                longitude REAL,
                recordedAt INTEGER NOT NULL
            )
        """)
    }
}

// Миграция 17 → 18: достижения
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS achievements (
                id TEXT PRIMARY KEY NOT NULL,
                userId TEXT NOT NULL,
                type TEXT NOT NULL,
                unlockedAt INTEGER NOT NULL,
                metadata TEXT
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS index_achievements_userId ON achievements(userId)")
    }
}

// Миграция 18 → 19: цели накопления
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS savings_goals (
                id TEXT PRIMARY KEY NOT NULL,
                carId TEXT NOT NULL,
                title TEXT NOT NULL,
                targetAmount REAL NOT NULL,
                currentAmount REAL NOT NULL DEFAULT 0.0,
                deadline INTEGER,
                isCompleted INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(carId) REFERENCES cars(id) ON DELETE CASCADE
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS index_savings_goals_carId ON savings_goals(carId)")
    }
}

// Миграция 19 → 20: участники авто
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS car_members (
                id TEXT PRIMARY KEY NOT NULL,
                carId TEXT NOT NULL,
                userId TEXT NOT NULL,
                email TEXT NOT NULL,
                role TEXT NOT NULL,
                joinedAt INTEGER NOT NULL,
                FOREIGN KEY(carId) REFERENCES cars(id) ON DELETE CASCADE
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS index_car_members_carId ON car_members(carId)")
    }
}

// Миграция 20 → 21: GPS-поездки
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS gps_trips (
                id TEXT PRIMARY KEY NOT NULL,
                carId TEXT NOT NULL,
                startTime INTEGER NOT NULL,
                endTime INTEGER,
                distanceKm REAL NOT NULL DEFAULT 0.0,
                routeJson TEXT,
                avgSpeedKmh REAL,
                FOREIGN KEY(carId) REFERENCES cars(id) ON DELETE CASCADE
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS index_gps_trips_carId ON gps_trips(carId)")
    }
}

// Миграция 21 → 22: VIN-кэш
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS vin_cache (
                vin TEXT PRIMARY KEY NOT NULL,
                make TEXT,
                model TEXT,
                year TEXT,
                engine TEXT,
                country TEXT,
                cachedAt INTEGER NOT NULL
            )
        """)
    }
}

// ✅ ИСПРАВЛЕННАЯ МИГРАЦИЯ: Исправление типов для тегов (12 → 13)
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Стратегия: полностью пересоздаём таблицы тегов
        // Данные восстановятся при синхронизации с Supabase

        // 1. Удаляем старые таблицы (если существуют)
        database.execSQL("DROP TABLE IF EXISTS expense_tag_cross_ref")
        database.execSQL("DROP TABLE IF EXISTS expense_tags")

        // 2. Создаём таблицу expense_tags с правильным типом TEXT
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS expense_tags (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                color TEXT NOT NULL,
                userId TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """)

        // 3. Создаём таблицу expense_tag_cross_ref с правильным типом TEXT
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS expense_tag_cross_ref (
                expenseId TEXT NOT NULL,
                tagId TEXT NOT NULL,
                PRIMARY KEY(expenseId, tagId),
                FOREIGN KEY(expenseId) REFERENCES expenses(id) ON DELETE CASCADE,
                FOREIGN KEY(tagId) REFERENCES expense_tags(id) ON DELETE CASCADE
            )
        """)

        // 4. Создаём индексы
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

val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN mediaUrl TEXT")
    }
}

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN mediaType TEXT")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN fileName TEXT")
    }
}

val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS chat_messages (
                id TEXT PRIMARY KEY NOT NULL,
                carId TEXT NOT NULL,
                userId TEXT NOT NULL,
                userEmail TEXT NOT NULL,
                message TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(carId) REFERENCES cars(id) ON DELETE CASCADE
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_carId ON chat_messages(carId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_createdAt ON chat_messages(createdAt)")
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
        PlannedExpense::class,
        CarDocument::class,
        CategoryBudget::class,
        AiInsight::class,
        FuelPrice::class,
        Achievement::class,
        SavingsGoal::class,
        CarMember::class,
        GpsTrip::class,
        VinCache::class,
        ChatMessage::class
    ],
    version = 25,
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
    abstract fun carDocumentDao(): CarDocumentDao
    abstract fun categoryBudgetDao(): CategoryBudgetDao
    abstract fun aiInsightDao(): AiInsightDao
    abstract fun fuelPriceDao(): FuelPriceDao
    abstract fun achievementDao(): AchievementDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun carMemberDao(): CarMemberDao
    abstract fun gpsTripDao(): GpsTripDao
    abstract fun vinCacheDao(): VinCacheDao
    abstract fun chatMessageDao(): ChatMessageDao

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
                    .addMigrations(
                        MIGRATION_7_8,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                        MIGRATION_16_17,
                        MIGRATION_17_18,
                        MIGRATION_18_19,
                        MIGRATION_19_20,
                        MIGRATION_20_21,
                        MIGRATION_21_22,
                        MIGRATION_22_23,
                        MIGRATION_23_24,
                        MIGRATION_24_25
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}