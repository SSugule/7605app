package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Data class representing a fully calculated row in the 13-column journal
data class JournalRow(
    val index: Int,
    val log: WeeklyLog,
    val col6: Double,  // Переработка (будни сверх установленного времени)
    val col7: Double,  // Переработка (выходные/праздники)
    val col8: Double,  // Суммарные часы работы
    val col9: String,  // Даты отдыха
    val col10: Double, // Часы отдыха
    val col11: String, // Дата доп. суток отдыха
    val col12: Double, // Часы доп. суток отдыха
    val col13: Double, // Нереализованное время отдыха (Баланс)
    val penaltyApplied: Double // Примененное списание за превышение выходных (>6 в месяц)
)

class OverworkViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: OverworkRepository = OverworkRepository(OverworkDatabase.getDatabase(application).overworkDao())
    private val sharedPrefs = application.getSharedPreferences("overwork_journal_prefs", Context.MODE_PRIVATE)

    // List of all employees
    val employees: StateFlow<List<Employee>> = repositoryAllEmployeesFlow()

    // Flows of employee state mapped with their current dynamically calculated Column 13 overwork balance.
    val employeesWithBalance: StateFlow<List<Pair<Employee, Double>>> = combine(
        repository.allEmployees,
        repository.allWeeklyLogs
    ) { employeeList, logsList ->
        employeeList.map { employee ->
            val empLogs = logsList.filter { it.employeeId == employee.id }.sortedBy { it.startDate }
            val balance = if (empLogs.isEmpty()) {
                employee.initialBalance
            } else {
                val calculatedRows = computeChronologicalLogs(employee, empLogs)
                calculatedRows.lastOrNull()?.col13 ?: employee.initialBalance
            }
            Pair(employee, balance)
        }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Currently selected employee
    private val _selectedEmployee = MutableStateFlow<Employee?>(null)
    val selectedEmployee: StateFlow<Employee?> = _selectedEmployee.asStateFlow()

    // Current navigation screen (acting like persisted URL route/localStorage state)
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Employees)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private fun repositoryAllEmployeesFlow(): StateFlow<List<Employee>> {
        return repository.allEmployees
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    init {
        // Load saved state (simulating web's localStorage persistence)
        val savedEmployeeId = sharedPrefs.getInt("selected_employee_id", -1)
        val savedScreenRoute = sharedPrefs.getString("current_screen", Screen.Employees.route) ?: Screen.Employees.route

        _currentScreen.value = when (savedScreenRoute) {
            Screen.Journal.route -> Screen.Journal
            Screen.Info.route -> Screen.Info
            else -> Screen.Employees
        }

        if (savedEmployeeId != -1) {
            viewModelScope.launch {
                val employee = repository.getEmployeeById(savedEmployeeId)
                if (employee != null) {
                    _selectedEmployee.value = employee
                }
            }
        }
    }

    // Logs of the selected employee
    val selectedEmployeeLogs: StateFlow<List<WeeklyLog>> = _selectedEmployee
        .flatMapLatest { employee ->
            if (employee == null) flowOf(emptyList())
            else repository.getWeeklyLogsForEmployee(employee.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Chronologically calculated journal rows for the selected employee
    val journalRows: StateFlow<List<JournalRow>> = combine(
        _selectedEmployee,
        selectedEmployeeLogs
    ) { employee, logs ->
        if (employee == null || logs.isEmpty()) emptyList()
        else {
            // Sort logs from oldest to newest for chronological rolling balance
            val sortedLogs = logs.sortedBy { it.startDate }
            computeChronologicalLogs(employee, sortedLogs).reversed() // reverse to show newest on top in UI
        }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Navigation Screen Actions ---
    fun setCurrentScreen(screen: Screen) {
        _currentScreen.value = screen
        sharedPrefs.edit().putString("current_screen", screen.route).apply()
    }

    // --- Employee Actions ---
    fun selectEmployee(employee: Employee?) {
        _selectedEmployee.value = employee
        sharedPrefs.edit().putInt("selected_employee_id", employee?.id ?: -1).apply()
    }

    fun addEmployee(name: String, position: String, employeeNumber: String, initialBalance: Double) {
        viewModelScope.launch {
            val employee = Employee(
                name = name,
                position = position,
                employeeNumber = employeeNumber,
                initialBalance = initialBalance
            )
            repository.insertEmployee(employee)
        }
    }

    fun updateEmployee(employee: Employee) {
        viewModelScope.launch {
            repository.updateEmployee(employee)
            // Refresh selection if we updated the current one
            if (_selectedEmployee.value?.id == employee.id) {
                _selectedEmployee.value = employee
            }
        }
    }

    fun deleteEmployee(employee: Employee) {
        viewModelScope.launch {
            if (_selectedEmployee.value?.id == employee.id) {
                _selectedEmployee.value = null
            }
            repository.deleteEmployee(employee)
        }
    }

    // --- WeeklyLog Actions ---
    fun saveWeeklyLog(
        employeeId: Int,
        startDate: String,
        loads: List<String>,
        holidays: List<Boolean>,
        additionalRestDate: String = "",
        additionalRestHours: Double = 0.0,
        col13Override: Double? = null
    ) {
        viewModelScope.launch {
            val existing = repository.getWeeklyLogByDate(employeeId, startDate)
            val log = WeeklyLog(
                id = existing?.id ?: 0,
                employeeId = employeeId,
                startDate = startDate,
                monLoad = loads.getOrElse(0) { "—" },
                tueLoad = loads.getOrElse(1) { "—" },
                wedLoad = loads.getOrElse(2) { "—" },
                thuLoad = loads.getOrElse(3) { "—" },
                friLoad = loads.getOrElse(4) { "—" },
                satLoad = loads.getOrElse(5) { "—" },
                sunLoad = loads.getOrElse(6) { "—" },
                monHoliday = holidays.getOrElse(0) { false },
                tueHoliday = holidays.getOrElse(1) { false },
                wedHoliday = holidays.getOrElse(2) { false },
                thuHoliday = holidays.getOrElse(3) { false },
                friHoliday = holidays.getOrElse(4) { false },
                satHoliday = holidays.getOrElse(5) { true },
                sunHoliday = holidays.getOrElse(6) { true },
                additionalRestDaysDate = additionalRestDate,
                additionalRestDaysHours = additionalRestHours,
                col13Override = col13Override
            )
            repository.insertWeeklyLog(log)
        }
    }

    fun deleteWeeklyLog(log: WeeklyLog) {
        viewModelScope.launch {
            repository.deleteWeeklyLog(log)
        }
    }

    // Helper to compute rolling chronological balances
    private fun computeChronologicalLogs(
        employee: Employee,
        logs: List<WeeklyLog> // Sorted ascending by date
    ): List<JournalRow> {
        val rows = mutableListOf<JournalRow>()
        var runningOvertimeBalance = employee.initialBalance
        val restCountByMonth = mutableMapOf<String, Int>() // "yyyy-MM" -> total count of 'В' days so far

        logs.forEachIndexed { index, log ->
            val monday = try {
                LocalDate.parse(log.startDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            } catch (e: Exception) {
                null
            }
            
            var penaltyThisWeek = 0.0
            
            if (monday != null) {
                log.getLoads().forEachIndexed { loadIdx, load ->
                    if (load == "В") {
                        val dayDate = monday.plusDays(loadIdx.toLong())
                        val monthKey = dayDate.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                        val countSoFar = restCountByMonth[monthKey] ?: 0
                        val newCount = countSoFar + 1
                        restCountByMonth[monthKey] = newCount
                        
                        if (newCount > 6) {
                            // Penalty: deduction of 8 hours from overwork balance
                            penaltyThisWeek += 8.0
                        }
                    }
                }
            }
            
            val col6 = log.calculateWeeklyOvertimeHours()
            val col12 = log.additionalRestDaysHours
            
            if (log.col13Override != null) {
                runningOvertimeBalance = log.col13Override
            } else {
                runningOvertimeBalance = runningOvertimeBalance + col6 - col12 - penaltyThisWeek
            }
            
            rows.add(
                JournalRow(
                    index = index + 1,
                    log = log,
                    col6 = col6,
                    col7 = log.calculateWeekendWorkHours(),
                    col8 = log.calculateTotalWorkHours(),
                    col9 = log.calculateRestDates().joinToString(", "),
                    col10 = log.calculateRestHours(),
                    col11 = log.additionalRestDaysDate,
                    col12 = col12,
                    col13 = runningOvertimeBalance,
                    penaltyApplied = penaltyThisWeek
                )
            )
        }
        return rows
    }

    // Get Monday date of current week (default week selection helper)
    fun getMondayOfCurrentWeek(): String {
        val today = LocalDate.now()
        val monday = today.with(DayOfWeek.MONDAY)
        return monday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }
}
