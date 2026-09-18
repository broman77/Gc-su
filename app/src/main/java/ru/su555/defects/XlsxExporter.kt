package ru.su555.defects

import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxExporter {
    fun build(defects: List<Defect>): ByteArray {
        val dashboard = defects.dashboard()
        val apartments = defects.byApartment()
        val categories = defects.byCategory()
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
            add("xl/worksheets/sheet1.xml", summarySheet(dashboard))
            add("xl/worksheets/_rels/sheet1.xml.rels", sheetDrawingRel(1))
            add("xl/drawings/drawing1.xml", drawingXml("Статусы", 1))
            add("xl/drawings/_rels/drawing1.xml.rels", drawingRel(1))
            add("xl/charts/chart1.xml", statusChart())
            add("xl/worksheets/sheet2.xml", apartmentSheet(apartments))
            add("xl/worksheets/sheet3.xml", categorySheet(categories))
            add("xl/worksheets/_rels/sheet3.xml.rels", sheetDrawingRel(2))
            add("xl/drawings/drawing2.xml", drawingXml("Категории", 2))
            add("xl/drawings/_rels/drawing2.xml.rels", drawingRel(2))
            add("xl/charts/chart2.xml", categoryChart(categories.size.coerceAtLeast(1).coerceAtMost(10)))
            add("xl/worksheets/sheet4.xml", defectsSheet(defects))
        }
        return out.toByteArray()
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
<dc:title>Отчёт по устранению замечаний</dc:title>
<dc:creator>ООО ГК СУ-555</dc:creator>
<dcterms:created xsi:type="dcterms:W3CDTF">$now</dcterms:created>
</cp:coreProperties>"""
    }

    private fun workbook() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>
<sheet name="Сводка" sheetId="1" r:id="rId1"/>
<sheet name="По квартирам" sheetId="2" r:id="rId2"/>
<sheet name="Категории" sheetId="3" r:id="rId3"/>
<sheet name="Замечания" sheetId="4" r:id="rId4"/>
</sheets>
</workbook>"""

    private fun workbookRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
<Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet4.xml"/>
<Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="2">
<font><sz val="11"/><name val="Calibri"/></font>
<font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Calibri"/></font>
</fonts>
<fills count="3">
<fill><patternFill patternType="none"/></fill>
<fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF005B8D"/><bgColor indexed="64"/></patternFill></fill>
</fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="3">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment wrapText="1" vertical="top"/></xf>
</cellXfs>
</styleSheet>"""

    private fun summarySheet(d: Dashboard): String {
        val rows = mutableListOf<List<Any>>()
        rows += listOf("Показатель", "Значение")
        rows += listOf("Всего замечаний", d.total)
        rows += listOf("Открыто", d.open)
        rows += listOf("Просрочено", d.overdue)
        rows += listOf("Устранено", d.closed)
        rows += listOf("Квартир с замечаниями", d.apartments)
        rows += listOf("Квартир с просрочкой", d.apartmentsWithOverdue)
        rows += listOf("Выполнение, %", d.completionPercent)
        rows += listOf("Дата отчёта", LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
        return worksheet(rows, widths = listOf(34.0, 18.0), drawing = true)
    }

    private fun apartmentSheet(items: List<ApartmentSummary>): String {
        val rows = mutableListOf<List<Any>>()
        rows += listOf("Квартира", "Всего", "Открыто", "Просрочено", "Устранено", "Ближайший срок", "Макс. просрочка, дней")
        items.forEach {
            rows += listOf(
                it.apartment, it.total, it.open, it.overdue, it.closed,
                it.nearestDue?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: "",
                it.maxOverdueDays
            )
        }
        return worksheet(rows, widths = listOf(18.0, 12.0, 12.0, 14.0, 14.0, 18.0, 22.0))
    }

    private fun categorySheet(items: List<CategorySummary>): String {
        val rows = mutableListOf<List<Any>>()
        rows += listOf("Категория", "Всего", "Открыто", "Просрочено", "Устранено")
        items.forEach { rows += listOf(it.name, it.total, it.open, it.overdue, it.closed) }
        if (items.isEmpty()) rows += listOf("Нет данных", 0, 0, 0, 0)
        return worksheet(rows, widths = listOf(34.0, 12.0, 12.0, 14.0, 14.0), drawing = true)
    }

    private fun defectsSheet(items: List<Defect>): String {
        val rows = mutableListOf<List<Any>>()
        rows += listOf("Квартира", "Замечание", "Категория", "Ответственный", "Статус", "Срок", "Просрочка, дней", "Дата выявления", "Лист", "Строка")
        items.sortedWith(compareByDescending<Defect> { it.isOverdue() }.thenBy { it.apartment }).forEach {
            rows += listOf(
                it.apartment,
                it.description,
                it.category,
                it.responsible,
                if (it.isClosed) "Устранено" else it.statusText.ifBlank { "Открыто" },
                it.dueDate?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: "",
                it.overdueDays(),
                it.createdDate?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: "",
                it.sourceSheet,
                it.sourceRow
            )
        }
        return worksheet(rows, widths = listOf(14.0, 58.0, 24.0, 26.0, 18.0, 16.0, 18.0, 18.0, 18.0, 10.0))
    }

    private fun worksheet(rows: List<List<Any>>, widths: List<Double>, drawing: Boolean = false): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        if (widths.isNotEmpty()) {
            sb.append("<cols>")
            widths.forEachIndexed { index, width ->
                val col = index + 1
                sb.append("""<col min="$col" max="$col" width="$width" customWidth="1"/>""")
            }
            sb.append("</cols>")
        }
        sb.append("<sheetData>")
        rows.forEachIndexed { rowIndex, row ->
            val r = rowIndex + 1
            sb.append("""<row r="$r">""")
            row.forEachIndexed { colIndex, value ->
                val ref = columnName(colIndex + 1) + r
                val style = if (rowIndex == 0) 1 else if (value is String && value.length > 40) 2 else 0
                when (value) {
                    is Number -> sb.append("""<c r="$ref" s="$style"><v>$value</v></c>""")
                    else -> sb.append("""<c r="$ref" s="$style" t="inlineStr"><is><t>${escape(value.toString())}</t></is></c>""")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData>")
        sb.append("""<autoFilter ref="A1:${columnName(widths.size.coerceAtLeast(1))}${rows.size.coerceAtLeast(1)}"/>""")
        if (drawing) sb.append("""<drawing r:id="rId1"/>""")
        sb.append("</worksheet>")
        return sb.toString()
    }

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
        title = "Статус замечаний",
        categoryFormula = "'Сводка'!\$A\$3:\$A\$5",
        valueFormula = "'Сводка'!\$B\$3:\$B\$5"
    )

    private fun categoryChart(count: Int) = barChart(
        title = "Замечания по категориям",
        categoryFormula = "'Категории'!\$A\$2:\$A${count + 1}",
        valueFormula = "'Категории'!\$B\$2:\$B${count + 1}"
    )

    private fun barChart(title: String, categoryFormula: String, valueFormula: String): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
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
            val rem = (n - 1) % 26
            sb.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
