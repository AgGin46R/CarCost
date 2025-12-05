package com.aggin.carcost.data.local.database

import androidx.room.TypeConverter
import com.aggin.carcost.data.local.database.entities.*

class Converters {

    // FuelType converters
    @TypeConverter
    fun fromFuelType(value: FuelType): String {
        return value.name
    }

    @TypeConverter
    fun toFuelType(value: String): FuelType {
        return try {
            FuelType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            FuelType.GASOLINE
        }
    }

    // OdometerUnit converters
    @TypeConverter
    fun fromOdometerUnit(value: OdometerUnit): String {
        return value.name
    }

    @TypeConverter
    fun toOdometerUnit(value: String): OdometerUnit {
        return try {
            OdometerUnit.valueOf(value)
        } catch (e: IllegalArgumentException) {
            OdometerUnit.KM
        }
    }

    // ExpenseCategory converters
    @TypeConverter
    fun fromExpenseCategory(value: ExpenseCategory): String {
        return value.name
    }

    @TypeConverter
    fun toExpenseCategory(value: String): ExpenseCategory {
        return try {
            ExpenseCategory.valueOf(value)
        } catch (e: IllegalArgumentException) {
            ExpenseCategory.OTHER
        }
    }

    // ServiceType converters
    @TypeConverter
    fun fromServiceType(value: ServiceType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toServiceType(value: String?): ServiceType? {
        return value?.let {
            try {
                ServiceType.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    // MaintenanceType converters
    @TypeConverter
    fun fromMaintenanceType(value: MaintenanceType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toMaintenanceType(value: String?): MaintenanceType? {
        return value?.let {
            try {
                MaintenanceType.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    // PlannedExpensePriority converters
    @TypeConverter
    fun fromPlannedExpensePriority(value: PlannedExpensePriority): String {
        return value.name
    }

    @TypeConverter
    fun toPlannedExpensePriority(value: String): PlannedExpensePriority {
        return try {
            PlannedExpensePriority.valueOf(value)
        } catch (e: IllegalArgumentException) {
            PlannedExpensePriority.MEDIUM
        }
    }

    // PlannedExpenseStatus converters
    @TypeConverter
    fun fromPlannedExpenseStatus(value: PlannedExpenseStatus): String {
        return value.name
    }

    @TypeConverter
    fun toPlannedExpenseStatus(value: String): PlannedExpenseStatus {
        return try {
            PlannedExpenseStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            PlannedExpenseStatus.PLANNED
        }
    }
}