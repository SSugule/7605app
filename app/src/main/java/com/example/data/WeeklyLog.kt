package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Entity(
    tableName = "weekly_logs",
    foreignKeys = [
        ForeignKey(
            entity = Employee::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("employeeId")]
)
data class WeeklyLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val employeeId: Int,
    val startDate: String, // Дата начала недели (Monday) в формате "yyyy-MM-dd"
    
    // Обозначения для дней недели: "Р", "ВГ", "П1", "Ф", "В", "—"
    val monLoad: String = "—",
    val tueLoad: String = "—",
    val wedLoad: String = "—",
    val thuLoad: String = "—",
    val friLoad: String = "—",
    val satLoad: String = "—",
    val sunLoad: String = "—",
    
    // Флаг выходного или праздничного дня (помимо стандартных Сб/Вс)
    val monHoliday: Boolean = false,
    val tueHoliday: Boolean = false,
    val wedHoliday: Boolean = false,
    val thuHoliday: Boolean = false,
    val friHoliday: Boolean = false,
    val satHoliday: Boolean = true, // По умолчанию Сб - выходной
    val sunHoliday: Boolean = true, // По умолчанию Вс - выходной
    
    // Дополнительные сутки отдыха (колонки 11 и 12)
    val additionalRestDaysDate: String = "", // Дата(ы) дополнительных суток отдыха
    val additionalRestDaysHours: Double = 0.0, // Часы дополнительных суток отдыха
    val col13Override: Double? = null // Ручной ввод переработки (К13)
) {
    
    // Helper to get list of loads
    fun getLoads(): List<String> = listOf(monLoad, tueLoad, wedLoad, thuLoad, friLoad, satLoad, sunLoad)
    
    // Helper to get list of holiday statuses
    fun getHolidays(): List<Boolean> = listOf(monHoliday, tueHoliday, wedHoliday, thuHoliday, friHoliday, satHoliday, sunHoliday)

    // Преобразует строковые обозначения в часы работы для каждого дня
    // Обозначения:
    // «Р» — Рабочий день: Будние — 7 ч, Выходные/праздники — 4 ч
    // «ВГ» — Наряд ВГ: Будние — 30 ч, Выходные/праздники — 29 ч
    // «П1» — Наряд П1: Будние — 30 ч, Выходные/праздники — 29 ч
    // «Ф» — Наряд Ф: Будние — 28 ч, Выходные/праздники — 27 ч
    // «В» — Выходной: 0 ч работы (но 8 ч отдыха в колонку 10)
    // «—» — Ранее отработанный день после суточного наряда (0 ч работы, 0 ч отдыха)
    fun calculateDailyWorkHours(rulesMap: Map<String, DutyRule> = emptyMap()): List<Double> {
        val loads = getLoads()
        val holidays = getHolidays()
        
        return loads.zip(holidays).map { (load, isHoliday) ->
            val cleanLoad = load.trim().uppercase()
            val rule = rulesMap[cleanLoad]
            if (rule != null) {
                if (isHoliday) rule.holidayHours else rule.weekdayHours
            } else {
                when (cleanLoad) {
                    "Р" -> if (isHoliday) 4.0 else 7.0
                    "ВГ", "П1" -> if (isHoliday) 29.0 else 30.0
                    "Ф" -> if (isHoliday) 27.0 else 28.0
                    "В", "B", "—" -> 0.0
                    else -> 0.0
                }
            }
        }
    }

    // Расчет суммарного времени нагрузки за неделю (Колонка 8)
    fun calculateTotalWorkHours(rulesMap: Map<String, DutyRule> = emptyMap()): Double {
        return calculateDailyWorkHours(rulesMap).sum()
    }

    // Расчет времени в выходные и праздничные дни (Колонка 7)
    fun calculateWeekendWorkHours(rulesMap: Map<String, DutyRule> = emptyMap()): Double {
        val dailyHours = calculateDailyWorkHours(rulesMap)
        val holidays = getHolidays()
        
        return dailyHours.zip(holidays)
            .filter { (_, isHoliday) -> isHoliday }
            .sumOf { (hours, _) -> hours }
    }

    // Расчет переработки сверх установленной нормы в 40 часов (Колонка 6)
    // "40 часов в неделю норма работы, всё, что выше идёт в переработку"
    fun calculateWeeklyOvertimeHours(rulesMap: Map<String, DutyRule> = emptyMap()): Double {
        val totalWork = calculateTotalWorkHours(rulesMap)
        return if (totalWork > 40.0) totalWork - 40.0 else 0.0
    }

    // Получение дат дней отдыха «В» (Колонка 9)
    fun calculateRestDates(): List<String> {
        val loads = getLoads()
        
        return try {
            val monday = LocalDate.parse(startDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val formatter = DateTimeFormatter.ofPattern("dd.MM")
            
            loads.mapIndexedNotNull { index, load ->
                val cleanLoad = load.trim().uppercase()
                if (cleanLoad == "В" || cleanLoad == "B") {
                    monday.plusDays(index.toLong()).format(formatter)
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Расчет предоставленного времени отдыха в часах (Колонка 10)
    // "В" = 8 часов отдыха
    fun calculateRestHours(rulesMap: Map<String, DutyRule> = emptyMap()): Double {
        val countV = getLoads().count {
            val cleanLoad = it.trim().uppercase()
            cleanLoad == "В" || cleanLoad == "B"
        }
        return countV * 8.0
    }
}
