package ru.su555.defects

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Defect(
    val apartment: String,
    val description: String,
    val category: String,
    val responsible: String,
    val statusText: String,
    val dueDate: LocalDate?,
    val createdDate: LocalDate?,
    val sourceSheet: String,
    val sourceRow: Int
) {
    val isClosed: Boolean
        get() {
            val s = statusText.trim().lowercase()
            if (s.isBlank()) return false
            return listOf(
                "устран", "выполн", "закры", "готов", "заверш", "принят",
                "done", "closed", "completed", "100%", "да"
            ).any { s.contains(it) } || s == "+" || s == "1"
        }

    fun isOverdue(today: LocalDate = LocalDate.now()): Boolean =
        !isClosed && dueDate?.isBefore(today) == true

    fun overdueDays(today: LocalDate = LocalDate.now()): Long =
        if (isOverdue(today)) ChronoUnit.DAYS.between(dueDate, today) else 0L
}

data class ApartmentSummary(
    val apartment: String,
    val total: Int,
    val open: Int,
    val overdue: Int,
    val closed: Int,
    val nearestDue: LocalDate?,
    val maxOverdueDays: Long
)

data class CategorySummary(
    val name: String,
    val total: Int,
    val open: Int,
    val overdue: Int,
    val closed: Int
)

data class Dashboard(
    val total: Int,
    val open: Int,
    val overdue: Int,
    val closed: Int,
    val apartments: Int,
    val apartmentsWithOverdue: Int,
    val completionPercent: Int
)

fun List<Defect>.dashboard(today: LocalDate = LocalDate.now()): Dashboard {
    val closed = count { it.isClosed }
    val overdue = count { it.isOverdue(today) }
    val open = count { !it.isClosed }
    val apt = map { it.apartment }.filter { it.isNotBlank() }.distinct()
    val aptOverdue = filter { it.isOverdue(today) }.map { it.apartment }.filter { it.isNotBlank() }.distinct()
    return Dashboard(
        total = size,
        open = open,
        overdue = overdue,
        closed = closed,
        apartments = apt.size,
        apartmentsWithOverdue = aptOverdue.size,
        completionPercent = if (isEmpty()) 0 else ((closed * 100.0) / size).toInt()
    )
}

fun List<Defect>.byApartment(today: LocalDate = LocalDate.now()): List<ApartmentSummary> =
    groupBy { it.apartment.ifBlank { "Без номера" } }
        .map { (apt, rows) ->
            val openRows = rows.filter { !it.isClosed }
            ApartmentSummary(
                apartment = apt,
                total = rows.size,
                open = openRows.size,
                overdue = rows.count { it.isOverdue(today) },
                closed = rows.count { it.isClosed },
                nearestDue = openRows.mapNotNull { it.dueDate }.minOrNull(),
                maxOverdueDays = rows.maxOfOrNull { it.overdueDays(today) } ?: 0
            )
        }
        .sortedWith(
            compareByDescending<ApartmentSummary> { it.overdue }
                .thenByDescending { it.open }
                .thenBy { apartmentSortKey(it.apartment) }
        )

fun List<Defect>.byCategory(today: LocalDate = LocalDate.now()): List<CategorySummary> =
    groupBy { it.category.ifBlank { "Без категории" } }
        .map { (name, rows) ->
            CategorySummary(
                name = name,
                total = rows.size,
                open = rows.count { !it.isClosed },
                overdue = rows.count { it.isOverdue(today) },
                closed = rows.count { it.isClosed }
            )
        }
        .sortedByDescending { it.total }

private fun apartmentSortKey(value: String): String {
    val n = Regex("\\d+").find(value)?.value?.toIntOrNull()
    return if (n != null) n.toString().padStart(8, '0') else "99999999$value"
}
