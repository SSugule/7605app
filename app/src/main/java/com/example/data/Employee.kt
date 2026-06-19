package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String, // ФИО сотрудника
    val position: String, // Должность сотрудника
    val employeeNumber: String, // Номер сотрудника
    val initialBalance: Double = 0.0, // Начальное нереализованное время отдыха (часы)
    val createdAt: Long = System.currentTimeMillis()
)
