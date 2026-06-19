package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OverworkDao {

    // --- Employee Operations ---
    @Query("SELECT * FROM employees ORDER BY name ASC")
    fun getAllEmployees(): Flow<List<Employee>>

    @Query("SELECT * FROM employees WHERE id = :id")
    suspend fun getEmployeeById(id: Int): Employee?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: Employee): Long

    @Update
    suspend fun updateEmployee(employee: Employee)

    @Delete
    suspend fun deleteEmployee(employee: Employee)

    // --- WeeklyLog Operations ---
    @Query("SELECT * FROM weekly_logs WHERE employeeId = :employeeId ORDER BY startDate DESC")
    fun getWeeklyLogsForEmployee(employeeId: Int): Flow<List<WeeklyLog>>

    @Query("SELECT * FROM weekly_logs ORDER BY startDate DESC")
    fun getAllWeeklyLogs(): Flow<List<WeeklyLog>>

    @Query("SELECT * FROM weekly_logs WHERE employeeId = :employeeId AND startDate = :startDate LIMIT 1")
    suspend fun getWeeklyLogByDate(employeeId: Int, startDate: String): WeeklyLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeklyLog(log: WeeklyLog): Long

    @Update
    suspend fun updateWeeklyLog(log: WeeklyLog)

    @Delete
    suspend fun deleteWeeklyLog(log: WeeklyLog)

    // --- DutyRule Operations ---
    @Query("SELECT * FROM duty_rules ORDER BY isSystem ASC, symbol ASC")
    fun getAllDutyRules(): Flow<List<DutyRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDutyRule(rule: DutyRule)

    @Update
    suspend fun updateDutyRule(rule: DutyRule)

    @Delete
    suspend fun deleteDutyRule(rule: DutyRule)
}
