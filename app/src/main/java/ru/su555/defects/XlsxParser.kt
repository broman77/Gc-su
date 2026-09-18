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
        val detectedColumns: Map<String, String>,
        val colorStats: Map<DefectStatus, Int>
    )

    private data class Cell(
        val value: String,
        val styleId: Int
    )

    private data class Row(
        val number: Int,
        val cells: Map<Int, Cell>
    )

    private enum class Field {
        ADDRESS, ELEMENT, DEFECT, DUE, RESPONSIBLE
    }

    fun parse(input: InputStream): ParseResult {
        val bytes = input.readBytes()
        if (bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4B.toByte()) {
            throw IllegalArgumentException(
                "Поддерживается формат .xlsx. Откройте файл в Excel и сохраните как «Книга Excel (*.xlsx)»."
            )
        }

        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val shared = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings) ?: emptyList()
        val styleColors = parseStyleColors(entries["xl/styles.xml"])
        val workbookNames = parseSheetNames(entries["xl/workbook.xml"])

        val sheets = entries
            .filterKeys { it.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
            .toSortedMap(compareBy { name ->
                Regex("\\d+").find(name)?.value?.toIntOrNull() ?: Int.MAX_VALUE
            })

        if (sheets.isEmpty()) throw IllegalArgumentException("В файле не найдены листы Excel.")

        val warnings = mutableListOf<String>()
        val all = mutableListOf<Defect>()
        val detected = linkedMapOf<String, String>()
        var invalidAddresses = 0

        sheets.entries.forEachIndexed { index, entry ->
            val sheetName = workbookNames.getOrNull(index) ?: "Лист ${index + 1}"
            val rows = parseWorksheet(entry.value, shared)
            if (rows.isEmpty()) return@forEachIndexed

            val detection = detectHeader(rows)
            if (detection == null) {
                if (rows.any { row -> row.cells.values.any { it.value.isNotBlank() } }) {
                    warnings += "Лист «$sheetName» пропущен: таблица замечаний не распознана."
                }
                return@forEachIndexed
            }

            val (headerIndex, mapping) = detection
            val header = rows[headerIndex]

            mapping.forEach { (field, col) ->
                detected.putIfAbsent(field.russianName(), value(header, col))
            }

            val addressCol = mapping[Field.ADDRESS]
            val defectCol = mapping[Field.DEFECT]
            if (addressCol == null || defectCol == null) {
                warnings += "Лист «$sheetName»: не найдены колонки адреса и дефекта."
                return@forEachIndexed
            }

            for (i in (headerIndex + 1) until rows.size) {
                val row = rows[i]
                val address = value(row, addressCol).trim()
                val description = value(row, defectCol).trim()
                if (description.isBlank()) continue

                val parsedAddress = parseAddress(address)
                if (parsedAddress == null) {
                    invalidAddresses++
                    continue
                }

                val building = parsedAddress.first
                val apartment = parsedAddress.second
                val section = MironovskayaProject.sectionFor(building, apartment)

                if (section == null) {
                    invalidAddresses++
                    continue
                }

                val defectColor = color(row, defectCol, styleColors)
                val addressColor = color(row, addressCol, styleColors)
                val hasOrange = addressColor == ORANGE ||
                    row.cells.values.any { styleColors[it.styleId] == ORANGE }

                val status = when (defectColor) {
                    GREEN -> DefectStatus.DONE
                    BLUE -> DefectStatus.REPORTED_NOT_DONE
                    else -> if (hasOrange) DefectStatus.ATTENTION else DefectStatus.OPEN
                }

                all += Defect(
                    building = building,
                    section = section,
                    apartment = apartment,
                    address = address,
                    element = value(row, mapping[Field.ELEMENT]).trim(),
                    description = description,
                    responsible = value(row, mapping[Field.RESPONSIBLE]).trim(),
                    status = status,
                    dueDate = parseDate(value(row, mapping[Field.DUE])),
                    sourceSheet = sheetName,
                    sourceRow = row.number
                )
            }
        }

        if (all.isEmpty()) {
            throw IllegalArgumentException(
                "Замечания не найдены. Для реестра Мироновской нужны колонки адреса, дефекта и данные квартир."
            )
        }

        if (invalidAddresses > 0) {
            warnings += "Пропущено строк с адресом вне структуры 568 квартир: $invalidAddresses."
        }
        if (all.none { it.dueDate != null }) {
            warnings += "Не удалось распознать даты планового устранения."
        }

        val colorStats = DefectStatus.entries.associateWith { status ->
            all.count { it.status == status }
        }

        val coloredCount = colorStats.getValue(DefectStatus.DONE) +
            colorStats.getValue(DefectStatus.ATTENTION) +
            colorStats.getValue(DefectStatus.REPORTED_NOT_DONE)

        if (coloredCount == 0) {
            warnings += "Цветовые статусы не обнаружены. Проверьте, что исходный Excel сохраняет заливку ячеек."
        }

        return ParseResult(
            defects = all,
            warnings = warnings,
            detectedColumns = detected,
            colorStats = colorStats
        )
    }

    private fun value(row: Row, col: Int?): String =
        if (col == null) "" else row.cells[col]?.value.orEmpty()

    private fun color(row: Row, col: Int?, styleColors: Map<Int, String?>): String? =
        if (col == null) null else row.cells[col]?.styleId?.let(styleColors::get)

    private fun parseAddress(raw: String): Pair<Int, Int>? {
        if (raw.isBlank()) return null
        val normalized = raw
            .lowercase()
            .replace('ё', 'е')
            .replace(Regex("\\s+"), " ")

        val building = Regex("""(?:^|[ ,])к(?:орпус)?\s*\.?\s*(\d+)""")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

        val apartment = Regex("""(?:^|[ ,])кв(?:артира)?\s*\.?\s*(\d+)""")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

        return if (building != null && apartment != null) building to apartment else null
    }

    private fun parseStyleColors(data: ByteArray?): Map<Int, String?> {
        if (data == null) return emptyMap()

        val parser = newParser(data)
        val fills = mutableListOf<String?>()
        val styleFillIds = mutableListOf<Int>()

        var inFills = false
        var inCellXfs = false
        var currentFill: String? = null

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "fills" -> inFills = true
                    "cellXfs" -> inCellXfs = true
                    "fill" -> if (inFills) currentFill = null
                    "fgColor" -> if (inFills) {
                        currentFill = normalizeRgb(parser.getAttributeValue(null, "rgb"))
                    }
                    "xf" -> if (inCellXfs) {
                        styleFillIds += parser.getAttributeValue(null, "fillId")?.toIntOrNull() ?: 0
                    }
                }

                XmlPullParser.END_TAG -> when (parser.name) {
                    "fill" -> if (inFills) fills += currentFill
                    "fills" -> inFills = false
                    "cellXfs" -> inCellXfs = false
                }
            }
            parser.next()
        }

        return styleFillIds.mapIndexed { index, fillId ->
            index to fills.getOrNull(fillId)
        }.toMap()
    }

    private fun normalizeRgb(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val value = raw.uppercase()
        return when (value.length) {
            8 -> value.takeLast(6)
            6 -> value
            else -> null
        }
    }

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
        var currentCells = linkedMapOf<Int, Cell>()
        var currentColumn = -1
        var currentType = ""
        var currentValue = ""
        var currentStyleId = 0
        var inlineText = StringBuilder()
        var inCell = false

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        currentRow = parser.getAttributeValue(null, "r")?.toIntOrNull()
                            ?: (result.size + 1)
                        currentCells = linkedMapOf()
                    }

                    "c" -> {
                        inCell = true
                        val ref = parser.getAttributeValue(null, "r").orEmpty()
                        currentColumn = columnIndex(ref)
                        currentType = parser.getAttributeValue(null, "t").orEmpty()
                        currentStyleId = parser.getAttributeValue(null, "s")?.toIntOrNull() ?: 0
                        currentValue = ""
                        inlineText = StringBuilder()
                    }

                    "v" -> if (inCell) currentValue = parser.nextText()
                    "t" -> if (inCell && currentType == "inlineStr") inlineText.append(parser.nextText())
                }

                XmlPullParser.END_TAG -> when (parser.name) {
                    "c" -> {
                        if (currentColumn >= 0) {
                            val parsedValue = when (currentType) {
                                "s" -> currentValue.toIntOrNull()
                                    ?.let { shared.getOrNull(it) }
                                    .orEmpty()

                                "inlineStr" -> inlineText.toString()
                                "b" -> if (currentValue == "1") "Да" else "Нет"
                                else -> currentValue
                            }

                            currentCells[currentColumn] = Cell(
                                value = parsedValue,
                                styleId = currentStyleId
                            )
                        }
                        inCell = false
                    }

                    "row" -> if (currentRow >= 0) {
                        result += Row(currentRow, currentCells.toMap())
                    }
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

            row.cells.forEach { (col, cell) ->
                detectField(normalize(cell.value))?.let { field ->
                    map.putIfAbsent(field, col)
                }
            }

            var score = map.size
            if (map.containsKey(Field.ADDRESS)) score += 3
            if (map.containsKey(Field.DEFECT)) score += 4
            if (map.containsKey(Field.DUE)) score += 1
            if (map.containsKey(Field.RESPONSIBLE)) score += 1

            if (score > bestScore) {
                bestScore = score
                bestIndex = index
                bestMap = map
            }
        }

        return if (
            bestIndex >= 0 &&
            bestMap.containsKey(Field.ADDRESS) &&
            bestMap.containsKey(Field.DEFECT)
        ) {
            bestIndex to bestMap
        } else {
            null
        }
    }

    private fun detectField(value: String): Field? {
        if (value.isBlank()) return null

        return when {
            matches(
                value,
                "новый адрес",
                "адрес",
                "квартира",
                "номер квартиры",
                "№ квартиры"
            ) -> Field.ADDRESS

            matches(
                value,
                "эллементы квартиры",
                "элементы квартиры",
                "элемент квартиры",
                "помещение",
                "зона"
            ) -> Field.ELEMENT

            matches(
                value,
                "дефекты",
                "дефект",
                "замечание",
                "описание замечания",
                "перечень замечаний"
            ) -> Field.DEFECT

            matches(
                value,
                "дата планового устранения",
                "плановый срок",
                "срок устранения",
                "плановая дата",
                "дата устранения"
            ) -> Field.DUE

            matches(
                value,
                "подрядчик",
                "ответственный",
                "исполнитель",
                "организация",
                "субподрядчик"
            ) -> Field.RESPONSIBLE

            else -> null
        }
    }

    private fun matches(value: String, vararg variants: String): Boolean =
        variants.any { variant ->
            val normalized = normalize(variant)
            value == normalized || (normalized.length >= 5 && value.contains(normalized))
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
        val value = raw.trim()
        if (value.isBlank()) return null

        value.replace(',', '.').toDoubleOrNull()?.let { serial ->
            if (serial in 20_000.0..80_000.0) {
                return LocalDate.of(1899, 12, 30).plusDays(serial.toLong())
            }
        }

        val clean = value.substringBefore(" ").trim()
        val patterns = listOf(
            "dd.MM.yyyy",
            "d.M.yyyy",
            "dd/MM/yyyy",
            "d/M/yyyy",
            "yyyy-MM-dd",
            "dd-MM-yyyy",
            "d-M-yyyy",
            "dd.MM.yy",
            "d.M.yy"
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
        Field.ADDRESS -> "Адрес"
        Field.ELEMENT -> "Элемент квартиры"
        Field.DEFECT -> "Дефект"
        Field.DUE -> "Плановый срок"
        Field.RESPONSIBLE -> "Подрядчик"
    }

    private const val GREEN = "92D050"
    private const val ORANGE = "FFC000"
    private const val BLUE = "00B0F0"
}
