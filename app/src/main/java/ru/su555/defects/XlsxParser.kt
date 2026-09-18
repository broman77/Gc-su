package ru.su555.defects

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.zip.ZipInputStream

object XlsxParser {
    data class ParseResult(
        val defects: List<Defect>,
        val warnings: List<String>,
        val detectedColumns: Map<String, String>
    )

    private data class Row(val number: Int, val cells: Map<Int, String>)

    private enum class Field {
        APARTMENT, DEFECT, DUE, STATUS, RESPONSIBLE, CATEGORY, CREATED
    }

    fun parse(input: InputStream): ParseResult {
        val bytes = input.readBytes()
        if (bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4B.toByte()) {
            throw IllegalArgumentException("Поддерживается формат .xlsx. Откройте файл в Excel и сохраните как «Книга Excel (*.xlsx)».")
        }

        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory) entries[e.name] = zip.readBytes()
                zip.closeEntry()
                e = zip.nextEntry
            }
        }

        val shared = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings) ?: emptyList()
        val workbookNames = parseSheetNames(entries["xl/workbook.xml"])
        val sheets = entries
            .filterKeys { it.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
            .toSortedMap(compareBy { name ->
                Regex("\\d+").find(name)?.value?.toIntOrNull() ?: Int.MAX_VALUE
            })

        if (sheets.isEmpty()) throw IllegalArgumentException("В файле не найдены листы Excel.")

        val warnings = mutableListOf<String>()
        val all = mutableListOf<Defect>()
        val globalDetected = linkedMapOf<String, String>()

        sheets.entries.forEachIndexed { index, entry ->
            val sheetName = workbookNames.getOrNull(index) ?: ("Лист " + (index + 1))
            val rows = parseWorksheet(entry.value, shared)
            if (rows.isEmpty()) return@forEachIndexed

            val detection = detectHeader(rows)
            if (detection == null) {
                warnings += "Лист «$sheetName»: не удалось определить строку заголовков — лист пропущен."
                return@forEachIndexed
            }

            val (headerIndex, mapping) = detection
            val headerRow = rows[headerIndex]
            mapping.forEach { (field, col) ->
                val header = headerRow.cells[col].orEmpty()
                globalDetected.putIfAbsent(field.russianName(), header)
            }

            if (mapping[Field.APARTMENT] == null || mapping[Field.DEFECT] == null) {
                warnings += "Лист «$sheetName»: нужны хотя бы колонки «Квартира» и «Замечание»."
                return@forEachIndexed
            }

            for (i in (headerIndex + 1) until rows.size) {
                val row = rows[i]
                val apartment = value(row, mapping[Field.APARTMENT])
                val description = value(row, mapping[Field.DEFECT])
                if (apartment.isBlank() && description.isBlank()) continue
                if (description.isBlank()) continue

                all += Defect(
                    apartment = apartment.trim(),
                    description = description.trim(),
                    category = value(row, mapping[Field.CATEGORY]).trim(),
                    responsible = value(row, mapping[Field.RESPONSIBLE]).trim(),
                    statusText = value(row, mapping[Field.STATUS]).trim(),
                    dueDate = parseDate(value(row, mapping[Field.DUE])),
                    createdDate = parseDate(value(row, mapping[Field.CREATED])),
                    sourceSheet = sheetName,
                    sourceRow = row.number
                )
            }
        }

        if (all.isEmpty()) {
            throw IllegalArgumentException(
                "Замечания не найдены. Проверьте, что в таблице есть колонки с квартирой и описанием замечания."
            )
        }

        if (all.none { it.dueDate != null }) {
            warnings += "Не найдена колонка со сроком устранения или даты не распознаны. Просрочки пока не рассчитаны."
        }
        if (all.none { it.statusText.isNotBlank() }) {
            warnings += "Не найдена колонка статуса. Все замечания считаются открытыми."
        }

        return ParseResult(
            defects = all,
            warnings = warnings,
            detectedColumns = globalDetected
        )
    }

    private fun value(row: Row, col: Int?): String = if (col == null) "" else row.cells[col].orEmpty()

    private fun parseSharedStrings(data: ByteArray): List<String> {
        val result = mutableListOf<String>()
        val parser = newParser(data)
        var insideSi = false
        var current = StringBuilder()

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> {
                        insideSi = true
                        current = StringBuilder()
                    }
                    "t" -> if (insideSi) current.append(parser.nextText())
                }
                XmlPullParser.END_TAG -> if (parser.name == "si") {
                    result += current.toString()
                    insideSi = false
                }
            }
            parser.next()
        }
        return result
    }

    private fun parseWorksheet(data: ByteArray, shared: List<String>): List<Row> {
        val parser = newParser(data)
        val result = mutableListOf<Row>()
        var currentRow = -1
        var currentCells = linkedMapOf<Int, String>()
        var currentColumn = -1
        var currentType = ""
        var currentValue = ""
        var inlineText = StringBuilder()
        var inCell = false

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        currentRow = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: (result.size + 1)
                        currentCells = linkedMapOf()
                    }
                    "c" -> {
                        inCell = true
                        val ref = parser.getAttributeValue(null, "r").orEmpty()
                        currentColumn = columnIndex(ref)
                        currentType = parser.getAttributeValue(null, "t").orEmpty()
                        currentValue = ""
                        inlineText = StringBuilder()
                    }
                    "v" -> if (inCell) currentValue = parser.nextText()
                    "t" -> if (inCell && currentType == "inlineStr") inlineText.append(parser.nextText())
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "c" -> {
                        if (currentColumn >= 0) {
                            val v = when (currentType) {
                                "s" -> currentValue.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                                "inlineStr" -> inlineText.toString()
                                "b" -> if (currentValue == "1") "Да" else "Нет"
                                else -> currentValue
                            }
                            currentCells[currentColumn] = v
                        }
                        inCell = false
                    }
                    "row" -> if (currentRow >= 0) result += Row(currentRow, currentCells.toMap())
                }
            }
            parser.next()
        }
        return result
    }

    private fun parseSheetNames(data: ByteArray?): List<String> {
        if (data == null) return emptyList()
        val parser = newParser(data)
        val names = mutableListOf<String>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                parser.getAttributeValue(null, "name")?.let(names::add)
            }
            parser.next()
        }
        return names
    }

    private fun newParser(data: ByteArray): XmlPullParser =
        XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(ByteArrayInputStream(data), "UTF-8")
        }

    private fun columnIndex(cellRef: String): Int {
        val letters = cellRef.takeWhile { it.isLetter() }.uppercase()
        if (letters.isBlank()) return -1
        var result = 0
        for (ch in letters) result = result * 26 + (ch - 'A' + 1)
        return result - 1
    }

    private fun detectHeader(rows: List<Row>): Pair<Int, Map<Field, Int>>? {
        var bestIndex = -1
        var bestMap = emptyMap<Field, Int>()
        var bestScore = 0

        rows.take(40).forEachIndexed { index, row ->
            val map = linkedMapOf<Field, Int>()
            row.cells.forEach { (col, raw) ->
                val n = normalize(raw)
                detectField(n)?.let { field -> map.putIfAbsent(field, col) }
            }
            var score = map.size
            if (map.containsKey(Field.APARTMENT)) score += 2
            if (map.containsKey(Field.DEFECT)) score += 3
            if (map.containsKey(Field.DUE)) score += 1
            if (score > bestScore) {
                bestScore = score
                bestIndex = index
                bestMap = map
            }
        }

        return if (bestIndex >= 0 && bestScore >= 5) bestIndex to bestMap else null
    }

    private fun detectField(n: String): Field? {
        if (n.isBlank()) return null
        return when {
            matches(n, "квартира", "номер квартиры", "кв", "кв.", "№ квартиры", "номер кв") -> Field.APARTMENT
            matches(n, "замечание", "описание замечания", "дефект", "нарушение", "перечень замечаний", "описание дефекта") -> Field.DEFECT
            matches(n, "срок устранения", "плановый срок", "плановая дата", "дата устранения", "срок до", "срок") -> Field.DUE
            matches(n, "статус", "состояние", "выполнение", "устранено", "исполнение") -> Field.STATUS
            matches(n, "ответственный", "подрядчик", "исполнитель", "организация", "субподрядчик") -> Field.RESPONSIBLE
            matches(n, "вид работ", "раздел", "категория", "тип замечания", "работа", "раздел работ") -> Field.CATEGORY
            matches(n, "дата выявления", "дата замечания", "дата составления", "дата регистрации", "дата") -> Field.CREATED
            else -> null
        }
    }

    private fun matches(value: String, vararg variants: String): Boolean {
        return variants.any { variant ->
            val v = normalize(variant)
            value == v || (v.length >= 4 && value.contains(v))
        }
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace('ё', 'е')
            .replace('№', ' ')
            .replace(Regex("[\\n\\r\\t]+"), " ")
            .replace(Regex("[^а-яa-z0-9%+./ -]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun parseDate(raw: String): LocalDate? {
        val s = raw.trim()
        if (s.isBlank()) return null

        s.replace(',', '.').toDoubleOrNull()?.let { serial ->
            if (serial in 20000.0..80000.0) {
                return LocalDate.of(1899, 12, 30).plusDays(serial.toLong())
            }
        }

        val clean = s.substringBefore(" ").trim()
        val patterns = listOf(
            "dd.MM.yyyy", "d.M.yyyy", "dd/MM/yyyy", "d/M/yyyy",
            "yyyy-MM-dd", "dd-MM-yyyy", "d-M-yyyy", "dd.MM.yy", "d.M.yy"
        )
        for (pattern in patterns) {
            try {
                return LocalDate.parse(clean, DateTimeFormatter.ofPattern(pattern))
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    private fun Field.russianName(): String = when (this) {
        Field.APARTMENT -> "Квартира"
        Field.DEFECT -> "Замечание"
        Field.DUE -> "Срок"
        Field.STATUS -> "Статус"
        Field.RESPONSIBLE -> "Ответственный"
        Field.CATEGORY -> "Категория"
        Field.CREATED -> "Дата выявления"
    }
}
