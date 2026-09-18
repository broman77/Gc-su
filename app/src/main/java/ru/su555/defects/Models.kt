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
        ProjectSection(building = 1, section = 1, apartmentFrom = 1, apartmentTo = 161),
        ProjectSection(building = 1, section = 2, apartmentFrom = 162, apartmentTo = 269),
        ProjectSection(building = 2, section = 1, apartmentFrom = 1, apartmentTo = 161),
        ProjectSection(building = 2, section = 2, apartmentFrom = 162, apartmentTo = 299)
    )

    val totalApartments: Int = sections.sumOf { it.apartmentCount }

    fun sectionFor(building: Int, apartment: Int): Int? =
        sections.firstOrNull {
            it.building == building && apartment in it.apartmentFrom..it.apartmentTo
        }?.section

    fun isValidApartment(building: Int, apartment: Int): Boolean =
        sectionFor(building, apartment) != null
}

data class Defect(
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
    val sourceRow: Int
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
    val overdue: Int,
    val closed: Int,
    val nearestDue: LocalDate?,
    val maxOverdueDays: Long
) {
    val title: String get() = "Корпус $building · секция $section · кв. $apartment"
}

data class SectionSummary(
    val building: Int,
    val section: Int,
    val apartmentCapacity: Int,
    val apartmentsInRegister: Int,
    val apartmentsWithOpen: Int,
    val totalDefects: Int,
    val open: Int,
    val attention: Int,
    val reportedNotDone: Int,
    val overdue: Int,
    val closed: Int
) {
    val title: String get() = "Корпус $building · секция $section"
    val completionPercent: Int
        get() = if (totalDefects == 0) 0 else ((closed * 100.0) / totalDefects).toInt()
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
    val totalDefects: Int,
    val plainOpen: Int,
    val attention: Int,
    val reportedNotDone: Int,
    val overdue: Int,
    val closed: Int,
    val completionPercent: Int
) {
    val open: Int get() = plainOpen + attention + reportedNotDone
}

fun List<Defect>.dashboard(today: LocalDate = LocalDate.now()): Dashboard {
    val grouped = groupBy { Triple(it.building, it.section, it.apartment) }
    val closed = count { it.isClosed }
    return Dashboard(
        totalApartments = MironovskayaProject.totalApartments,
        apartmentsInRegister = grouped.size,
        apartmentsWithOpen = grouped.count { (_, rows) -> rows.any { !it.isClosed } },
        apartmentsWithOverdue = grouped.count { (_, rows) -> rows.any { it.isOverdue(today) } },
        totalDefects = size,
        plainOpen = count { it.status == DefectStatus.OPEN },
        attention = count { it.status == DefectStatus.ATTENTION },
        reportedNotDone = count { it.status == DefectStatus.REPORTED_NOT_DONE },
        overdue = count { it.isOverdue(today) },
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
                overdue = rows.count { it.isOverdue(today) },
                closed = rows.count { it.isClosed },
                nearestDue = openRows.mapNotNull { it.dueDate }.minOrNull(),
                maxOverdueDays = rows.maxOfOrNull { it.overdueDays(today) } ?: 0
            )
        }
        .sortedWith(
            compareByDescending<ApartmentSummary> { it.overdue }
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
            totalDefects = rows.size,
            open = rows.count { !it.isClosed },
            attention = rows.count { it.status == DefectStatus.ATTENTION },
            reportedNotDone = rows.count { it.status == DefectStatus.REPORTED_NOT_DONE },
            overdue = rows.count { it.isOverdue(today) },
            closed = rows.count { it.isClosed }
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

fun List<Defect>.byResponsible(today: LocalDate = LocalDate.now()): List<CategorySummary> =
    groupBy { it.responsible.ifBlank { "Не указан" } }
        .map { (name, rows) ->
            CategorySummary(
                name = name,
                total = rows.size,
                open = rows.count { !it.isClosed },
                overdue = rows.count { it.isOverdue(today) },
                closed = rows.count { it.isClosed }
            )
        }
        .sortedByDescending { it.open }
