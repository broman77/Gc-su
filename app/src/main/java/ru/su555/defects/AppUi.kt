package ru.su555.defects

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val BrandBlue = Color(0xFF0B4F86)
private val BrandLightBlue = Color(0xFF62B5E5)
private val BrandDark = Color(0xFF153347)
private val AppBackground = Color(0xFFF4F7FA)
private val Muted = Color(0xFF6A7882)
private val Danger = Color(0xFFC62828)

private val ExcelGreen = Color(0xFF92D050)
private val ExcelOrange = Color(0xFFFFC000)
private val ExcelBlue = Color(0xFF00B0F0)
private val ExcelWhite = Color(0xFFE3E8EC)

@Composable
fun Su555Theme(content: @Composable () -> Unit) {
    val scheme = androidx.compose.material3.lightColorScheme(
        primary = BrandBlue,
        secondary = BrandLightBlue,
        background = AppBackground,
        surface = Color.White,
        surfaceVariant = Color(0xFFEAF2F7),
        onPrimary = Color.White,
        onBackground = BrandDark,
        onSurface = BrandDark,
        error = Danger
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

private enum class AppTab(val label: String, val icon: ImageVector) {
    OVERVIEW("Сводка", Icons.Outlined.Home),
    APARTMENTS("Квартиры", Icons.Outlined.Apartment),
    REPORTS("Отчёты", Icons.Outlined.BarChart)
}

private enum class DefectFilter(val label: String) {
    ALL("Все"),
    OVERDUE("Просрочено"),
    OPEN("Белые"),
    ATTENTION("Оранжевые"),
    REPORTED("Синие"),
    DONE("Зелёные")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefectsApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var parsed by remember { mutableStateOf<XlsxParser.ParseResult?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var tabIndex by remember { mutableIntStateOf(0) }
    var selectedApartment by remember { mutableStateOf<ApartmentSummary?>(null) }
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        val bytes = pendingExport
        if (uri != null && bytes != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                            ?: error("Не удалось открыть файл для записи")
                    }
                }.onSuccess {
                    snackbar.showSnackbar("Excel-отчёт сохранён")
                }.onFailure {
                    snackbar.showSnackbar("Ошибка сохранения: " + (it.message ?: "неизвестная ошибка"))
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                loading = true
                runCatching {
                    val name = withContext(Dispatchers.IO) {
                        context.contentResolver.query(
                            uri,
                            arrayOf(OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
                        } ?: "Реестр.xlsx"
                    }

                    val result = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { XlsxParser.parse(it) }
                            ?: error("Не удалось открыть выбранный файл")
                    }

                    sourceName = name
                    parsed = result
                    selectedApartment = null
                    tabIndex = 0
                    snackbar.showSnackbar(
                        "Загружено " + result.defects.size +
                            " замечаний, квартир в реестре: " + result.defects.dashboard().apartmentsInRegister
                    )
                }.onFailure {
                    snackbar.showSnackbar(it.message ?: "Не удалось прочитать Excel")
                }
                loading = false
            }
        }
    }

    fun chooseExcel() {
        importLauncher.launch(
            arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "application/octet-stream"
            )
        )
    }

    fun startExport() {
        val defects = parsed?.defects ?: return
        scope.launch {
            loading = true
            runCatching {
                withContext(Dispatchers.Default) { XlsxExporter.build(defects) }
            }.onSuccess { bytes ->
                pendingExport = bytes
                val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                exportLauncher.launch("Мироновская_отчёт_" + date + ".xlsx")
            }.onFailure {
                snackbar.showSnackbar("Не удалось сформировать Excel: " + (it.message ?: "ошибка"))
            }
            loading = false
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (selectedApartment != null) {
                        IconButton(onClick = { selectedApartment = null }) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
                title = {
                    val apartment = selectedApartment
                    if (apartment != null) {
                        Column {
                            Text(
                                "Квартира " + apartment.apartment,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Корпус " + apartment.building + " · секция " + apartment.section,
                                style = MaterialTheme.typography.labelSmall,
                                color = Muted
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OfficialLogo(compact = true)
                            Spacer(Modifier.width(9.dp))
                            Column {
                                Text(
                                    "Устранение замечаний",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    if (sourceName.isBlank()) "Мироновская, д. 30" else sourceName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (selectedApartment == null) {
                        IconButton(onClick = ::chooseExcel) {
                            Icon(Icons.Outlined.UploadFile, contentDescription = "Загрузить Excel")
                        }
                        if (parsed != null) {
                            IconButton(onClick = ::startExport) {
                                Icon(Icons.Outlined.Download, contentDescription = "Экспорт")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            if (parsed != null && selectedApartment == null) {
                NavigationBar(
                    containerColor = Color.White,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    AppTab.entries.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = tabIndex == index,
                            onClick = { tabIndex = index },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .padding(innerPadding)
        ) {
            val current = parsed
            when {
                current == null -> EmptyImportScreen(loading = loading, onImport = ::chooseExcel)
                selectedApartment != null -> {
                    val apartment = selectedApartment!!
                    ApartmentDetailScreen(
                        apartment = apartment,
                        defects = current.defects.defectsForApartment(
                            apartment.building,
                            apartment.section,
                            apartment.apartment
                        )
                    )
                }
                else -> when (AppTab.entries[tabIndex]) {
                    AppTab.OVERVIEW -> OverviewScreen(
                        defects = current.defects,
                        warnings = current.warnings,
                        onReload = ::chooseExcel
                    )
                    AppTab.APARTMENTS -> ApartmentsScreen(
                        defects = current.defects,
                        onApartmentClick = { selectedApartment = it }
                    )
                    AppTab.REPORTS -> ReportsScreen(current.defects, ::startExport)
                }
            }

            if (loading && current != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(14.dp))
                        Text("Обработка…", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
@Composable
private fun EmptyImportScreen(loading: Boolean, onImport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OfficialLogo(compact = false)
        Spacer(Modifier.height(28.dp))
        Text(
            "Устранение замечаний",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = BrandDark
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Мироновская, д. 30 · 2 корпуса · 568 квартир",
            style = MaterialTheme.typography.titleSmall,
            color = BrandBlue,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "Приложение читает ваш реестр Excel вместе с цветами: зелёный — выполнено, белый — не выполнено, оранжевый — обратить внимание, синий — отчитано, но по факту не выполнено.",
            style = MaterialTheme.typography.bodyLarge,
            color = Muted
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onImport, enabled = !loading) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(Modifier.width(10.dp))
                Text("Чтение файла…")
            } else {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Выбрать реестр .xlsx")
            }
        }
    }
}

@Composable
private fun OverviewScreen(
    defects: List<Defect>,
    warnings: List<String>,
    onReload: () -> Unit
) {
    val dashboard = remember(defects) { defects.dashboard() }
    val sections = remember(defects) { defects.bySection() }
    val urgent = remember(defects) {
        defects
            .filter { it.isOverdue() }
            .sortedWith(
                compareByDescending<Defect> { it.status == DefectStatus.REPORTED_NOT_DONE }
                    .thenByDescending { it.status == DefectStatus.ATTENTION }
                    .thenByDescending { it.overdueDays() }
            )
            .take(8)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Сводка", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Мироновская, д. 30", color = Muted)
                }
                OutlinedButton(onClick = onReload) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Обновить")
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { MetricCard("Квартир всего", dashboard.totalApartments.toString(), Icons.Outlined.Apartment, BrandBlue) }
                item { MetricCard("В работе", dashboard.apartmentsWithOpen.toString(), Icons.Outlined.ErrorOutline, BrandDark) }
                item { MetricCard("Квартир с просрочкой", dashboard.apartmentsWithOverdue.toString(), Icons.Outlined.WarningAmber, Danger) }
                item { MetricCard("Закрыты", dashboard.apartmentsFullyDone.toString(), Icons.Outlined.CheckCircle, ExcelGreen) }
            }
        }

        item {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onApartmentClick(item) },
                colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text("Выполнение замечаний", fontWeight = FontWeight.Bold)
                            Text(
                                dashboard.closed.toString() + " из " + dashboard.totalDefects + " выполнено",
                                color = Muted
                            )
                        }
                        Text(
                            dashboard.completionPercent.toString() + "%",
                            style = MaterialTheme.typography.headlineMedium,
                            color = BrandBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = dashboard.completionPercent / 100f,
                        modifier = Modifier.fillMaxWidth().height(10.dp),
                        color = ExcelGreen
                    )
                    Spacer(Modifier.height(16.dp))
                    StatusLegend(dashboard)
                }
            }
        }

        if (warnings.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF6E5))) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = ExcelOrange)
                            Spacer(Modifier.width(8.dp))
                            Text("Проверка файла", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        warnings.forEach { warning ->
                            Text("• " + warning, color = BrandDark)
                        }
                    }
                }
            }
        }

        item {
            Text("Корпуса и секции", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        items(sections) { section ->
            SectionCard(section)
        }

        if (urgent.isNotEmpty()) {
            item {
                Text("Что горит сейчас", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            items(urgent) { defect ->
                DefectCard(defect)
            }
        }
    }
}

@Composable
private fun StatusLegend(dashboard: Dashboard) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LegendRow("Белые · не выполнено", dashboard.plainOpen, ExcelWhite)
        LegendRow("Оранжевые · внимание", dashboard.attention, ExcelOrange)
        LegendRow("Синие · отчёт не принят", dashboard.reportedNotDone, ExcelBlue)
        LegendRow("Зелёные · выполнено", dashboard.closed, ExcelGreen)
    }
}

@Composable
private fun SectionCard(section: SectionSummary) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(section.title, fontWeight = FontWeight.Bold, color = BrandBlue)
                    Text(
                        section.apartmentCapacity.toString() + " квартир · в реестре " + section.apartmentsInRegister,
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (section.apartmentsWithOverdue > 0) {
                    StatusPill(section.apartmentsWithOverdue.toString() + " просроч.", Danger)
                } else {
                    StatusPill("Без просрочек", ExcelGreen)
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SmallMetric("Замечаний", section.totalDefects)
                SmallMetric("Открыто", section.openDefects)
                SmallMetric("Выполнено", section.closedDefects)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = section.completionPercent / 100f,
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = ExcelGreen
            )
            if (section.attention > 0 || section.reportedNotDone > 0) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (section.attention > 0) {
                        StatusPill(section.attention.toString() + " внимание", ExcelOrange)
                    }
                    if (section.reportedNotDone > 0) {
                        StatusPill(section.reportedNotDone.toString() + " синих", ExcelBlue)
                    }
                }
            }
        }
    }
}

@Composable
private fun ApartmentsScreen(defects: List<Defect>, onApartmentClick: (ApartmentSummary) -> Unit) {
    val summaries = remember(defects) { defects.byApartment() }
    var search by remember { mutableStateOf("") }
    var building by remember { mutableIntStateOf(0) }
    var section by remember { mutableIntStateOf(0) }

    val filtered = remember(summaries, search, building, section) {
        summaries.filter {
            (building == 0 || it.building == building) &&
                (section == 0 || it.section == section) &&
                (search.isBlank() || it.apartment.toString().contains(search.trim()))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Квартиры", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Нажмите на квартиру — внутри будут все её замечания", color = Muted)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = search,
                onValueChange = { search = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                label = { Text("Номер квартиры") }
            )

            Spacer(Modifier.height(10.dp))
            FilterRow(
                labels = listOf("Все корпуса", "Корпус 1", "Корпус 2"),
                selected = building,
                onSelected = {
                    building = it
                    if (building == 0) section = 0
                }
            )
            Spacer(Modifier.height(8.dp))
            FilterRow(
                labels = listOf("Все секции", "Секция 1", "Секция 2"),
                selected = section,
                onSelected = { section = it }
            )
            Spacer(Modifier.height(6.dp))
            Text("Найдено квартир: " + filtered.size, style = MaterialTheme.typography.labelMedium, color = Muted)
        }

        items(filtered) { item ->
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Квартира " + item.apartment,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Корпус " + item.building + " · секция " + item.section,
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        when {
                            item.hasOverdue -> StatusPill("Просрочка", Danger)
                            item.reportedNotDone > 0 -> StatusPill(item.reportedNotDone.toString() + " синих", ExcelBlue)
                            item.attention > 0 -> StatusPill(item.attention.toString() + " внимание", ExcelOrange)
                            item.open > 0 -> StatusPill(item.open.toString() + " открыто", BrandDark)
                            else -> StatusPill("Всё выполнено", ExcelGreen)
                        }
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "Открыть", tint = Muted)
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        SmallMetric("Всего", item.total)
                        SmallMetric("Открыто", item.open)
                        SmallMetric("Зелёных", item.closed)
                    }

                    if (item.attention > 0 || item.reportedNotDone > 0) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (item.attention > 0) {
                                StatusPill(item.attention.toString() + " оранж.", ExcelOrange)
                            }
                            if (item.reportedNotDone > 0) {
                                StatusPill(item.reportedNotDone.toString() + " синих", ExcelBlue)
                            }
                        }
                    }

                    if (item.nearestDue != null || item.maxOverdueDays > 0) {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(10.dp))
                        if (item.maxOverdueDays > 0) {
                            Text(
                                "Максимальная просрочка: " + item.maxOverdueDays + " дн.",
                                color = Danger,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                "Ближайший срок: " +
                                    item.nearestDue?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                                color = Muted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    labels: List<String>,
    selected: Int,
    onSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { index, label ->
            FilterChip(
                selected = selected == index,
                onClick = { onSelected(index) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun ApartmentDetailScreen(
    apartment: ApartmentSummary,
    defects: List<Defect>
) {
    var filter by remember { mutableStateOf<DefectStatus?>(null) }

    val filtered = remember(defects, filter) {
        if (filter == null) defects else defects.filter { it.status == filter }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Состояние квартиры", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        SmallMetric("Всего", apartment.total)
                        SmallMetric("Открыто", apartment.open)
                        SmallMetric("Выполнено", apartment.closed)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (apartment.plainOpen > 0) StatusPill(apartment.plainOpen.toString() + " бел.", ExcelWhite)
                        if (apartment.attention > 0) StatusPill(apartment.attention.toString() + " оранж.", ExcelOrange)
                        if (apartment.reportedNotDone > 0) StatusPill(apartment.reportedNotDone.toString() + " син.", ExcelBlue)
                        if (apartment.hasOverdue) StatusPill("Просрочка", Danger)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == null,
                    onClick = { filter = null },
                    label = { Text("Все (" + defects.size + ")") }
                )
                DefectStatus.entries.forEach { status ->
                    val count = defects.count { it.status == status }
                    if (count > 0) {
                        FilterChip(
                            selected = filter == status,
                            onClick = { filter = status },
                            label = { Text(shortStatus(status) + " (" + count + ")") }
                        )
                    }
                }
            }
        }

        item {
            Text("Замечания", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        items(filtered) { defect ->
            DefectCard(defect)
        }
    }
}

@Composable
private fun ReportsScreen(defects: List<Defect>, onExport: () -> Unit) {
    val dashboard = remember(defects) { defects.dashboard() }
    val sections = remember(defects) { defects.bySection() }
    val responsible = remember(defects) { defects.byResponsible().take(10) }
    val elements = remember(defects) { defects.byCategory().take(10) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Отчёт руководителю", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Коротко: квартиры, риски и ответственные", color = Muted)
        }

        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                Column(Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Статусы из Excel", fontWeight = FontWeight.Bold)
                        Text("Готовность " + dashboard.completionPercent + "%", color = BrandBlue, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(14.dp))
                    StatusBar("Белые · не выполнено", dashboard.plainOpen, dashboard.totalDefects, ExcelWhite)
                    StatusBar("Оранжевые · внимание", dashboard.attention, dashboard.totalDefects, ExcelOrange)
                    StatusBar("Синие · отчёт не принят", dashboard.reportedNotDone, dashboard.totalDefects, ExcelBlue)
                    StatusBar("Зелёные · выполнено", dashboard.closed, dashboard.totalDefects, ExcelGreen)
                }
            }
        }

        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                Column(Modifier.padding(18.dp)) {
                    Text("По секциям", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    sections.forEach { section ->
                        Text(section.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            section.apartmentsWithOpen.toString() + " кв. в работе · " +
                                section.apartmentsWithOverdue + " кв. с просрочкой",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(5.dp))
                        LinearProgressIndicator(
                            progress = section.completionPercent / 100f,
                            modifier = Modifier.fillMaxWidth().height(7.dp),
                            color = ExcelGreen
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }

        if (responsible.isNotEmpty()) {
            item {
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Подрядчики — открытые замечания", fontWeight = FontWeight.Bold)
                        Text("Комбинированные записи разделяются между подрядчиками", color = Muted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(14.dp))
                        BarList(responsible.map { it.name to it.open }, BrandBlue)
                    }
                }
            }
        }

        if (elements.isNotEmpty()) {
            item {
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("По элементам квартиры", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(14.dp))
                        BarList(elements.map { it.name to it.total }, BrandLightBlue)
                    }
                }
            }
        }

        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Download, contentDescription = null, tint = BrandBlue)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Excel для руководства", fontWeight = FontWeight.Bold)
                            Text(
                                "Сводка, 4 секции, квартиры, подрядчики, все замечания и диаграммы",
                                color = Muted
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Сформировать .xlsx")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    ElevatedCard(
        modifier = Modifier.width(154.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Surface(
                color = color.copy(alpha = 0.15f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (color == ExcelWhite) BrandDark else color,
                    modifier = Modifier.padding(8.dp).size(22.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(title, color = Muted)
        }
    }
}

@Composable
private fun SmallMetric(title: String, value: Int) {
    Column {
        Text(value.toString(), fontWeight = FontWeight.Bold, color = BrandDark)
        Text(title, style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

@Composable
private fun DefectCard(defect: Defect) {
    val statusColor = statusColor(defect.status)
    val overdue = defect.isOverdue()

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        defect.element.ifBlank { "Замечание" },
                        fontWeight = FontWeight.Bold,
                        color = BrandBlue
                    )
                    if (defect.responsible.isNotBlank()) {
                        Text(
                            "Подрядчик: " + defect.responsible,
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                StatusPill(defect.status.title, statusColor)
            }

            Spacer(Modifier.height(10.dp))
            Text(
                defect.description,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            defect.dueDate?.let { due ->
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = if (overdue) Danger else Muted
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "Срок: " + due.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                        color = if (overdue) Danger else Muted,
                        fontWeight = if (overdue) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (overdue) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "· " + defect.overdueDays() + " дн.",
                            color = Danger,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    val textColor = when (color) {
        ExcelGreen -> Color(0xFF315A13)
        ExcelOrange -> Color(0xFF704F00)
        ExcelBlue -> Color(0xFF005A78)
        ExcelWhite -> BrandDark
        else -> color
    }

    Surface(
        color = color.copy(alpha = if (color == ExcelWhite) 0.75f else 0.18f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LegendRow(label: String, value: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(13.dp)
                .background(color, RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(9.dp))
        Text(label, modifier = Modifier.weight(1f), color = Muted)
        Text(value.toString(), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusBar(label: String, value: Int, total: Int, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label)
            Text(value.toString(), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(
            progress = if (total == 0) 0f else value.toFloat() / total,
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = color,
            trackColor = Color(0xFFEDF1F4)
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun BarList(items: List<Pair<String, Int>>, color: Color) {
    val maxValue = items.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (label, value) ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(value.toString(), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = value.toFloat() / maxValue,
                    modifier = Modifier.fillMaxWidth().height(7.dp),
                    color = color,
                    trackColor = color.copy(alpha = 0.12f)
                )
            }
        }
    }
}

@Composable
private fun OfficialLogo(compact: Boolean) {
    Surface(
        color = BrandBlue,
        shape = RoundedCornerShape(if (compact) 7.dp else 18.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.su555_logo),
            contentDescription = "ООО ГК СУ-555",
            contentScale = ContentScale.Fit,
            modifier = if (compact) {
                Modifier.width(82.dp).height(31.dp).padding(horizontal = 5.dp, vertical = 3.dp)
            } else {
                Modifier.width(244.dp).height(102.dp).padding(horizontal = 16.dp, vertical = 12.dp)
            }
        )
    }
}

private fun shortStatus(status: DefectStatus): String = when (status) {
    DefectStatus.DONE -> "Выполнено"
    DefectStatus.OPEN -> "Белые"
    DefectStatus.ATTENTION -> "Внимание"
    DefectStatus.REPORTED_NOT_DONE -> "Синие"
}

private fun statusColor(status: DefectStatus): Color = when (status) {
    DefectStatus.DONE -> ExcelGreen
    DefectStatus.OPEN -> ExcelWhite
    DefectStatus.ATTENTION -> ExcelOrange
    DefectStatus.REPORTED_NOT_DONE -> ExcelBlue
}
