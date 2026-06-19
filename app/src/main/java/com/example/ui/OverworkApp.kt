package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Employee
import com.example.data.WeeklyLog
import com.example.data.DutyRule
import com.example.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

sealed class Screen(val route: String) {
    object Employees : Screen("employees")
    object Journal : Screen("journal")
    object Info : Screen("info")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverworkApp(viewModel: OverworkViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val employeesbyBalance by viewModel.employeesWithBalance.collectAsStateWithLifecycle()
    val selectedEmployee by viewModel.selectedEmployee.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    var showAddEmployeeDialog by remember { mutableStateOf(false) }
    var employeeToEdit by remember { mutableStateOf<Employee?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (isWideScreen) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                    header = {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    },
                    modifier = Modifier.testTag("side_nav_rail")
                ) {
                    NavigationRailItem(
                        selected = currentScreen == Screen.Employees,
                        onClick = { viewModel.setCurrentScreen(Screen.Employees) },
                        icon = { Icon(Icons.Default.People, contentDescription = "Сотрудники") },
                        label = { Text("Сотрудники") },
                        modifier = Modifier.testTag("nav_employees_tab")
                    )
                    NavigationRailItem(
                        selected = currentScreen == Screen.Journal,
                        onClick = { viewModel.setCurrentScreen(Screen.Journal) },
                        icon = { Icon(Icons.Default.Assignment, contentDescription = "Журнал") },
                        label = { Text("Журнал") },
                        modifier = Modifier.testTag("nav_journal_tab")
                    )
                    NavigationRailItem(
                        selected = currentScreen == Screen.Info,
                        onClick = { viewModel.setCurrentScreen(Screen.Info) },
                        icon = { Icon(Icons.Default.HelpCenter, contentDescription = "Правила") },
                        label = { Text("Правила") },
                        modifier = Modifier.testTag("nav_info_tab")
                    )
                }
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isWideScreen) {
                                    Icon(
                                        imageVector = Icons.Default.Timeline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                                Text(
                                    text = "Учёт переработок",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                if (isWideScreen && selectedEmployee != null) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = "• Журнал: ${selectedEmployee?.name}",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                        )
                    )
                },
                bottomBar = {
                    if (!isWideScreen) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                            modifier = Modifier.testTag("bottom_nav_bar")
                        ) {
                            NavigationBarItem(
                                selected = currentScreen == Screen.Employees,
                                onClick = { viewModel.setCurrentScreen(Screen.Employees) },
                                icon = { Icon(Icons.Default.People, contentDescription = "Сотрудники") },
                                label = { Text("Сотрудники") },
                                modifier = Modifier.testTag("nav_employees_tab")
                            )
                            NavigationBarItem(
                                selected = currentScreen == Screen.Journal,
                                onClick = { viewModel.setCurrentScreen(Screen.Journal) },
                                icon = { Icon(Icons.Default.Assignment, contentDescription = "Журнал") },
                                label = { Text("Журнал" + (if (selectedEmployee != null) " (${selectedEmployee?.name?.take(7)}...)" else "")) },
                                modifier = Modifier.testTag("nav_journal_tab")
                            )
                            NavigationBarItem(
                                selected = currentScreen == Screen.Info,
                                onClick = { viewModel.setCurrentScreen(Screen.Info) },
                                icon = { Icon(Icons.Default.HelpCenter, contentDescription = "Правила") },
                                label = { Text("Правила") },
                                modifier = Modifier.testTag("nav_info_tab")
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (currentScreen == Screen.Employees) {
                        ExtendedFloatingActionButton(
                            text = { Text("Добавить", fontWeight = FontWeight.SemiBold) },
                            icon = { Icon(Icons.Default.PersonAdd, contentDescription = "Добавить сотрудника") },
                            onClick = { showAddEmployeeDialog = true },
                            modifier = Modifier.testTag("add_employee_fab")
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentScreen) {
                        Screen.Employees -> {
                            EmployeesScreen(
                                employees = employeesbyBalance,
                                isWideScreen = isWideScreen,
                                onSelectEmployee = { emp ->
                                    viewModel.selectEmployee(emp)
                                    viewModel.setCurrentScreen(Screen.Journal)
                                },
                                onEditEmployee = { emp ->
                                    employeeToEdit = emp
                                    showAddEmployeeDialog = true
                                },
                                onDeleteEmployee = { emp ->
                                    viewModel.deleteEmployee(emp)
                                }
                            )
                        }
                        Screen.Journal -> {
                            JournalScreen(
                                selectedEmployee = selectedEmployee,
                                viewModel = viewModel,
                                isWideScreen = isWideScreen,
                                onChangeEmployee = {
                                    viewModel.setCurrentScreen(Screen.Employees)
                                }
                            )
                        }
                        Screen.Info -> {
                            InfoScreen(isWideScreen = isWideScreen, viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    if (showAddEmployeeDialog) {
        EmployeeDialog(
            employee = employeeToEdit,
            onDismiss = {
                showAddEmployeeDialog = false
                employeeToEdit = null
            },
            onSave = { name, pos, num, initBal ->
                if (employeeToEdit == null) {
                    viewModel.addEmployee(name, pos, num, initBal)
                } else {
                    viewModel.updateEmployee(employeeToEdit!!.copy(
                        name = name,
                        position = pos,
                        employeeNumber = num,
                        initialBalance = initBal
                    ))
                }
                showAddEmployeeDialog = false
                employeeToEdit = null
            }
        )
    }

    // --- Update Handler Dialog Overlay ---
    val state = updateState
    if (state != UpdateState.Idle && state != UpdateState.UpToDate) {
        when (state) {
            is UpdateState.Checking -> {
                // Silently checks, do not block the UI
            }
            is UpdateState.UpdateAvailable -> {
                AlertDialog(
                    onDismissRequest = { viewModel.resetUpdateState() },
                    title = { Text("🔄 Доступно обновление!") },
                    text = {
                        Column {
                            Text("Обнаружена новая версия: ${state.latestVersion}", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Изменения в этой версии:")
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp)
                            ) {
                                LazyColumn(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    item {
                                        Text(
                                            text = state.changelog.ifEmpty { "Нет описания изменений" },
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.downloadAndInstallUpdate(state.downloadUrl) }
                        ) {
                            Text("Скачать и установить")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { viewModel.resetUpdateState() }
                        ) {
                            Text("Позже")
                        }
                    }
                )
            }
            is UpdateState.Downloading -> {
                AlertDialog(
                    onDismissRequest = { /* No dismiss */ },
                    title = { Text("📥 Загрузка обновления...") },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("${(state.progress * 100).toInt()}% завершено", fontSize = 14.sp)
                        }
                    },
                    confirmButton = {}
                )
            }
            is UpdateState.ReadyToInstall -> {
                LaunchedEffect(state.apkFile) {
                    viewModel.installApk(state.apkFile)
                }
                
                AlertDialog(
                    onDismissRequest = { viewModel.resetUpdateState() },
                    title = { Text("✅ Обновление загружено") },
                    text = { Text("Файл обновления успешно скачан. Запустить ручную установку, если она не началась автоматически?") },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.installApk(state.apkFile) }
                        ) {
                            Text("Установить")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { viewModel.resetUpdateState() }
                        ) {
                            Text("Отмена")
                        }
                    }
                )
            }
            is UpdateState.PermissionRedirect -> {
                AlertDialog(
                    onDismissRequest = { viewModel.resetUpdateState() },
                    title = { Text("⚙️ Требуется разрешение") },
                    text = { Text("Для установки обновления необходимо разрешить установку приложений из внешних источников для этой программы.\n\nМы перенаправили вас в настройки системы. Пожалуйста, включите переключатель и попробуйте установить снова.") },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.resetUpdateState() }
                        ) {
                            Text("Понятно")
                        }
                    }
                )
            }
            is UpdateState.Error -> {
                AlertDialog(
                    onDismissRequest = { viewModel.resetUpdateState() },
                    title = { Text("❌ Ошибка обновления") },
                    text = { Text(state.message) },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.resetUpdateState() }
                        ) {
                            Text("Закрыть")
                        }
                    }
                )
            }
            is UpdateState.Idle, is UpdateState.UpToDate -> {
                // Do nothing
            }
        }
    }
}

// Helper function to format hour values nicely and drop trailing zero
fun formatHours(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        value.toString()
    }
}

// --- Employees Screen ---
@Composable
fun EmployeesScreen(
    employees: List<Pair<Employee, Double>>,
    isWideScreen: Boolean,
    onSelectEmployee: (Employee) -> Unit,
    onEditEmployee: (Employee) -> Unit,
    onDeleteEmployee: (Employee) -> Unit
) {
    if (employees.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Список сотрудников пуст",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Нажмите круглую кнопку в правом нижнем углу,\nчтобы добавить первого сотрудника.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    } else {
        if (isWideScreen) {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 340.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("employees_grid"),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Сотрудники (${employees.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                    )
                }
                items(employees.size, key = { index -> employees[index].first.id }) { index ->
                    val (employee, balance) = employees[index]
                    EmployeeCard(
                        employee = employee,
                        balance = balance,
                        onClick = { onSelectEmployee(employee) },
                        onEdit = { onEditEmployee(employee) },
                        onDelete = { onDeleteEmployee(employee) }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("employees_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Сотрудники (${employees.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                    )
                }
                items(employees, key = { it.first.id }) { (employee, balance) ->
                    EmployeeCard(
                        employee = employee,
                        balance = balance,
                        onClick = { onSelectEmployee(employee) },
                        onEdit = { onEditEmployee(employee) },
                        onDelete = { onDeleteEmployee(employee) }
                    )
                }
            }
        }
    }
}

@Composable
fun EmployeeCard(
    employee: Employee,
    balance: Double,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("employee_card_${employee.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = employee.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${employee.employeeNumber} • ${employee.position}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EventAvailable,
                        contentDescription = null,
                        tint = Emerald500,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Переработка: ${formatHours(balance)} ч.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Emerald600
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row {
                IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_employee_${employee.id}")) {
                    Icon(Icons.Default.Edit, contentDescription = "Редактировать", tint = MaterialTheme.colorScheme.primary)
                }
                var showConfirmDelete by remember { mutableStateOf(false) }
                IconButton(onClick = { showConfirmDelete = true }, modifier = Modifier.testTag("delete_employee_${employee.id}")) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Rose500)
                }

                if (showConfirmDelete) {
                    AlertDialog(
                        onDismissRequest = { showConfirmDelete = false },
                        title = { Text("Удалить сотрудника?") },
                        text = { Text("Вы действительно хотите удалить ${employee.name}? Все его еженедельные записи и переработки будут полностью стерты.") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showConfirmDelete = false
                                    onDelete()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Rose500)
                            ) { Text("Удалить", color = Color.White) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showConfirmDelete = false }) { Text("Отмена") }
                        }
                    )
                }
            }
        }
    }
}

// --- Create/Edit Employee Dialog ---
@Composable
fun EmployeeDialog(
    employee: Employee?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double) -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("overwork_drafts_prefs", Context.MODE_PRIVATE) }
    val draftKeyPrefix = if (employee == null) "create_employee_" else "edit_employee_${employee.id}_"

    var name by remember {
        mutableStateOf(sharedPrefs.getString(draftKeyPrefix + "name", null) ?: employee?.name ?: "")
    }
    var position by remember {
        mutableStateOf(sharedPrefs.getString(draftKeyPrefix + "position", null) ?: employee?.position ?: "")
    }
    var num by remember {
        mutableStateOf(sharedPrefs.getString(draftKeyPrefix + "num", null) ?: employee?.employeeNumber ?: "Рядовой")
    }
    var initialBalanceStr by remember {
        mutableStateOf(sharedPrefs.getString(draftKeyPrefix + "balance", null) ?: employee?.initialBalance?.toString() ?: "0.0")
    }

    LaunchedEffect(name) {
        sharedPrefs.edit().putString(draftKeyPrefix + "name", name).apply()
    }
    LaunchedEffect(position) {
        sharedPrefs.edit().putString(draftKeyPrefix + "position", position).apply()
    }
    LaunchedEffect(num) {
        sharedPrefs.edit().putString(draftKeyPrefix + "num", num).apply()
    }
    LaunchedEffect(initialBalanceStr) {
        sharedPrefs.edit().putString(draftKeyPrefix + "balance", initialBalanceStr).apply()
    }

    fun clearDrafts() {
        sharedPrefs.edit()
            .remove(draftKeyPrefix + "name")
            .remove(draftKeyPrefix + "position")
            .remove(draftKeyPrefix + "num")
            .remove(draftKeyPrefix + "balance")
            .apply()
    }

    var expandedRank by remember { mutableStateOf(false) }
    val ranksList = listOf("Старший прапорщик", "Прапорщик", "Старший сержант", "Сержант", "Младший сержант", "Ефрейтор", "Рядовой")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (employee == null) "Зарегистрировать сотрудника" else "Редактировать профиль",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ФИО сотрудника") },
                    placeholder = { Text("Хужин А.А.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("employee_name_input")
                )
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it },
                    label = { Text("Должность сотрудника") },
                    placeholder = { Text("Старший инженер") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("employee_pos_input")
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = num,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Звание") },
                        trailingIcon = {
                            Icon(
                                imageVector = if (expandedRank) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = "Выбрать звание"
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("employee_num_input")
                    )
                    Box(
                         modifier = Modifier
                            .matchParentSize()
                            .clickable { expandedRank = true }
                    )
                    DropdownMenu(
                        expanded = expandedRank,
                        onDismissRequest = { expandedRank = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        ranksList.forEach { rank ->
                            DropdownMenuItem(
                                text = { Text(rank) },
                                onClick = {
                                    num = rank
                                    expandedRank = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = initialBalanceStr,
                    onValueChange = { initialBalanceStr = it },
                    label = { Text("Начальный баланс переработки (ч)") },
                    placeholder = { Text("0.0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("employee_balance_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val balanceVal = initialBalanceStr.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && position.isNotBlank() && num.isNotBlank()) {
                        clearDrafts()
                        onSave(name, position, num, balanceVal)
                    }
                },
                modifier = Modifier.testTag("save_employee_button")
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    clearDrafts()
                    onDismiss()
                }
            ) { Text("Отмена") }
        }
    )
}

// --- Journal View (Weekly details) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    selectedEmployee: Employee?,
    viewModel: OverworkViewModel,
    isWideScreen: Boolean,
    onChangeEmployee: () -> Unit
) {
    if (selectedEmployee == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Badge,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                modifier = Modifier.size(100.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Не выбран сотрудник",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Перейдите на вкладку 'Сотрудники' и нажмите на любого человека, чтобы открыть его еженедельный журнал переработок.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onChangeEmployee) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("К списку сотрудников")
            }
        }
        return
    }

    val rows by viewModel.journalRows.collectAsStateWithLifecycle()
    var selectedWeekMonday by remember { mutableStateOf(viewModel.getMondayOfCurrentWeek()) }
    var showAddJournalDialog by remember { mutableStateOf(false) }
    var journalLogToEdit by remember { mutableStateOf<WeeklyLog?>(null) }
    var viewModeTab by remember { mutableStateOf(0) } // 0 = Карточки (Интерактивный), 1 = Таблица (13 колонок)

    val currentDeviceYear = LocalDate.now().year
    var journalSectionTab by remember { mutableStateOf(0) } // 0 = Текущий год, 1 = Архив
    var selectedFilterMonth by remember { mutableStateOf<Int?>(null) } // null = Все
    var selectedFilterYear by remember { mutableStateOf<Int?>(null) } // null = Все

    val archiveYears = remember(rows) {
        rows.mapNotNull { row ->
            try { LocalDate.parse(row.log.startDate).year } catch(e: Exception) { null }
        }
        .filter { it != currentDeviceYear }
        .distinct()
        .sortedDescending()
    }

    val filteredRows = remember(rows, journalSectionTab, selectedFilterMonth, selectedFilterYear) {
        rows.filter { row ->
            val localDate = try { LocalDate.parse(row.log.startDate) } catch(e: Exception) { null }
            if (localDate == null) {
                journalSectionTab == 0
            } else {
                val matchesSection = if (journalSectionTab == 0) {
                    localDate.year == currentDeviceYear
                } else {
                    localDate.year != currentDeviceYear
                }
                
                val matchesMonth = if (selectedFilterMonth == null) {
                    true
                } else {
                    localDate.monthValue == selectedFilterMonth
                }
                
                val matchesYear = if (journalSectionTab == 0 || selectedFilterYear == null) {
                    true
                } else {
                    localDate.year == selectedFilterYear
                }
                
                matchesSection && matchesMonth && matchesYear
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // --- Employee Quick Header ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(14.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedEmployee.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${selectedEmployee.position} • ${selectedEmployee.employeeNumber}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                TextButton(onClick = onChangeEmployee) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Сменить")
                }
            }
        }

        // --- Current Year vs Archive Section Selector ---
        TabRow(
            selectedTabIndex = journalSectionTab,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
            divider = {}
        ) {
            Tab(
                selected = journalSectionTab == 0,
                onClick = { 
                    journalSectionTab = 0
                    selectedFilterMonth = null
                    selectedFilterYear = null
                },
                text = { Text("Текущий год ($currentDeviceYear)", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
            Tab(
                selected = journalSectionTab == 1,
                onClick = { 
                    journalSectionTab = 1
                    selectedFilterMonth = null
                    selectedFilterYear = null
                },
                text = { Text("Архив", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
        }

        // --- Filters Bar ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                // Months list scrollable
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Месяц:", 
                        fontSize = 11.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    val monthsList = listOf(
                        Pair(null, "Все"),
                        Pair(1, "Янв"),
                        Pair(2, "Фев"),
                        Pair(3, "Мар"),
                        Pair(4, "Апр"),
                        Pair(5, "Май"),
                        Pair(6, "Июн"),
                        Pair(7, "Июл"),
                        Pair(8, "Авг"),
                        Pair(9, "Сен"),
                        Pair(10, "Окт"),
                        Pair(11, "Ноя"),
                        Pair(12, "Дек")
                    )
                    monthsList.forEach { (mCode, mName) ->
                        FancyFilterChip(
                            selected = selectedFilterMonth == mCode,
                            onClick = { selectedFilterMonth = mCode },
                            label = mName
                        )
                    }
                }

                // Years list scrollable (only for Archive)
                if (journalSectionTab == 1 && archiveYears.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Год:", 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        FancyFilterChip(
                            selected = selectedFilterYear == null,
                            onClick = { selectedFilterYear = null },
                            label = "Все"
                        )
                        archiveYears.forEach { yr ->
                            FancyFilterChip(
                                selected = selectedFilterYear == yr,
                                onClick = { selectedFilterYear = yr },
                                label = "$yr г."
                            )
                        }
                    }
                }
            }
        }

        // --- View Mode Selector and FAB Row ---
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabRow(
                selectedTabIndex = viewModeTab,
                modifier = Modifier
                    .width(260.dp)
                    .clip(RoundedCornerShape(8.dp)),
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                divider = {},
                indicator = @Composable { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[viewModeTab]),
                        height = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                Tab(
                    selected = viewModeTab == 0,
                    onClick = { viewModeTab = 0 },
                    text = { Text("Сводка", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = viewModeTab == 1,
                    onClick = { viewModeTab = 1 },
                    text = { Text("Журнал (ТБ)", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
            }

            Button(
                onClick = { 
                    val newestLog = rows.firstOrNull()?.log
                    if (newestLog != null) {
                        try {
                            val lastMonday = LocalDate.parse(newestLog.startDate)
                            selectedWeekMonday = lastMonday.plusWeeks(1).toString()
                        } catch(e: Exception) {
                            selectedWeekMonday = viewModel.getMondayOfCurrentWeek()
                        }
                    } else {
                        selectedWeekMonday = viewModel.getMondayOfCurrentWeek()
                    }
                    showAddJournalDialog = true 
                },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.testTag("add_weekly_log_button")
            ) {
                Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Запись", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // --- Weekly Logs List ---
        Box(modifier = Modifier.weight(1f)) {
            if (rows.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.EventNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Нет записей для сотрудника",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Нажмите кнопку '+ Запись', чтобы добавить график работы на неделю.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            } else if (filteredRows.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Нет записей по выбранным деталям",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Попробуйте изменить выбранный месяц или год фильтрации.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                if (viewModeTab == 0) {
                    // List View (Cards showing dynamic breakdown)
                    if (isWideScreen) {
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 360.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(filteredRows.size, key = { index -> filteredRows[index].log.id }) { index ->
                                val row = filteredRows[index]
                                WeeklyBreakdownCard(
                                    row = row,
                                    onEdit = {
                                        journalLogToEdit = row.log
                                        selectedWeekMonday = row.log.startDate
                                        showAddJournalDialog = true
                                    },
                                    onDelete = { viewModel.deleteWeeklyLog(row.log) }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredRows) { row ->
                                WeeklyBreakdownCard(
                                    row = row,
                                    onEdit = {
                                        journalLogToEdit = row.log
                                        selectedWeekMonday = row.log.startDate
                                        showAddJournalDialog = true
                                    },
                                    onDelete = { viewModel.deleteWeeklyLog(row.log) }
                                )
                            }
                        }
                    }
                } else {
                    // Document Spreadsheet table view (mimics the 13 columns log printed sheet)
                    JournalTable(
                        rows = filteredRows,
                        employee = selectedEmployee,
                        onEdit = { row ->
                            journalLogToEdit = row.log
                            selectedWeekMonday = row.log.startDate
                            showAddJournalDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showAddJournalDialog) {
        WeeklyScheduleDialog(
            viewModel = viewModel,
            employeeId = selectedEmployee.id,
            startDateString = selectedWeekMonday,
            existingLog = journalLogToEdit,
            onDismiss = {
                showAddJournalDialog = false
                journalLogToEdit = null
            },
            onSave = { weekDate, loads, holidays, addRestDate, addRestHours, col13Override ->
                viewModel.saveWeeklyLog(
                    employeeId = selectedEmployee.id,
                    startDate = weekDate,
                    loads = loads,
                    holidays = holidays,
                    additionalRestDate = addRestDate,
                    additionalRestHours = addRestHours,
                    col13Override = col13Override
                )
                showAddJournalDialog = false
                journalLogToEdit = null
            }
        )
    }
}

// --- Visual Breakdown of weekly stats Card ---
@Composable
fun WeeklyBreakdownCard(
    row: JournalRow,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val log = row.log
    val monDateStr = LocalDate.parse(log.startDate)
    val fShort = DateTimeFormatter.ofPattern("dd.MM")
    val displayRange = "${monDateStr.format(fShort)} - ${monDateStr.plusDays(6).format(fShort)} | ${monDateStr.year} г."
    val weekOfMonth = ((monDateStr.dayOfMonth - 1) / 7) + 1
    val weekNumStr = "$weekOfMonth неделя"

    Card(
        modifier = Modifier.fillMaxWidth().testTag("weekly_breakdown_${log.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$displayRange • $weekNumStr",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Править", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Стереть", modifier = Modifier.size(16.dp), tint = Rose500)
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // Weekly schedule display grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val daysShort = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
                val loads = log.getLoads()
                val holidays = log.getHolidays()
                val baseDate = try { LocalDate.parse(log.startDate) } catch(e: Exception) { null }
                
                daysShort.zip(loads).zip(holidays).forEachIndexed { index, (dayAndLoad, isHoliday) ->
                    val (day, load) = dayAndLoad
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = day,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isHoliday) Rose500 else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        if (baseDate != null) {
                            Text(
                                text = baseDate.plusDays(index.toLong()).dayOfMonth.toString(),
                                fontSize = 10.sp,
                                color = if (isHoliday) Rose500.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Spacer(modifier = Modifier.height(13.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    color = when (load) {
                                        "В" -> Amber100
                                        "—" -> MaterialTheme.colorScheme.surfaceVariant
                                        else -> MaterialTheme.colorScheme.primaryContainer
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = load,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (load) {
                                    "В" -> Amber500
                                    "—" -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }
            }

            val baseDate = try { LocalDate.parse(log.startDate) } catch(e: Exception) { null }
            val restDaysList = log.getLoads().mapIndexedNotNull { index, load ->
                if (load == "В" && baseDate != null) {
                    baseDate.plusDays(index.toLong()).dayOfMonth.toString()
                } else null
            }
            val restDaysDisplay = if (restDaysList.isEmpty()) "—" else restDaysList.joinToString(", ")
            val restHoursDisplay = log.calculateRestHours().toInt()

            Spacer(modifier = Modifier.height(10.dp))

            // Display of rest days/dates & summary hours
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🌴 Выходные (д): ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = restDaysDisplay,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Amber500
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Выходные (ч): ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$restHoursDisplay ч.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Calculated Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Overtime Badge (Col 6)
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = if (row.col6 > 0) Emerald100 else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Переработал", fontSize = 10.sp, color = if (row.col6 > 0) Emerald600 else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("+${row.col6} ч.", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (row.col6 > 0) Emerald600 else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("сверх 40ч нор.", fontSize = 9.sp, color = if (row.col6 > 0) Emerald600 else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Weekend work Badge (Col 7)
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = if (row.col7 > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Выходные", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        Text("${row.col7} ч.", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("работа в вых/пр", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                // Accumulated running balance badge (Col 13)
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = Emerald500
                ) {
                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Переработка", fontSize = 10.sp, color = Color.White)
                        Text("${row.col13} ч.", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("текущий баланс", fontSize = 9.sp, color = Color.White)
                    }
                }
            }

            // Checks and alarms
            if (row.penaltyApplied > 0.0 || row.col12 > 0.0 || row.monthlyRestDaysCount > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        if (row.monthlyRestDaysCount > 0) {
                            Text(
                                text = "📅 Использовано выходных («В») за месяц: ${row.monthlyRestDaysCount} из 6 дн.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = if (row.monthlyRestDaysCount > 6) Rose500 else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (row.penaltyApplied > 0.0) {
                            Text(
                                text = "⚠️ Превышена норма выходных (>6 в мес): списано -${row.penaltyApplied} ч. переработки",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Rose500
                            )
                        }
                        if (row.col12 > 0.0) {
                            Text(
                                text = "ℹ️ Предоставлены доп. сутки отдыха: списано -${row.col12} ч.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Horizontal Scrollable Spreadsheet mimic of 13 columns ---
@Composable
fun JournalTable(
    rows: List<JournalRow>,
    employee: Employee,
    onEdit: (JournalRow) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(scrollState)
            .padding(8.dp)
    ) {
        // Table Header
        Row(
            modifier = Modifier
                .background(ElegantSurface)
                .border(1.dp, ElegantSurfaceVariant)
                .padding(vertical = 10.dp)
        ) {
            val headers = listOf(
                Pair("1. №", 45.dp),
                Pair("2. Должность", 130.dp),
                Pair("3. Звание", 80.dp),
                Pair("4. ФИО", 150.dp),
                Pair("5. Дата диапазон", 110.dp),
                Pair("6. Будни сверх н. (ч)", 125.dp),
                Pair("7. В вых/пр дни (ч)", 125.dp),
                Pair("8. Суммарная нагр (ч)", 135.dp),
                Pair("9. Дни отдыха (дата)", 130.dp),
                Pair("10. Дни отдыха (ч)", 125.dp),
                Pair("11. Доп. отдых (дата)", 135.dp),
                Pair("12. Доп. отдых (ч)", 125.dp),
                Pair("13. Нереализ. ост (ч)", 135.dp),
                Pair("Действие", 70.dp)
            )

            headers.forEach { (text, width) ->
                Text(
                    text = text,
                    modifier = Modifier.width(width),
                    color = ElegantDarkText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Table Rows
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(rows) { row ->
                Row(
                    modifier = Modifier
                        .border(1.dp, ElegantSurfaceVariant.copy(alpha = 0.5f))
                        .background(if (row.index % 2 == 0) ElegantDarkBg else ElegantSurface)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableCell(text = "${row.index}", width = 45.dp)
                    TableCell(text = employee.position, width = 130.dp, textAlign = TextAlign.Start)
                    TableCell(text = employee.employeeNumber, width = 80.dp)
                    TableCell(text = employee.name, width = 150.dp, textAlign = TextAlign.Start)
                    TableCell(text = DateUtilsShort(row.log.startDate), width = 110.dp)
                    TableCell(text = "+${row.col6}", width = 125.dp, fontWeight = FontWeight.Bold, color = if (row.col6 > 0) Emerald600 else Color.Unspecified)
                    TableCell(text = "${row.col7}", width = 125.dp)
                    TableCell(text = "${row.col8}", width = 135.dp)
                    TableCell(text = row.col9.ifEmpty { "—" }, width = 130.dp)
                    TableCell(text = "${row.col10}", width = 125.dp)
                    TableCell(text = row.col11.ifEmpty { "—" }, width = 135.dp)
                    TableCell(text = "${row.col12}", width = 125.dp, color = if (row.col12 > 0) Rose500 else Color.Unspecified)
                    Column(
                        modifier = Modifier
                            .width(135.dp)
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${row.col13} ч.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Emerald600,
                            textAlign = TextAlign.Center
                        )
                        if (row.penaltyApplied > 0.0) {
                            Text(
                                text = "(-${row.penaltyApplied} ч. вых.)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Rose500,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Box(modifier = Modifier.width(70.dp), contentAlignment = Alignment.Center) {
                        IconButton(onClick = { onEdit(row) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Править", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TableCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    textAlign: TextAlign = TextAlign.Center,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = Color.Unspecified
) {
    Text(
        text = text,
        modifier = Modifier.width(width).padding(horizontal = 4.dp),
        fontSize = 12.sp,
        fontWeight = fontWeight,
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

// Quick helper to display dd.MM - dd.MM of a starting Monday string
fun DateUtilsShort(startDateStr: String): String {
    return try {
        val monday = LocalDate.parse(startDateStr)
        val formatter = DateTimeFormatter.ofPattern("dd.MM")
        "${monday.format(formatter)} - ${monday.plusDays(6).format(formatter)}"
    } catch(e: Exception) {
        "—"
    }
}


// --- Week schedule creator/editor Dialog ---
@Composable
fun WeeklyScheduleDialog(
    viewModel: OverworkViewModel,
    employeeId: Int,
    startDateString: String,
    existingLog: WeeklyLog?,
    onDismiss: () -> Unit,
    onSave: (String, List<String>, List<Boolean>, String, Double, Double?) -> Unit
) {
    var selectedMondayDate by remember { mutableStateOf(LocalDate.parse(startDateString)) }
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("overwork_drafts_prefs", Context.MODE_PRIVATE) }
    val draftKeyPrefix = "weekly_log_${employeeId}_${selectedMondayDate.format(formatter)}_"

    // Loads state for Monday-Sunday: "Р", "ВГ", "П1", "Ф", "В", "—"
    val loads = remember(selectedMondayDate) {
        val draftStr = sharedPrefs.getString("weekly_log_${employeeId}_${selectedMondayDate.format(formatter)}_loads", null)
        val draftList = draftStr?.split(",")?.filter { it.isNotEmpty() }
        val initialList = if (draftList != null && draftList.size == 7) {
            draftList
        } else {
            listOf(
                existingLog?.monLoad ?: "—",
                existingLog?.tueLoad ?: "—",
                existingLog?.wedLoad ?: "—",
                existingLog?.thuLoad ?: "—",
                existingLog?.friLoad ?: "—",
                existingLog?.satLoad ?: "—",
                existingLog?.sunLoad ?: "—"
            )
        }
        mutableStateListOf<String>().apply { addAll(initialList) }
    }

    // Holiday statuses for Monday-Sunday
    val holidays = remember(selectedMondayDate) {
        val draftStr = sharedPrefs.getString("weekly_log_${employeeId}_${selectedMondayDate.format(formatter)}_holidays", null)
        val draftList = draftStr?.split(",")?.filter { it.isNotEmpty() }?.map { it == "1" }
        val initialList = if (draftList != null && draftList.size == 7) {
            draftList
        } else {
            listOf(
                existingLog?.monHoliday ?: false,
                existingLog?.tueHoliday ?: false,
                existingLog?.wedHoliday ?: false,
                existingLog?.thuHoliday ?: false,
                existingLog?.friHoliday ?: false,
                existingLog?.satHoliday ?: true,
                existingLog?.sunHoliday ?: true
            )
        }
        mutableStateListOf<Boolean>().apply { addAll(initialList) }
    }

    var addRestDate by remember(selectedMondayDate) {
        mutableStateOf(
            sharedPrefs.getString("weekly_log_${employeeId}_${selectedMondayDate.format(formatter)}_add_rest_date", null)
                ?: existingLog?.additionalRestDaysDate
                ?: ""
        )
    }
    var addRestHoursStr by remember(selectedMondayDate) {
        mutableStateOf(
            sharedPrefs.getString("weekly_log_${employeeId}_${selectedMondayDate.format(formatter)}_add_rest_hours", null)
                ?: existingLog?.additionalRestDaysHours?.toString()
                ?: "0.0"
        )
    }
    var col13OverrideStr by remember(selectedMondayDate) {
        mutableStateOf(
            sharedPrefs.getString("weekly_log_${employeeId}_${selectedMondayDate.format(formatter)}_col13_override", null)
                ?: existingLog?.col13Override?.toString()
                ?: ""
        )
    }

    LaunchedEffect(selectedMondayDate, loads.toList()) {
        sharedPrefs.edit().putString(draftKeyPrefix + "loads", loads.toList().joinToString(",")).apply()
    }
    LaunchedEffect(selectedMondayDate, holidays.toList()) {
        sharedPrefs.edit().putString(draftKeyPrefix + "holidays", holidays.toList().map { if (it) "1" else "0" }.joinToString(",")).apply()
    }
    LaunchedEffect(selectedMondayDate, addRestDate) {
        sharedPrefs.edit().putString(draftKeyPrefix + "add_rest_date", addRestDate).apply()
    }
    LaunchedEffect(selectedMondayDate, addRestHoursStr) {
        sharedPrefs.edit().putString(draftKeyPrefix + "add_rest_hours", addRestHoursStr).apply()
    }
    LaunchedEffect(selectedMondayDate, col13OverrideStr) {
        sharedPrefs.edit().putString(draftKeyPrefix + "col13_override", col13OverrideStr).apply()
    }

    fun clearDrafts() {
        sharedPrefs.edit()
            .remove(draftKeyPrefix + "loads")
            .remove(draftKeyPrefix + "holidays")
            .remove(draftKeyPrefix + "add_rest_date")
            .remove(draftKeyPrefix + "add_rest_hours")
            .remove(draftKeyPrefix + "col13_override")
            .apply()
    }

    val daysLabelRus = listOf("Понедельник Пн", "Вторник Вт", "Среда Ср", "Четверг Чт", "Пятница Пт", "Суббота Сб", "Воскресенье Вс")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Text(
                    text = if (existingLog == null) "Заполнить часы недели" else "Редактировать часы недели",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Date Picker controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { selectedMondayDate = selectedMondayDate.minusWeeks(1) },
                        enabled = existingLog == null // prohibit shifting dates for existing entries to prevent messing keys
                    ) { Icon(Icons.Default.ChevronLeft, contentDescription = "Назад") }

                    val displayMon = selectedMondayDate.format(DateTimeFormatter.ofPattern("dd.MM"))
                    val displaySun = selectedMondayDate.plusDays(6).format(DateTimeFormatter.ofPattern("dd.MM"))
                    val displayYear = selectedMondayDate.year
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Неделя периода:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text("$displayMon - $displaySun | $displayYear г.", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    IconButton(
                        onClick = { selectedMondayDate = selectedMondayDate.plusWeeks(1) },
                        enabled = existingLog == null
                    ) { Icon(Icons.Default.ChevronRight, contentDescription = "Вперед") }
                }

                // Copy schedule helper (only if creating fresh)
                if (existingLog == null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            // Copy standard default schedule:
                            // We can just set Mon-Fri as "Р" (Working) and Sat-Sun as "—"
                            for (i in 0..4) {
                                loads[i] = "Р"
                                holidays[i] = false
                            }
                            loads[5] = "—"
                            loads[6] = "—"
                            holidays[5] = true
                            holidays[6] = true
                        },
                        modifier = Modifier.fillMaxWidth().testTag("apply_defaults_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Заполнить по умолчанию (Пн-Пт: Р, Сб-Вс: Выходные)", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Daily Grid inputs
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(7) { index ->
                        val dayLabel = daysLabelRus[index]
                        val dayDateStr = selectedMondayDate.plusDays(index.toLong()).format(DateTimeFormatter.ofPattern("dd.MM"))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$dayLabel ($dayDateStr)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (holidays[index]) Rose500 else MaterialTheme.colorScheme.onSurface
                                    )
                                    // Holiday Toggle
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Вых/Праздник", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        Checkbox(
                                            checked = holidays[index],
                                            onCheckedChange = { holidays[index] = it },
                                            modifier = Modifier.scale(0.8f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Designation rounded chips selector
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val dutyRulesState by viewModel.dutyRules.collectAsStateWithLifecycle()
                                    val options = dutyRulesState.map { it.symbol }
                                    options.forEach { option ->
                                        val isSelected = loads[index] == option
                                        Surface(
                                            modifier = Modifier
                                                .widthIn(min = 46.dp)
                                                .clickable { loads[index] = option }
                                                .testTag("day_${index}_chip_$option"),
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            border = if (isSelected) null else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = option,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Columns 11 and 12 (Additional holiday custom rest)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Учёт предоставленных дополнительных суток отдыха (при наличии)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                OutlinedTextField(
                                    value = addRestDate,
                                    onValueChange = { addRestDate = it },
                                    label = { Text("Дата дополнительных суток (Кол 11)") },
                                    placeholder = { Text("например: 08.05") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("additional_rest_date_input")
                                )
                                OutlinedTextField(
                                    value = addRestHoursStr,
                                    onValueChange = { addRestHoursStr = it },
                                    label = { Text("Часы предоставленных суток (Кол 12)") },
                                    placeholder = { Text("например: 8.0") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("additional_rest_hours_input")
                                )
                            }
                        }
                    }

                    // Column 13 Balance Override
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Переопределение итогового баланса переработки", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                OutlinedTextField(
                                    value = col13OverrideStr,
                                    onValueChange = { col13OverrideStr = it },
                                    label = { Text("Переработка (Кол 13 принудительно)") },
                                    placeholder = { Text("Оставьте пустым для авторасчёта") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("col13_override_input")
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            clearDrafts()
                            onDismiss()
                        }
                    ) { Text("Отмена") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val addHours = addRestHoursStr.toDoubleOrNull() ?: 0.0
                            val col13Override = col13OverrideStr.toDoubleOrNull()
                            clearDrafts()
                            onSave(
                                selectedMondayDate.format(formatter),
                                loads.toList(),
                                holidays.toList(),
                                addRestDate,
                                addHours,
                                col13Override
                            )
                        },
                        modifier = Modifier.testTag("save_weekly_log_button")
                    ) { Text("Сохранить и рассчитать") }
                }
            }
        }
    }
}


// --- Info Screen (Russian Rules & Designation guide) ---
@Composable
fun InfoScreen(isWideScreen: Boolean, viewModel: OverworkViewModel) {
    val githubOwner by viewModel.githubOwner.collectAsStateWithLifecycle()
    val githubRepo by viewModel.githubRepo.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val dutyRules by viewModel.dutyRules.collectAsStateWithLifecycle()

    var showAddRuleDialog by remember { mutableStateOf(false) }
    var ruleToEdit by remember { mutableStateOf<DutyRule?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Справка и правила расчетов",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Это приложение полностью автоматизирует громоздкий расчет 13 колонок ведомости переработок.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Автоматическое обновление",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "Сравните текущую установленную версию приложения с выпусками на GitHub, при наличии обновы APK скачается и установится автоматически.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    var ownerInput by remember(githubOwner) { mutableStateOf(githubOwner) }
                    var repoInput by remember(githubRepo) { mutableStateOf(githubRepo) }
                    val currentAppVersion = viewModel.getAppVersion()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Установленная версия:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "v$currentAppVersion",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    Text("Репозиторий обновлений на GitHub:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = ownerInput,
                            onValueChange = {
                                ownerInput = it
                                viewModel.setGithubConfig(it, repoInput)
                            },
                            label = { Text("Владелец (Owner)") },
                            modifier = Modifier.weight(1f).testTag("update_owner_input"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = repoInput,
                            onValueChange = {
                                repoInput = it
                                viewModel.setGithubConfig(ownerInput, it)
                            },
                            label = { Text("Репозиторий (Repo)") },
                            modifier = Modifier.weight(1f).testTag("update_repo_input"),
                            singleLine = true
                        )
                    }

                    when (val state = updateState) {
                        is UpdateState.Checking -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Проверка версий на GitHub...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        is UpdateState.UpToDate -> {
                            Text("✨ У вас установлена актуальная версия приложения!", fontSize = 12.sp, color = Emerald600, fontWeight = FontWeight.Medium)
                        }
                        is UpdateState.UpdateAvailable -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("🚀 Найдена новая версия: ${state.latestVersion}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("Нажмите кнопку ниже для быстрой автоматической установки.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(
                                        onClick = { viewModel.downloadAndInstallUpdate(state.downloadUrl) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Скачать и обновить (${state.latestVersion})")
                                    }
                                }
                            }
                        }
                        is UpdateState.Downloading -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("📥 Загрузка файла (${(state.progress * 100).toInt()}%)...", fontSize = 12.sp)
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                )
                            }
                        }
                        is UpdateState.ReadyToInstall -> {
                            Button(
                                onClick = { viewModel.installApk(state.apkFile) },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Запустить установку APK")
                             }
                        }
                        is UpdateState.Error -> {
                            Text("❌ Не удалось обновиться: ${state.message}", fontSize = 12.sp, color = Rose500)
                        }
                        else -> {
                            // Idle state
                        }
                    }

                    Button(
                        onClick = { viewModel.checkForUpdates() },
                        modifier = Modifier.fillMaxWidth().testTag("check_updates_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Проверить обновления сейчас")
                    }
                }
            }
        }

        if (isWideScreen) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📋 Обозначения рабочей нагрузки",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Button(
                                    onClick = { showAddRuleDialog = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp).testTag("add_rule_btn_wide")
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Добавить наряд", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            dutyRules.forEachIndexed { index, rule ->
                                RatingItemWithActions(
                                    rule = rule,
                                    onEdit = { ruleToEdit = rule },
                                    onDelete = { viewModel.deleteDutyRule(rule) }
                                )
                                if (index < dutyRules.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("⚖️ Законы и правила вычислений", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                            
                            Text(
                                text = "1. Норма еженедельной работы: 40 часов.\n" +
                                       "Все часы работы (Колонка 8), отработанные поверх 40 часов в неделю, автоматически рассчитываются как переработка и идут в Колонку 6, а затем суммируются в итоговый нереализованный баланс (Колонка 13).",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Text(
                                text = "2. Месячная норма выходных дней: 6 дней.\n" +
                                       "В соответствии с правилами, если сотрудник берет более 6 выходных дней («В») в течении одного календарного месяца, за каждый выходной день сверх нормы (начиная с 7-го) списывается ровно 8 часов из накопленной переработки (Колонка 13). Списание производится автоматически в логе той недели, на которую пришелся лишний выходной.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "3. Дополнительные сутки отдыха (Колонки 11-12):\n" +
                                       "По запросу сотрудника ему могут предоставляться отгулы из накопленного пула. При заполнении Колонки 12 (например, 8, 16 часов отгула) эти часы вычитаются прямо из нереализованной переработки (Колонка 13), уменьшая долг.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📋 Обозначения рабочей нагрузки",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Button(
                                onClick = { showAddRuleDialog = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp).testTag("add_rule_btn_mobile")
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Создать", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        dutyRules.forEachIndexed { index, rule ->
                            RatingItemWithActions(
                                rule = rule,
                                onEdit = { ruleToEdit = rule },
                                onDelete = { viewModel.deleteDutyRule(rule) }
                            )
                            if (index < dutyRules.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("⚖️ Законы и правила вычислений", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                        
                        Text(
                            text = "1. Норма еженедельной работы: 40 часов.\n" +
                                   "Все часы работы (Колонка 8), отработанные поверх 40 часов в неделю, автоматически рассчитываются как переработка и идут в Колонку 6, а затем суммируются в итоговый нереализованный баланс (Колонка 13).",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Text(
                            text = "2. Месячная норма выходных дней: 6 дней.\n" +
                                   "В соответствии с правилами, если сотрудник берет более 6 выходных дней («В») в течении одного календарного месяца, за каждый выходной день сверх нормы (начиная с 7-го) списывается ровно 8 часов из накопленной переработки (Колонка 13). Списание производится автоматически в логе той недели, на которую пришелся лишний выходной.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "3. Дополнительные сутки отдыха (Колонки 11-12):\n" +
                                   "По запросу сотрудника ему могут предоставляться отгулы из накопленного пула. При заполнении Колонки 12 (например, 8, 16 часов отгула) эти часы вычитаются прямо из нереализованной переработки (Колонка 13), уменьшая долг.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    if (showAddRuleDialog) {
        RuleDialog(
            initialRule = null,
            onDismiss = { showAddRuleDialog = false },
            onSave = {
                viewModel.insertDutyRule(it)
                showAddRuleDialog = false
            }
        )
    }

    ruleToEdit?.let { rule ->
        RuleDialog(
            initialRule = rule,
            onDismiss = { ruleToEdit = null },
            onSave = {
                viewModel.updateDutyRule(it)
                ruleToEdit = null
            }
        )
    }
}

@Composable
fun RatingItemWithActions(
    rule: DutyRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("rule_row_${rule.symbol}"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rule.symbol,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = rule.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Будни: ${rule.weekdayHours.toString().removeSuffix(".0")} ч | Праздники: ${rule.holidayHours.toString().removeSuffix(".0")} ч",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (rule.description.isNotEmpty()) {
                    Text(
                        text = rule.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp).testTag("edit_rule_${rule.symbol}")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Редактировать",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (!rule.isSystem) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp).testTag("delete_rule_${rule.symbol}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Удалить",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(36.dp))
            }
        }
    }
}

@Composable
fun RuleDialog(
    initialRule: DutyRule?,
    onDismiss: () -> Unit,
    onSave: (DutyRule) -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("overwork_drafts_prefs", Context.MODE_PRIVATE) }
    val draftKeyPrefix = if (initialRule == null) "create_rule_" else "edit_rule_${initialRule.symbol}_"

    var symbol by remember {
        mutableStateOf(sharedPrefs.getString(draftKeyPrefix + "symbol", null) ?: initialRule?.symbol ?: "")
    }
    var name by remember {
        mutableStateOf(sharedPrefs.getString(draftKeyPrefix + "name", null) ?: initialRule?.name ?: "")
    }
    var description by remember {
        mutableStateOf(sharedPrefs.getString(draftKeyPrefix + "description", null) ?: initialRule?.description ?: "")
    }
    var weekdayHours by remember {
        mutableStateOf(sharedPrefs.getString(draftKeyPrefix + "weekday_hours", null) ?: initialRule?.weekdayHours?.toString()?.removeSuffix(".0") ?: "8")
    }
    var holidayHours by remember {
        mutableStateOf(sharedPrefs.getString(draftKeyPrefix + "holiday_hours", null) ?: initialRule?.holidayHours?.toString()?.removeSuffix(".0") ?: "8")
    }

    LaunchedEffect(symbol) {
        sharedPrefs.edit().putString(draftKeyPrefix + "symbol", symbol).apply()
    }
    LaunchedEffect(name) {
        sharedPrefs.edit().putString(draftKeyPrefix + "name", name).apply()
    }
    LaunchedEffect(description) {
        sharedPrefs.edit().putString(draftKeyPrefix + "description", description).apply()
    }
    LaunchedEffect(weekdayHours) {
        sharedPrefs.edit().putString(draftKeyPrefix + "weekday_hours", weekdayHours).apply()
    }
    LaunchedEffect(holidayHours) {
        sharedPrefs.edit().putString(draftKeyPrefix + "holiday_hours", holidayHours).apply()
    }

    fun clearDrafts() {
        sharedPrefs.edit()
            .remove(draftKeyPrefix + "symbol")
            .remove(draftKeyPrefix + "name")
            .remove(draftKeyPrefix + "description")
            .remove(draftKeyPrefix + "weekday_hours")
            .remove(draftKeyPrefix + "holiday_hours")
            .apply()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialRule == null) "Создать наряд" else "Редактировать наряд")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it.uppercase().take(5).trim() },
                    label = { Text("Обозначение (Символ*)") },
                    placeholder = { Text("например: П2") },
                    enabled = initialRule == null,
                    modifier = Modifier.fillMaxWidth().testTag("rule_dialog_symbol_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название наряда*") },
                    placeholder = { Text("например: Наряд П2") },
                    modifier = Modifier.fillMaxWidth().testTag("rule_dialog_name_input"),
                    singleLine = true
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weekdayHours,
                        onValueChange = { weekdayHours = it },
                        label = { Text("Часы в будни") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("rule_dialog_weekday_hours_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = holidayHours,
                        onValueChange = { holidayHours = it },
                        label = { Text("В праздники") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("rule_dialog_holiday_hours_input"),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    placeholder = { Text("Краткое пояснение расчета нагрузки") },
                    modifier = Modifier.fillMaxWidth().testTag("rule_dialog_desc_input"),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val wdHours = weekdayHours.toDoubleOrNull() ?: 0.0
                    val hdHours = holidayHours.toDoubleOrNull() ?: 0.0
                    if (symbol.isNotBlank() && name.isNotBlank()) {
                        clearDrafts()
                        onSave(
                            DutyRule(
                                symbol = symbol,
                                name = name,
                                description = description,
                                weekdayHours = wdHours,
                                holidayHours = hdHours,
                                isSystem = initialRule?.isSystem ?: false
                            )
                        )
                    }
                },
                enabled = symbol.isNotBlank() && name.isNotBlank(),
                modifier = Modifier.testTag("rule_dialog_save_btn")
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    clearDrafts()
                    onDismiss()
                },
                modifier = Modifier.testTag("rule_dialog_cancel_btn")
            ) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun RatingItem(symbol: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
    }
}

@Composable
fun FancyFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.padding(2.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
