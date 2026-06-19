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
    val penaltyApplied: Double, // Примененное списание за превышение выходных (>6 в месяц)
    val monthlyRestDaysCount: Int // Накопленное количество выходных «В» за текущий месяц
)

class OverworkViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: OverworkRepository = OverworkRepository(OverworkDatabase.getDatabase(application).overworkDao())
    private val sharedPrefs = application.getSharedPreferences("overwork_journal_prefs", Context.MODE_PRIVATE)

    // GitHub repository configuration for updates
    val githubOwner = MutableStateFlow(sharedPrefs.getString("github_owner", "super-souls2018") ?: "super-souls2018")
    val githubRepo = MutableStateFlow(sharedPrefs.getString("github_repo", "overwork-journal") ?: "overwork-journal")

    val updateManager = UpdateManager(application)
    val updateState: StateFlow<UpdateState> = updateManager.updateState

    // List of all employees
    val employees: StateFlow<List<Employee>> = repositoryAllEmployeesFlow()

    // List of all duty rules
    val dutyRules: StateFlow<List<DutyRule>> = repository.allDutyRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Map of upper-cased symbol to its DutyRule
    val rulesMap: StateFlow<Map<String, DutyRule>> = dutyRules
        .map { list -> list.associateBy { it.symbol.trim().uppercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Flows of employee state mapped with their current dynamically calculated Column 13 overwork balance.
    val employeesWithBalance: StateFlow<List<Pair<Employee, Double>>> = combine(
        repository.allEmployees,
        repository.allWeeklyLogs,
        rulesMap
    ) { employeeList, logsList, rules ->
        employeeList.map { employee ->
            val empLogs = logsList.filter { it.employeeId == employee.id }.sortedBy { it.startDate }
            val balance = if (empLogs.isEmpty()) {
                employee.initialBalance
            } else {
                val calculatedRows = computeChronologicalLogs(employee, empLogs, rules)
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

        viewModelScope.launch {
            updateManager.checkForUpdates(githubOwner.value, githubRepo.value)
        }

        viewModelScope.launch {
            if (hasPendingCrash.value && githubToken.value.isNotEmpty()) {
                sendPendingCrashReportSilently()
            }
        }

        // Prepopulate default duty rules if database is empty
        viewModelScope.launch {
            repository.allDutyRules.take(1).collect { existingRules ->
                if (existingRules.isEmpty()) {
                    val defaultRules = listOf(
                        DutyRule("Р", "Рабочий день", "Рабочий день сверх продолжительности. В будни — 7 ч, в праздники — 4 ч.", 7.0, 4.0, false),
                        DutyRule("ВГ", "Наряд ВГ", "Наряд ВГ. Стандартный суточный караул. В будни — 30 ч, в праздники — 29 ч.", 30.0, 29.0, false),
                        DutyRule("П1", "Наряд П1", "Наряд П1. Суточный наряд. В будни — 30 ч, в праздники — 29 ч.", 30.0, 29.0, false),
                        DutyRule("Ф", "Наряд Ф", "Наряд Ф. Суточный наряд. В будни — 28 ч, в праздники — 27 ч.", 28.0, 27.0, false),
                        DutyRule("В", "Выходной", "Выходной день. 0 ч работы, 8 ч отдыха.", 0.0, 0.0, true),
                        DutyRule("—", "Послесуточный отдых", "Выходной день после суточного наряда (после суточного дежурства). 0 ч работы.", 0.0, 0.0, true)
                    )
                    defaultRules.forEach {
                        repository.insertDutyRule(it)
                    }
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
        selectedEmployeeLogs,
        rulesMap
    ) { employee, logs, rules ->
        if (employee == null || logs.isEmpty()) emptyList()
        else {
            // Sort logs from oldest to newest for chronological rolling balance
            val sortedLogs = logs.sortedBy { it.startDate }
            computeChronologicalLogs(employee, sortedLogs, rules).reversed() // reverse to show newest on top in UI
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

    // --- DutyRule Actions ---
    fun insertDutyRule(rule: DutyRule) {
        viewModelScope.launch {
            repository.insertDutyRule(rule)
        }
    }

    fun updateDutyRule(rule: DutyRule) {
        viewModelScope.launch {
            repository.updateDutyRule(rule)
        }
    }

    fun deleteDutyRule(rule: DutyRule) {
        viewModelScope.launch {
            repository.deleteDutyRule(rule)
        }
    }

    // Helper to compute rolling chronological balances
    private fun computeChronologicalLogs(
        employee: Employee,
        logs: List<WeeklyLog>, // Sorted ascending by date
        rules: Map<String, DutyRule> = emptyMap()
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
            var primaryMonthKey = ""
            
            if (monday != null) {
                primaryMonthKey = monday.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                log.getLoads().forEachIndexed { loadIdx, load ->
                    val cleanLoad = load.trim().uppercase()
                    if (cleanLoad == "В" || cleanLoad == "B") {
                        val dayDate = monday.plusDays(loadIdx.toLong())
                        val monthKey = dayDate.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                        val countSoFar = restCountByMonth[monthKey] ?: 0
                        val newCount = countSoFar + 1
                        restCountByMonth[monthKey] = newCount
                        
                        primaryMonthKey = monthKey
                        
                        if (newCount > 6) {
                            // Penalty: deduction of 8 hours from overwork balance
                            penaltyThisWeek += 8.0
                        }
                    }
                }
            }
            
            val col6 = log.calculateWeeklyOvertimeHours(rules)
            val col12 = log.additionalRestDaysHours
            
            if (log.col13Override != null) {
                runningOvertimeBalance = log.col13Override
            } else {
                runningOvertimeBalance = runningOvertimeBalance + col6 - col12 - penaltyThisWeek
            }
            
            val restDaysInMonth = if (primaryMonthKey.isNotEmpty()) {
                restCountByMonth[primaryMonthKey] ?: 0
            } else {
                0
            }
            
            rows.add(
                JournalRow(
                    index = index + 1,
                    log = log,
                    col6 = col6,
                    col7 = log.calculateWeekendWorkHours(rules),
                    col8 = log.calculateTotalWorkHours(rules),
                    col9 = log.calculateRestDates().joinToString(", "),
                    col10 = log.calculateRestHours(rules),
                    col11 = log.additionalRestDaysDate,
                    col12 = col12,
                    col13 = runningOvertimeBalance,
                    penaltyApplied = penaltyThisWeek,
                    monthlyRestDaysCount = restDaysInMonth
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

    fun setGithubConfig(owner: String, repo: String) {
        githubOwner.value = owner.trim()
        githubRepo.value = repo.trim()
        sharedPrefs.edit()
            .putString("github_owner", owner.trim())
            .putString("github_repo", repo.trim())
            .apply()
    }

    val githubToken = MutableStateFlow(sharedPrefs.getString("github_token", "") ?: "")
    val hasPendingCrash = MutableStateFlow(CrashReporter.hasPendingCrash(application))
    val pendingCrashDetails = MutableStateFlow(CrashReporter.getPendingCrashDetails(application))
    val crashReportSending = MutableStateFlow(false)
    val crashReportSendStatus = MutableStateFlow<String?>(null)

    fun setGithubToken(token: String) {
        githubToken.value = token.trim()
        sharedPrefs.edit().putString("github_token", token.trim()).apply()
        
        // Auto-submit crash if they just inputted a valid token
        if (hasPendingCrash.value && token.trim().isNotEmpty()) {
            sendPendingCrashReport()
        }
    }

    fun clearPendingCrashReport() {
        CrashReporter.clearPendingCrash(getApplication())
        hasPendingCrash.value = false
        pendingCrashDetails.value = null
        crashReportSendStatus.value = null
    }

    private suspend fun sendPendingCrashReportSilently() {
        val app = getApplication<Application>()
        val token = githubToken.value
        val owner = githubOwner.value
        val repo = githubRepo.value
        val rawJson = CrashReporter.getPendingCrashRawJson(app) ?: return

        val result = CrashReporter.sendReportToGithub(
            context = app,
            owner = owner,
            repo = repo,
            token = token,
            throwable = null,
            isFatal = true,
            contextInfo = "Automatic startup upload of fatal crash",
            customJsonStr = rawJson
        )
        if (result.isSuccess) {
            CrashReporter.clearPendingCrash(app)
            hasPendingCrash.value = false
            pendingCrashDetails.value = null
        }
    }

    fun sendPendingCrashReport() {
        viewModelScope.launch {
            crashReportSending.value = true
            crashReportSendStatus.value = null
            
            val app = getApplication<Application>()
            val token = githubToken.value
            val owner = githubOwner.value
            val repo = githubRepo.value
            val rawJson = CrashReporter.getPendingCrashRawJson(app)
            
            if (rawJson == null) {
                crashReportSending.value = false
                crashReportSendStatus.value = "Ошибка: Отчет о сбое не найден."
                return@launch
            }
            
            if (token.isEmpty()) {
                crashReportSending.value = false
                crashReportSendStatus.value = "Ошибка: GitHub токен отсутствует в настройках."
                return@launch
            }

            val result = CrashReporter.sendReportToGithub(
                context = app,
                owner = owner,
                repo = repo,
                token = token,
                throwable = null,
                isFatal = true,
                contextInfo = "Manual upload of fatal crash",
                customJsonStr = rawJson
            )
            
            crashReportSending.value = false
            if (result.isSuccess) {
                CrashReporter.clearPendingCrash(app)
                hasPendingCrash.value = false
                pendingCrashDetails.value = null
                crashReportSendStatus.value = "Успех! Отчёт отправлен: ${result.getOrNull()}"
            } else {
                crashReportSendStatus.value = "Ошибка отправки: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun reportHandledError(throwable: Throwable, contextInfo: String) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val token = githubToken.value
            val owner = githubOwner.value
            val repo = githubRepo.value
            
            if (token.isNotEmpty()) {
                CrashReporter.sendReportToGithub(
                    context = app,
                    owner = owner,
                    repo = repo,
                    token = token,
                    throwable = throwable,
                    isFatal = false,
                    contextInfo = contextInfo
                )
            } else {
                // Save locally so the user knows an error occurred
                CrashReporter.saveCrashLocally(app, throwable, false, contextInfo)
                hasPendingCrash.value = true
                pendingCrashDetails.value = CrashReporter.getPendingCrashDetails(app)
            }
        }
    }

    fun triggerManualCrash() {
        throw RuntimeException("Ручной тест: Сбой приложения, вызванный пользователем для проверки работы системы сбора крашей.")
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            updateManager.checkForUpdates(githubOwner.value, githubRepo.value)
        }
    }

    fun downloadAndInstallUpdate(downloadUrl: String) {
        viewModelScope.launch {
            updateManager.downloadAndInstall(downloadUrl)
        }
    }

    fun installApk(file: java.io.File) {
        updateManager.installApk(file)
    }

    fun resetUpdateState() {
        updateManager.resetToIdle()
    }
    
    fun getAppVersion(): String {
        return updateManager.getCurrentVersion()
    }
}
