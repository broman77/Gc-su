package ru.su555.defects

import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxExporter {
    private data class XCell(val value: Any, val style: Int = 0)

    fun build(defects: List<Defect>, norms: LaborNorms = LaborNorms()): ByteArray {
        val dashboard = defects.dashboard()
        val sections = defects.bySection()
        val apartments = defects.byApartment()
        val contractors = defects.byResponsible()
        val contractorResources = defects.contractorResourceEstimates(norms)
        val out = ByteArrayOutputStream()

        ZipOutputStream(out).use { zip ->
            fun add(path: String, xml: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            add("[Content_Types].xml", contentTypes())
            add("_rels/.rels", rootRels())
            add("docProps/app.xml", appProps())
            add("docProps/core.xml", coreProps())
            add("xl/workbook.xml", workbook())
            add("xl/_rels/workbook.xml.rels", workbookRels())
            add("xl/styles.xml", styles())

            add("xl/worksheets/sheet1.xml", summarySheet(dashboard, defects))
            add("xl/worksheets/_rels/sheet1.xml.rels", sheetDrawingRel(1))
            add("xl/drawings/drawing1.xml", drawingXml("Статусы замечаний", 1))
            add("xl/drawings/_rels/drawing1.xml.rels", drawingRel(1))
            add("xl/charts/chart1.xml", statusChart())

            add("xl/worksheets/sheet2.xml", sectionSheet(sections))
            add("xl/worksheets/_rels/sheet2.xml.rels", sheetDrawingRel(2))
            add("xl/drawings/drawing2.xml", drawingXml("Открытые замечания по секциям", 2))
            add("xl/drawings/_rels/drawing2.xml.rels", drawingRel(2))
            add("xl/charts/chart2.xml", sectionChart())

            add("xl/worksheets/sheet3.xml", apartmentSheet(apartments))
            add("xl/worksheets/sheet4.xml", contractorSheet(contractors, contractorResources))
            add("xl/worksheets/sheet5.xml", defectsSheet(defects))
        }

        return out.toByteArray()
    }

    private fun summarySheet(d: Dashboard, currentDefects: List<Defect>): String {
        val rows = listOf(
            row("Показатель", "Значение", header = true),
            row("Квартир всего", d.totalApartments),
            row("Квартир в реестре", d.apartmentsInRegister),
            row("Квартир в работе", d.apartmentsWithOpen),
            row("Квартир с просрочкой", d.apartmentsWithOverdue),
            row("Критичных открытых замечаний", currentDefects.count { !it.isClosed && it.priority == DefectPriority.CRITICAL }),
            row("Полностью закрытых квартир", d.apartmentsFullyDone),
            row("Всего замечаний", d.totalDefects),
            listOf(XCell("Белые · не выполнено"), XCell(d.plainOpen, 6)),
            listOf(XCell("Оранжевые · обратить внимание"), XCell(d.attention, 4)),
            listOf(XCell("Синие · отчитано, но не выполнено"), XCell(d.reportedNotDone, 5)),
            listOf(XCell("Зелёные · выполнено"), XCell(d.closed, 3)),
            row("Просроченных замечаний", d.overdueDefects),
            row("Выполнение замечаний, %", d.completionPercent),
            row("Дата отчёта", LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
        )
        return worksheet(rows, listOf(38.0, 18.0), drawing = true)
    }

    private fun sectionSheet(items: List<SectionSummary>): String {
        val rows = mutableListOf<List<XCell>>()
        rows += headers(
            "Корпус / секция", "Квартир всего", "В реестре", "Квартир в работе",
            "Квартир с просрочкой", "Замечаний", "Открытых замечаний",
            "Оранжевые", "Синие", "Зелёные", "Готовность, %"
        )
        items.forEach {
            rows += listOf(
                XCell(it.title),
                XCell(it.apartmentCapacity),
                XCell(it.apartmentsInRegister),
                XCell(it.apartmentsWithOpen),
                XCell(it.apartmentsWithOverdue),
                XCell(it.totalDefects),
                XCell(it.openDefects),
                XCell(it.attention, if (it.attention > 0) 4 else 0),
                XCell(it.reportedNotDone, if (it.reportedNotDone > 0) 5 else 0),
                XCell(it.closedDefects, 3),
                XCell(it.completionPercent)
            )
        }
        return worksheet(
            rows,
            listOf(24.0, 14.0, 12.0, 18.0, 21.0, 14.0, 19.0, 13.0, 11.0, 11.0, 16.0),
            drawing = true
        )
    }

    private fun apartmentSheet(items: List<ApartmentSummary>): String {
        val rows = mutableListOf<List<XCell>>()
        rows += headers(
            "Корпус", "Секция", "Квартира", "Всего", "Белые", "Оранжевые",
            "Синие", "Зелёные", "Просрочено", "Ближайший срок", "Макс. просрочка, дней"
        )
        items.forEach {
            rows += listOf(
                XCell(it.building),
                XCell(it.section),
                XCell(it.apartment),
                XCell(it.total),
                XCell(it.plainOpen, 6),
                XCell(it.attention, if (it.attention > 0) 4 else 0),
                XCell(it.reportedNotDone, if (it.reportedNotDone > 0) 5 else 0),
                XCell(it.closed, 3),
                XCell(it.overdueDefects),
                XCell(it.nearestDue?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: ""),
                XCell(it.maxOverdueDays)
            )
        }
        return worksheet(
            rows,
            listOf(11.0, 11.0, 12.0, 10.0, 10.0, 13.0, 10.0, 10.0, 13.0, 18.0, 22.0)
        )
    }

    private fun contractorSheet(
        items: List<CategorySummary>,
        resources: List<ContractorResourceEstimate>
    ): String {
        val rows = mutableListOf<List<XCell>>()
        rows += headers(
            "Подрядчик", "Всего", "Открыто", "Просрочено", "Выполнено",
            "Квартир в работе", "Оценка, чел.-ч", "Рекомендуемо людей", "Оценка, раб. дней"
        )
        items.forEach { item ->
            val estimate = resources.firstOrNull {
                it.name.equals(item.name, ignoreCase = true)
            }
            rows += listOf(
                XCell(item.name),
                XCell(item.total),
                XCell(item.open),
                XCell(item.overdue),
                XCell(item.closed),
                XCell(estimate?.apartments ?: 0),
                XCell(estimate?.laborHours ?: 0.0),
                XCell(estimate?.recommendedPeople ?: 0),
                XCell(estimate?.estimatedWorkingDays ?: 0.0)
            )
        }
        return worksheet(
            rows,
            listOf(34.0, 10.0, 10.0, 12.0, 12.0, 18.0, 17.0, 20.0, 19.0)
        )
    }

    private fun defectsSheet(items: List<Defect>): String {
        val rows = mutableListOf<List<XCell>>()
        rows += headers(
            "Корпус", "Секция", "Квартира", "Адрес", "Элемент квартиры", "Дефект",
            "Подрядчик", "Ответственный сотрудник", "Приоритет", "Статус", "Плановый срок",
            "Дата отчёта", "Дата проверки", "Дата закрытия", "Просрочка, дней", "Лист", "Строка"
        )

        items.sortedWith(
            compareByDescending<Defect> { it.isOverdue() }
                .thenByDescending { it.status == DefectStatus.REPORTED_NOT_DONE }
                .thenByDescending { it.status == DefectStatus.ATTENTION }
                .thenBy { it.building }
                .thenBy { it.section }
                .thenBy { it.apartment }
        ).forEach { defect ->
            val statusStyle = when (defect.status) {
                DefectStatus.DONE -> 3
                DefectStatus.ATTENTION -> 4
                DefectStatus.REPORTED_NOT_DONE -> 5
                DefectStatus.OPEN -> 6
            }
            rows += listOf(
                XCell(defect.building),
                XCell(defect.section),
                XCell(defect.apartment),
                XCell(defect.address),
                XCell(defect.element),
                XCell(defect.description, 2),
                XCell(displayResponsible(defect)),
                XCell(defect.assignedEmployee),
                XCell(defect.priority.title),
                XCell(defect.status.title, statusStyle),
                XCell(defect.dueDate?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: ""),
                XCell(formatTimestamp(defect.reportedAt)),
                XCell(formatTimestamp(defect.verifiedAt)),
                XCell(formatTimestamp(defect.completedAt)),
                XCell(defect.overdueDays()),
                XCell(defect.sourceSheet),
                XCell(defect.sourceRow)
            )
        }

        return worksheet(
            rows,
            listOf(10.0, 10.0, 11.0, 28.0, 28.0, 55.0, 25.0, 24.0, 14.0, 25.0, 18.0, 16.0, 16.0, 16.0, 19.0, 16.0, 10.0)
        )
    }

    private fun formatTimestamp(value: Long?): String =
        value?.let {
            java.time.Instant.ofEpochMilli(it)
                .atZone(java.time.ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        } ?: ""

    private fun row(label: String, value: Any, header: Boolean = false): List<XCell> =
        if (header) listOf(XCell(label, 1), XCell(value, 1))
        else listOf(XCell(label), XCell(value))

    private fun headers(vararg values: String): List<XCell> =
        values.map { XCell(it, 1) }

    private fun worksheet(
        rows: List<List<XCell>>,
        widths: List<Double>,
        drawing: Boolean = false
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")

        sb.append("<cols>")
        widths.forEachIndexed { index, width ->
            val column = index + 1
            sb.append("""<col min="$column" max="$column" width="$width" customWidth="1"/>""")
        }
        sb.append("</cols>")

        sb.append("<sheetData>")
        rows.forEachIndexed { rowIndex, row ->
            val number = rowIndex + 1
            sb.append("""<row r="$number">""")
            row.forEachIndexed { columnIndex, cell ->
                val reference = columnName(columnIndex + 1) + number
                when (val value = cell.value) {
                    is Number -> sb.append(
                        """<c r="$reference" s="${cell.style}"><v>$value</v></c>"""
                    )
                    else -> sb.append(
                        """<c r="$reference" s="${cell.style}" t="inlineStr"><is><t>${escape(value.toString())}</t></is></c>"""
                    )
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData>")

        if (rows.isNotEmpty() && widths.isNotEmpty()) {
            sb.append(
                """<autoFilter ref="A1:${columnName(widths.size)}${rows.size}"/>"""
            )
        }
        if (drawing) sb.append("""<drawing r:id="rId1"/>""")
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet4.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet5.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/drawings/drawing1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>
<Override PartName="/xl/drawings/drawing2.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>
<Override PartName="/xl/charts/chart1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.chart+xml"/>
<Override PartName="/xl/charts/chart2.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.chart+xml"/>
<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
</Types>"""

    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>"""

    private fun appProps() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties">
<Application>Устранение замечаний</Application>
</Properties>"""

    private fun coreProps(): String {
        val now = java.time.Instant.now().toString()
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
 xmlns:dc="http://purl.org/dc/elements/1.1/"
 xmlns:dcterms="http://purl.org/dc/terms/"
 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
<dc:title>Мироновская 30 — отчёт по устранению замечаний</dc:title>
<dc:creator>ООО ГК СУ-555</dc:creator>
<dcterms:created xsi:type="dcterms:W3CDTF">$now</dcterms:created>
</cp:coreProperties>"""
    }

    private fun workbook() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>
<sheet name="Руководителю" sheetId="1" r:id="rId1"/>
<sheet name="Секции" sheetId="2" r:id="rId2"/>
<sheet name="Квартиры" sheetId="3" r:id="rId3"/>
<sheet name="Подрядчики" sheetId="4" r:id="rId4"/>
<sheet name="Детали" sheetId="5" r:id="rId5"/>
</sheets>
</workbook>"""

    private fun workbookRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
<Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet4.xml"/>
<Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet5.xml"/>
<Relationship Id="rId6" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="2">
<font><sz val="11"/><name val="Calibri"/></font>
<font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Calibri"/></font>
</fonts>
<fills count="7">
<fill><patternFill patternType="none"/></fill>
<fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF0B4F86"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF92D050"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFFFC000"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF00B0F0"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFF1F3F5"/><bgColor indexed="64"/></patternFill></fill>
</fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="7">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment wrapText="1" vertical="top"/></xf>
<xf numFmtId="0" fontId="0" fillId="3" borderId="0" xfId="0" applyFill="1"/>
<xf numFmtId="0" fontId="0" fillId="4" borderId="0" xfId="0" applyFill="1"/>
<xf numFmtId="0" fontId="0" fillId="5" borderId="0" xfId="0" applyFill="1"/>
<xf numFmtId="0" fontId="0" fillId="6" borderId="0" xfId="0" applyFill="1"/>
</cellXfs>
</styleSheet>"""

    private fun sheetDrawingRel(number: Int) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing$number.xml"/>
</Relationships>"""

    private fun drawingRel(number: Int) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart" Target="../charts/chart$number.xml"/>
</Relationships>"""

    private fun drawingXml(name: String, number: Int) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing"
 xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<xdr:twoCellAnchor>
<xdr:from><xdr:col>3</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>1</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>
<xdr:to><xdr:col>11</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>18</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to>
<xdr:graphicFrame macro="">
<xdr:nvGraphicFramePr><xdr:cNvPr id="$number" name="${escape(name)}"/><xdr:cNvGraphicFramePr/></xdr:nvGraphicFramePr>
<xdr:xfrm/>
<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/chart">
<c:chart xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" r:id="rId1"/>
</a:graphicData></a:graphic>
</xdr:graphicFrame>
<xdr:clientData/>
</xdr:twoCellAnchor>
</xdr:wsDr>"""

    private fun statusChart() = barChart(
        title = "Статусы замечаний",
        categoryFormula = "'Руководителю'!\$A\$5:\$A\$8",
        valueFormula = "'Руководителю'!\$B\$5:\$B\$8"
    )

    private fun sectionChart() = barChart(
        title = "Открытые замечания по секциям",
        categoryFormula = "'Секции'!\$A\$2:\$A\$5",
        valueFormula = "'Секции'!\$E\$2:\$E\$5"
    )

    private fun barChart(
        title: String,
        categoryFormula: String,
        valueFormula: String
    ): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<c:chartSpace xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
<c:chart>
<c:title><c:tx><c:rich><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>${escape(title)}</a:t></a:r></a:p></c:rich></c:tx><c:layout/></c:title>
<c:plotArea>
<c:layout/>
<c:barChart>
<c:barDir val="col"/><c:grouping val="clustered"/><c:varyColors val="1"/>
<c:ser><c:idx val="0"/><c:order val="0"/>
<c:tx><c:v>Количество</c:v></c:tx>
<c:cat><c:strRef><c:f>$categoryFormula</c:f></c:strRef></c:cat>
<c:val><c:numRef><c:f>$valueFormula</c:f></c:numRef></c:val>
</c:ser>
<c:axId val="48650112"/><c:axId val="48672768"/>
</c:barChart>
<c:catAx><c:axId val="48650112"/><c:scaling><c:orientation val="minMax"/></c:scaling><c:axPos val="b"/><c:tickLblPos val="nextTo"/><c:crossAx val="48672768"/><c:crosses val="autoZero"/></c:catAx>
<c:valAx><c:axId val="48672768"/><c:scaling><c:orientation val="minMax"/></c:scaling><c:axPos val="l"/><c:majorGridlines/><c:numFmt formatCode="0" sourceLinked="0"/><c:tickLblPos val="nextTo"/><c:crossAx val="48650112"/><c:crosses val="autoZero"/></c:valAx>
</c:plotArea>
<c:plotVisOnly val="1"/><c:dispBlanksAs val="gap"/>
</c:chart>
</c:chartSpace>"""

    private fun columnName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n > 0) {
            val remainder = (n - 1) % 26
            sb.append(('A'.code + remainder).toChar())
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    private fun escape(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
