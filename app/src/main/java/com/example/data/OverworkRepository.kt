package com.example.data

import kotlinx.coroutines.flow.Flow

class OverworkRepository(private val overworkDao: OverworkDao) {

    // Employees
    val allEmployees: Flow<List<Employee>> = overworkDao.getAllEmployees()

    suspend fun getEmployeeById(id: Int): Employee? {
        return overworkDao.getEmployeeById(id)
    }

    suspend fun insertEmployee(employee: Employee): Long {
        return overworkDao.insertEmployee(employee)
    }

    suspend fun updateEmployee(employee: Employee) {
        overworkDao.updateEmployee(employee)
    }

    suspend fun deleteEmployee(employee: Employee) {
        overworkDao.deleteEmployee(employee)
    }

    // Weekly Logs
    val allWeeklyLogs: Flow<List<WeeklyLog>> = overworkDao.getAllWeeklyLogs()

    fun getWeeklyLogsForEmployee(employeeId: Int): Flow<List<WeeklyLog>> {
        return overworkDao.getWeeklyLogsForEmployee(employeeId)
    }

    suspend fun getWeeklyLogByDate(employeeId: Int, startDate: String): WeeklyLog? {
        return overworkDao.getWeeklyLogByDate(employeeId, startDate)
    }

    suspend fun insertWeeklyLog(log: WeeklyLog): Long {
        return overworkDao.insertWeeklyLog(log)
    }

    suspend fun updateWeeklyLog(log: WeeklyLog) {
        overworkDao.updateWeeklyLog(log)
    }

    suspend fun deleteWeeklyLog(log: WeeklyLog) {
        overworkDao.deleteWeeklyLog(log)
    }

    // Duty Rules
    val allDutyRules: Flow<List<DutyRule>> = overworkDao.getAllDutyRules()

    suspend fun insertDutyRule(rule: DutyRule) {
        overworkDao.insertDutyRule(rule)
    }

    suspend fun updateDutyRule(rule: DutyRule) {
        overworkDao.updateDutyRule(rule)
    }

    suspend fun deleteDutyRule(rule: DutyRule) {
        overworkDao.deleteDutyRule(rule)
    }
}
