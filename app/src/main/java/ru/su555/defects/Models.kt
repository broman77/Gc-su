package ru.su555.defects

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class DefectStatus(val title: String) {
    DONE("Выполнено"),
    OPEN("Не выполнено"),
    ATTENTION("Обратить внимание"),
    REPORTED_NOT_DONE("Отчитано, но не выполнено")
}

data class ProjectSection(
    val building: Int,
    val section: Int,
    val apartmentFrom: Int,
    val apartmentTo: Int
) {
    val apartmentCount: Int get() = apartmentTo - apartmentFrom + 1
    val title: String get() = "Корпус $building · секция $section"
}

object MironovskayaProject {
    val sections = listOf(
        ProjectSection(1, 1, 1, 161),
        ProjectSection(1, 2, 162, 269),
        ProjectSection(2, 1, 1, 161),
        ProjectSection(2, 2, 162, 299)
    )

    val totalApartments: Int = sections.sumOf { it.apartmentCount }

    fun sectionFor(building: Int, apartment: Int): Int? =
        sections.firstOrNull {
            it.building == building && apartment in it.apartmentFrom..it.apartmentTo
        }?.section
}

data class Defect(
    val id: Long = 0L,
    val building: Int,
    val section: Int,
    val apartment: Int,
    val address: String,
    val element: String,
    val description: String,
    val responsible: String,
    val status: DefectStatus,
    val dueDate: LocalDate?,
    val sourceSheet: String,
    val sourceRow: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val apartmentLabel: String
        get() = "Корпус $building · секция $section · кв. $apartment"

    val isClosed: Boolean
        get() = status == DefectStatus.DONE

    fun isOverdue(today: LocalDate = LocalDate.now()): Boolean =
        !isClosed && dueDate?.isBefore(today) == true

    fun overdueDays(today: LocalDate = LocalDate.now()): Long =
        if (isOverdue(today)) ChronoUnit.DAYS.between(dueDate, today) else 0L
}

data class ApartmentSummary(
    val building: Int,
    val section: Int,
    val apartment: Int,
    val total: Int,
    val open: Int,
    val plainOpen: Int,
    val attention: Int,
    val reportedNotDone: Int,
    val overdueDefects: Int,
    val closed: Int,
    val nearestDue: LocalDate?,
    val maxOverdueDays: Long
) {
    val title: String get() = "Корпус $building · секция $section · кв. $apartment"
    val hasOverdue: Boolean get() = overdueDefects > 0
}

data class SectionSummary(
    val building: Int,
    val section: Int,
    val apartmentCapacity: Int,
    val apartmentsInRegister: Int,
    val apartmentsWithOpen: Int,
    val apartmentsWithOverdue: Int,
    val totalDefects: Int,
    val openDefects: Int,
    val attention: Int,
    val reportedNotDone: Int,
    val overdueDefects: Int,
    val closedDefects: Int
) {
    val title: String get() = "Корпус $building · секция $section"
    val completionPercent: Int
        get() = if (totalDefects == 0) 0 else ((closedDefects * 100.0) / totalDefects).toInt()
}

data class CategorySummary(
    val name: String,
    val total: Int,
    val open: Int,
    val overdue: Int,
    val closed: Int
)

data class Dashboard(
    val totalApartments: Int,
    val apartmentsInRegister: Int,
    val apartmentsWithOpen: Int,
    val apartmentsWithOverdue: Int,
    val apartmentsFullyDone: Int,
    val totalDefects: Int,
    val plainOpen: Int,
    val attention: Int,
    val reportedNotDone: Int,
    val overdueDefects: Int,
    val closed: Int,
    val completionPercent: Int
) {
    val open: Int get() = plainOpen + attention + reportedNotDone
}

fun List<Defect>.dashboard(today: LocalDate = LocalDate.now()): Dashboard {
    val grouped = groupBy { Triple(it.building, it.section, it.apartment) }
    val closed = count { it.isClosed }
    val withOpen = grouped.count { (_, rows) -> rows.any { !it.isClosed } }
    return Dashboard(
        totalApartments = MironovskayaProject.totalApartments,
        apartmentsInRegister = grouped.size,
        apartmentsWithOpen = withOpen,
        apartmentsWithOverdue = grouped.count { (_, rows) -> rows.any { it.isOverdue(today) } },
        apartmentsFullyDone = grouped.size - withOpen,
        totalDefects = size,
        plainOpen = count { it.status == DefectStatus.OPEN },
        attention = count { it.status == DefectStatus.ATTENTION },
        reportedNotDone = count { it.status == DefectStatus.REPORTED_NOT_DONE },
        overdueDefects = count { it.isOverdue(today) },
        closed = closed,
        completionPercent = if (isEmpty()) 0 else ((closed * 100.0) / size).toInt()
    )
}

fun List<Defect>.byApartment(today: LocalDate = LocalDate.now()): List<ApartmentSummary> =
    groupBy { Triple(it.building, it.section, it.apartment) }
        .map { (key, rows) ->
            val openRows = rows.filter { !it.isClosed }
            ApartmentSummary(
                building = key.first,
                section = key.second,
                apartment = key.third,
                total = rows.size,
                open = openRows.size,
                plainOpen = rows.count { it.status == DefectStatus.OPEN },
                attention = rows.count { it.status == DefectStatus.ATTENTION },
                reportedNotDone = rows.count { it.status == DefectStatus.REPORTED_NOT_DONE },
                overdueDefects = rows.count { it.isOverdue(today) },
                closed = rows.count { it.isClosed },
                nearestDue = openRows.mapNotNull { it.dueDate }.minOrNull(),
                maxOverdueDays = rows.maxOfOrNull { it.overdueDays(today) } ?: 0
            )
        }
        .sortedWith(
            compareByDescending<ApartmentSummary> { it.hasOverdue }
                .thenByDescending { it.reportedNotDone }
                .thenByDescending { it.attention }
                .thenByDescending { it.open }
                .thenBy { it.building }
                .thenBy { it.section }
                .thenBy { it.apartment }
        )

fun List<Defect>.bySection(today: LocalDate = LocalDate.now()): List<SectionSummary> =
    MironovskayaProject.sections.map { projectSection ->
        val rows = filter {
            it.building == projectSection.building && it.section == projectSection.section
        }
        val grouped = rows.groupBy { it.apartment }
        SectionSummary(
            building = projectSection.building,
            section = projectSection.section,
            apartmentCapacity = projectSection.apartmentCount,
            apartmentsInRegister = grouped.size,
            apartmentsWithOpen = grouped.count { (_, defects) -> defects.any { !it.isClosed } },
            apartmentsWithOverdue = grouped.count { (_, defects) -> defects.any { it.isOverdue(today) } },
            totalDefects = rows.size,
            openDefects = rows.count { !it.isClosed },
            attention = rows.count { it.status == DefectStatus.ATTENTION },
            reportedNotDone = rows.count { it.status == DefectStatus.REPORTED_NOT_DONE },
            overdueDefects = rows.count { it.isOverdue(today) },
            closedDefects = rows.count { it.isClosed }
        )
    }

fun List<Defect>.byCategory(today: LocalDate = LocalDate.now()): List<CategorySummary> =
    groupBy { it.element.ifBlank { "Без элемента" } }
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

fun canonicalResponsibleName(value: String): String {
    val cleaned = value
        .trim()
        .replace(Regex("""\s+"""), " ")

    val key = cleaned
        .lowercase()
        .replace('ё', 'е')
        .replace(".", "")
        .trim()

    return when {
        key == "сму" || key.startsWith("мкд") || key.startsWith("стм") -> "СМУ"
        key == "уир" || key.contains("электрик") || key.contains("сантех") -> "УИР"
        else -> cleaned
    }
}

fun splitResponsible(value: String): List<String> {
    if (value.isBlank()) return listOf("Не указан")

    val normalized = value
        .replace("＋", "+")
        .replace("&", "+")
        .replace("/", "+")
        .replace("\\", "+")
        .replace(";", "+")
        .replace(",", "+")
        .replace(Regex("""\s+\+\s+"""), "+")
        .trim()

    val parts = normalized
        .split("+")
        .map { canonicalResponsibleName(it) }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }

    return if (parts.isEmpty()) listOf("Не указан") else parts
}

fun List<Defect>.byResponsible(today: LocalDate = LocalDate.now()): List<CategorySummary> {
    data class Counts(var total: Int = 0, var open: Int = 0, var overdue: Int = 0, var closed: Int = 0)

    val map = linkedMapOf<String, Counts>()

    for (defect in this) {
        for (name in splitResponsible(defect.responsible)) {
            val key = map.keys.firstOrNull { it.equals(name, ignoreCase = true) } ?: name
            val counts = map.getOrPut(key) { Counts() }
            counts.total++
            if (defect.isClosed) counts.closed++ else counts.open++
            if (defect.isOverdue(today)) counts.overdue++
        }
    }

    return map.map { (name, counts) ->
        CategorySummary(
            name = name,
            total = counts.total,
            open = counts.open,
            overdue = counts.overdue,
            closed = counts.closed
        )
    }.sortedWith(
        compareByDescending<CategorySummary> { it.open }
            .thenByDescending { it.overdue }
            .thenBy { it.name.lowercase() }
    )
}

fun List<Defect>.defectsForApartment(
    building: Int,
    section: Int,
    apartment: Int
): List<Defect> =
    filter {
        it.building == building &&
            it.section == section &&
            it.apartment == apartment
    }.sortedWith(
        compareByDescending<Defect> { it.isOverdue() }
            .thenByDescending { it.status == DefectStatus.REPORTED_NOT_DONE }
            .thenByDescending { it.status == DefectStatus.ATTENTION }
            .thenBy { it.element }
    )
