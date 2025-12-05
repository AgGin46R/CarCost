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
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import com.aggin.carcost.data.local.database.entities.User
import com.aggin.carcost.data.local.database.entities.ExpenseTag
import com.aggin.carcost.data.local.database.entities.ExpenseTagCrossRef

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

@Database(
    entities = [
        Car::class,
        Expense::class,
        MaintenanceReminder::class,
        User::class,
        ExpenseTag::class,
        ExpenseTagCrossRef::class
    ],
    version = 10,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun carDao(): CarDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun maintenanceReminderDao(): MaintenanceReminderDao
    abstract fun userDao(): UserDao
    abstract fun expenseTagDao(): ExpenseTagDao

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
                    .addMigrations(MIGRATION_7_8)  // Добавляем миграцию
                    .fallbackToDestructiveMigration()  // На случай других изменений
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}