package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "duty_rules")
data class DutyRule(
    @PrimaryKey val symbol: String, // e.g., "Р", "ВГ", "П1", "Ф"
    val name: String,
    val description: String,
    val weekdayHours: Double,
    val holidayHours: Double,
    val isSystem: Boolean = false // System rules (like "В" and "—") have unique behaviors in calculation logic
)
