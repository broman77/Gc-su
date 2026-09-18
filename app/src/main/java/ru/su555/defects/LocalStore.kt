package ru.su555.defects

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

data class HistoryItem(
    val id: Long,
    val defectId: Long,
    val timestamp: Long,
    val action: String,
    val details: String
)

data class DefectPhoto(
    val id: Long,
    val defectId: Long,
    val path: String,
    val label: String,
    val createdAt: Long
)

class LocalStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE defects (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                building INTEGER NOT NULL,
                section INTEGER NOT NULL,
                apartment INTEGER NOT NULL,
                address TEXT NOT NULL,
                element TEXT NOT NULL,
                description TEXT NOT NULL,
                responsible TEXT NOT NULL,
                status TEXT NOT NULL,
                due_date TEXT,
                source_sheet TEXT NOT NULL,
                source_row INTEGER NOT NULL,
                priority TEXT NOT NULL DEFAULT 'NORMAL',
                assigned_employee TEXT NOT NULL DEFAULT '',
                reported_at INTEGER,
                verified_at INTEGER,
                completed_at INTEGER,
                archived INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                defect_id INTEGER NOT NULL,
                timestamp INTEGER NOT NULL,
                action TEXT NOT NULL,
                details TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE photos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                defect_id INTEGER NOT NULL,
                path TEXT NOT NULL,
                label TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_defects_apartment ON defects(building, section, apartment)")
        db.execSQL("CREATE INDEX idx_history_defect ON history(defect_id)")
        db.execSQL("CREATE INDEX idx_photos_defect ON photos(defect_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE defects ADD COLUMN priority TEXT NOT NULL DEFAULT 'NORMAL'")
            db.execSQL("ALTER TABLE defects ADD COLUMN assigned_employee TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE defects ADD COLUMN reported_at INTEGER")
            db.execSQL("ALTER TABLE defects ADD COLUMN verified_at INTEGER")
            db.execSQL("ALTER TABLE defects ADD COLUMN completed_at INTEGER")
        }
    }

    fun activeDefects(): List<Defect> = queryDefects(archived = false)

    fun archivedDefects(): List<Defect> = queryDefects(archived = true)

    fun replaceAllFromExcel(defects: List<Defect>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("history", null, null)
            db.delete("photos", null, null)
            db.delete("defects", null, null)

            defects.forEach { defect ->
                val id = db.insertOrThrow("defects", null, defectValues(defect, archived = false))
                insertHistory(
                    db = db,
                    defectId = id,
                    action = "Импорт из Excel",
                    details = "Импортировано из листа «${defect.sourceSheet}», строка ${defect.sourceRow}"
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun addDefect(defect: Defect): Long {
        val now = System.currentTimeMillis()
        val entity = defect.copy(createdAt = now, updatedAt = now)
        val db = writableDatabase
        val id = db.insertOrThrow("defects", null, defectValues(entity, archived = false))
        insertHistory(
            db,
            id,
            "Создано замечание",
            "Добавлено вручную в приложении"
        )
        return id
    }

    fun updateDefect(defect: Defect) {
        require(defect.id > 0) { "Нельзя изменить замечание без id" }
        val before = defectById(defect.id)
        val now = System.currentTimeMillis()
        val updated = defect.copy(
            reportedAt = when {
                defect.status == DefectStatus.REPORTED_NOT_DONE && defect.reportedAt == null -> now
                else -> defect.reportedAt
            },
            verifiedAt = when {
                defect.status == DefectStatus.REPORTED_NOT_DONE && defect.verifiedAt == null -> now
                defect.status == DefectStatus.DONE && defect.verifiedAt == null -> now
                else -> defect.verifiedAt
            },
            completedAt = when {
                defect.status == DefectStatus.DONE && defect.completedAt == null -> now
                else -> defect.completedAt
            },
            updatedAt = now
        )

        writableDatabase.update(
            "defects",
            defectValues(updated, archived = false, includeCreatedAt = false),
            "id=?",
            arrayOf(defect.id.toString())
        )

        val changes = mutableListOf<String>()
        if (before != null) {
            if (before.element != updated.element) changes += "Элемент: «${before.element}» → «${updated.element}»"
            if (before.description != updated.description) changes += "Описание изменено"
            if (before.responsible != updated.responsible) {
                changes += "Подрядчик: «${before.responsible}» → «${updated.responsible}»"
            }
            if (before.status != updated.status) changes += "Статус: ${before.status.title} → ${updated.status.title}"
            if (before.priority != updated.priority) changes += "Приоритет: ${before.priority.title} → ${updated.priority.title}"
            if (before.assignedEmployee != updated.assignedEmployee) {
                changes += "Ответственный: «${before.assignedEmployee}» → «${updated.assignedEmployee}»"
            }
            if (before.dueDate != updated.dueDate) {
                changes += "Срок: ${before.dueDate ?: "не указан"} → ${updated.dueDate ?: "не указан"}"
            }
        }

        insertHistory(
            writableDatabase,
            defect.id,
            "Изменено",
            changes.joinToString("; ").ifBlank { "Данные сохранены" }
        )
    }

    fun setStatus(defectId: Long, status: DefectStatus) {
        val before = defectById(defectId) ?: return
        if (before.status == status) return

        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("status", status.name)
            put("updated_at", now)
            when (status) {
                DefectStatus.REPORTED_NOT_DONE -> {
                    if (before.reportedAt == null) put("reported_at", now)
                    if (before.verifiedAt == null) put("verified_at", now)
                }
                DefectStatus.DONE -> {
                    if (before.verifiedAt == null) put("verified_at", now)
                    if (before.completedAt == null) put("completed_at", now)
                }
                DefectStatus.ATTENTION,
                DefectStatus.OPEN -> Unit
            }
        }
        writableDatabase.update("defects", values, "id=?", arrayOf(defectId.toString()))
        insertHistory(
            writableDatabase,
            defectId,
            "Изменён статус",
            "${before.status.title} → ${status.title}"
        )
    }

    fun restoreSnapshot(defect: Defect) {
        require(defect.id > 0)
        writableDatabase.update(
            "defects",
            defectValues(defect.copy(updatedAt = System.currentTimeMillis()), archived = false, includeCreatedAt = false),
            "id=?",
            arrayOf(defect.id.toString())
        )
        insertHistory(
            writableDatabase,
            defect.id,
            "Отмена действия",
            "Восстановлено предыдущее состояние"
        )
    }

    fun archiveDefect(defectId: Long) {
        val values = ContentValues().apply {
            put("archived", 1)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("defects", values, "id=?", arrayOf(defectId.toString()))
        insertHistory(
            writableDatabase,
            defectId,
            "Перенесено в архив",
            "Замечание скрыто из рабочего реестра"
        )
    }

    fun restoreDefect(defectId: Long) {
        val values = ContentValues().apply {
            put("archived", 0)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("defects", values, "id=?", arrayOf(defectId.toString()))
        insertHistory(
            writableDatabase,
            defectId,
            "Восстановлено из архива",
            "Замечание возвращено в рабочий реестр"
        )
    }

    fun defectById(id: Long): Defect? {
        readableDatabase.query(
            "defects",
            DEFECT_COLUMNS,
            "id=?",
            arrayOf(id.toString()),
            null,
            null,
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursorToDefect(cursor) else null
        }
    }

    fun history(defectId: Long): List<HistoryItem> {
        val result = mutableListOf<HistoryItem>()
        readableDatabase.query(
            "history",
            arrayOf("id", "defect_id", "timestamp", "action", "details"),
            "defect_id=?",
            arrayOf(defectId.toString()),
            null,
            null,
            "timestamp DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += HistoryItem(
                    id = cursor.getLong(0),
                    defectId = cursor.getLong(1),
                    timestamp = cursor.getLong(2),
                    action = cursor.getString(3),
                    details = cursor.getString(4)
                )
            }
        }
        return result
    }

    fun historySince(timestamp: Long): List<HistoryItem> {
        val result = mutableListOf<HistoryItem>()
        readableDatabase.query(
            "history",
            arrayOf("id", "defect_id", "timestamp", "action", "details"),
            "timestamp>=?",
            arrayOf(timestamp.toString()),
            null,
            null,
            "timestamp DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += HistoryItem(
                    id = cursor.getLong(0),
                    defectId = cursor.getLong(1),
                    timestamp = cursor.getLong(2),
                    action = cursor.getString(3),
                    details = cursor.getString(4)
                )
            }
        }
        return result
    }

    fun addPhoto(defectId: Long, path: String, label: String): Long {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("defect_id", defectId)
            put("path", path)
            put("label", label)
            put("created_at", now)
        }
        val id = writableDatabase.insertOrThrow("photos", null, values)
        insertHistory(
            writableDatabase,
            defectId,
            "Добавлено фото",
            label
        )
        return id
    }

    fun photos(defectId: Long): List<DefectPhoto> {
        val result = mutableListOf<DefectPhoto>()
        readableDatabase.query(
            "photos",
            arrayOf("id", "defect_id", "path", "label", "created_at"),
            "defect_id=?",
            arrayOf(defectId.toString()),
            null,
            null,
            "created_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += DefectPhoto(
                    id = cursor.getLong(0),
                    defectId = cursor.getLong(1),
                    path = cursor.getString(2),
                    label = cursor.getString(3),
                    createdAt = cursor.getLong(4)
                )
            }
        }
        return result
    }

    fun removePhoto(photo: DefectPhoto) {
        writableDatabase.delete("photos", "id=?", arrayOf(photo.id.toString()))
        insertHistory(
            writableDatabase,
            photo.defectId,
            "Удалено фото",
            photo.label
        )
    }

    fun archivedCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM defects WHERE archived=1", null).use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    private fun queryDefects(archived: Boolean): List<Defect> {
        val result = mutableListOf<Defect>()
        readableDatabase.query(
            "defects",
            DEFECT_COLUMNS,
            "archived=?",
            arrayOf(if (archived) "1" else "0"),
            null,
            null,
            "building, section, apartment, id"
        ).use { cursor ->
            while (cursor.moveToNext()) result += cursorToDefect(cursor)
        }
        return result
    }

    private fun defectValues(
        defect: Defect,
        archived: Boolean,
        includeCreatedAt: Boolean = true
    ): ContentValues = ContentValues().apply {
        put("building", defect.building)
        put("section", defect.section)
        put("apartment", defect.apartment)
        put("address", defect.address)
        put("element", defect.element)
        put("description", defect.description)
        put("responsible", defect.responsible)
        put("status", defect.status.name)
        if (defect.dueDate == null) putNull("due_date") else put("due_date", defect.dueDate.toString())
        put("source_sheet", defect.sourceSheet)
        put("source_row", defect.sourceRow)
        put("priority", defect.priority.name)
        put("assigned_employee", defect.assignedEmployee)
        if (defect.reportedAt == null) putNull("reported_at") else put("reported_at", defect.reportedAt)
        if (defect.verifiedAt == null) putNull("verified_at") else put("verified_at", defect.verifiedAt)
        if (defect.completedAt == null) putNull("completed_at") else put("completed_at", defect.completedAt)
        put("archived", if (archived) 1 else 0)
        if (includeCreatedAt) put("created_at", defect.createdAt)
        put("updated_at", defect.updatedAt)
    }

    private fun cursorToDefect(cursor: android.database.Cursor): Defect {
        fun string(name: String): String = cursor.getString(cursor.getColumnIndexOrThrow(name))
        fun int(name: String): Int = cursor.getInt(cursor.getColumnIndexOrThrow(name))
        fun long(name: String): Long = cursor.getLong(cursor.getColumnIndexOrThrow(name))
        fun nullableLong(name: String): Long? {
            val index = cursor.getColumnIndexOrThrow(name)
            return if (cursor.isNull(index)) null else cursor.getLong(index)
        }

        val dueRaw = cursor.getString(cursor.getColumnIndexOrThrow("due_date"))
        return Defect(
            id = long("id"),
            building = int("building"),
            section = int("section"),
            apartment = int("apartment"),
            address = string("address"),
            element = string("element"),
            description = string("description"),
            responsible = string("responsible"),
            status = runCatching { DefectStatus.valueOf(string("status")) }.getOrDefault(DefectStatus.OPEN),
            dueDate = dueRaw?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
            sourceSheet = string("source_sheet"),
            sourceRow = int("source_row"),
            priority = runCatching { DefectPriority.valueOf(string("priority")) }.getOrDefault(DefectPriority.NORMAL),
            assignedEmployee = string("assigned_employee"),
            reportedAt = nullableLong("reported_at"),
            verifiedAt = nullableLong("verified_at"),
            completedAt = nullableLong("completed_at"),
            createdAt = long("created_at"),
            updatedAt = long("updated_at")
        )
    }

    private fun insertHistory(
        db: SQLiteDatabase,
        defectId: Long,
        action: String,
        details: String
    ) {
        db.insert(
            "history",
            null,
            ContentValues().apply {
                put("defect_id", defectId)
                put("timestamp", System.currentTimeMillis())
                put("action", action)
                put("details", details)
            }
        )
    }

    companion object {
        private const val DB_NAME = "defects.db"
        private const val DB_VERSION = 2

        private val DEFECT_COLUMNS = arrayOf(
            "id", "building", "section", "apartment", "address", "element",
            "description", "responsible", "status", "due_date", "source_sheet",
            "source_row", "priority", "assigned_employee", "reported_at", "verified_at",
            "completed_at", "created_at", "updated_at"
        )
    }
}
