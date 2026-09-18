package ru.su555.defects

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val BrandBlue = Color(0xFF005B8D)
private val BrandCyan = Color(0xFF2DA9E0)
private val BrandDark = Color(0xFF153347)
private val AppBackground = Color(0xFFF5F8FA)
private val Danger = Color(0xFFC62828)
private val Warning = Color(0xFFEF8C00)
private val Success = Color(0xFF2E7D32)
private val Muted = Color(0xFF6A7882)

@Composable
fun Su555Theme(content: @Composable () -> Unit) {
    val scheme = androidx.compose.material3.lightColorScheme(
        primary = BrandBlue,
        secondary = BrandCyan,
        background = AppBackground,
        surface = Color.White,
        surfaceVariant = Color(0xFFEAF2F6),
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
    DEFECTS("Замечания", Icons.Outlined.ListAlt),
    REPORTS("Отчёты", Icons.Outlined.BarChart)
}

private enum class DefectFilter(val label: String) {
    ALL("Все"),
    OVERDUE("Просрочено"),
    OPEN("Открыто"),
    CLOSED("Устранено")
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
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            val bytes = pendingExport
            if (bytes != null) {
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                                ?: error("Не удалось открыть файл для записи")
                        }
                    }.onSuccess {
                        snackbar.showSnackbar("Excel-отчёт сохранён")
                    }.onFailure {
                        snackbar.showSnackbar("Ошибка сохранения: ${it.message ?: "неизвестная ошибка"}")
                    }
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
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { cursor ->
                                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
                            } ?: "Таблица.xlsx"
                    }
                    val result = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { XlsxParser.parse(it) }
                            ?: error("Не удалось открыть выбранный файл")
                    }
                    sourceName = name
                    parsed = result
                    tabIndex = 0
                    val message = if (result.warnings.isEmpty()) {
                        "Загружено замечаний: ${result.defects.size}"
                    } else {
                        "Файл загружен. Есть предупреждения: ${result.warnings.size}"
                    }
                    snackbar.showSnackbar(message)
                }.onFailure {
                    snackbar.showSnackbar(it.message ?: "Не удалось прочитать Excel")
                }
                loading = false
            }
        }
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
                exportLauncher.launch("Отчёт_замечания_$date.xlsx")
            }.onFailure {
                snackbar.showSnackbar("Не удалось сформировать Excel: ${it.message}")
            }
            loading = false
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MiniBrandMark()
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Устранение замечаний",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (sourceName.isNotBlank()) {
                                Text(
                                    sourceName,
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
                    IconButton(onClick = {
                        importLauncher.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel",
                                "application/octet-stream"
                            )
                        )
                    }) {
                        Icon(Icons.Outlined.UploadFile, contentDescription = "Загрузить Excel")
                    }
                    if (parsed != null) {
                        IconButton(onClick = ::startExport) {
                            Icon(Icons.Outlined.Download, contentDescription = "Экспорт")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            if (parsed != null) {
                NavigationBar(
                    containerColor = Color.White,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    AppTab.entries.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = tabIndex == index,
                            onClick = { tabIndex = index },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, fontSize = 11.sp) }
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
            if (current == null) {
                EmptyImportScreen(
                    loading = loading,
                    onImport = {
                        importLauncher.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel",
                                "application/octet-stream"
                            )
                        )
                    }
                )
            } else {
                when (AppTab.entries[tabIndex]) {
                    AppTab.OVERVIEW -> OverviewScreen(
                        defects = current.defects,
                        warnings = current.warnings,
                        detected = current.detectedColumns,
                        onReload = {
                            importLauncher.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.ms-excel",
                                    "application/octet-stream"
                                )
                            )
                        }
                    )
                    AppTab.APARTMENTS -> ApartmentsScreen(current.defects)
                    AppTab.DEFECTS -> DefectsScreen(current.defects)
                    AppTab.REPORTS -> ReportsScreen(current.defects, ::startExport)
                }
            }

            if (loading && current != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 8.dp
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
        LargeBrandMark()
        Spacer(Modifier.height(26.dp))
        Text(
            "Устранение замечаний",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = BrandDark
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "ГК «СУ-555»",
            style = MaterialTheme.typography.titleMedium,
            color = BrandBlue,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "Загрузите таблицу Excel с замечаниями. Приложение само определит основные колонки, рассчитает просрочки, соберёт квартиры и построит отчётность.",
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
                Text("Выбрать Excel-файл")
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Формат: .xlsx",
            style = MaterialTheme.typography.labelMedium,
            color = Muted
        )
    }
}

@Composable
private fun OverviewScreen(
    defects: List<Defect>,
    warnings: List<String>,
    detected: Map<String, String>,
    onReload: () -> Unit
) {
    val d = remember(defects) { defects.dashboard() }
    val categories = remember(defects) { defects.byCategory().take(7) }
    val urgent = remember(defects) {
        defects.filter { it.isOverdue() }
            .sortedByDescending { it.overdueDays() }
            .take(6)
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
                    Text("Состояние передачи квартир на сегодня", color = Muted)
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
                item { MetricCard("Всего", d.total.toString(), Icons.Outlined.Assessment, BrandBlue) }
                item { MetricCard("Открыто", d.open.toString(), Icons.Outlined.Schedule, Warning) }
                item { MetricCard("Просрочено", d.overdue.toString(), Icons.Outlined.WarningAmber, Danger) }
                item { MetricCard("Устранено", d.closed.toString(), Icons.Outlined.CheckCircle, Success) }
                item { MetricCard("Квартир", d.apartments.toString(), Icons.Outlined.Apartment, BrandCyan) }
            }
        }

        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                Column(Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text("Общий прогресс", fontWeight = FontWeight.SemiBold)
                            Text("${d.closed} из ${d.total} замечаний устранено", color = Muted)
                        }
                        Text(
                            "${d.completionPercent}%",
                            style = MaterialTheme.typography.headlineMedium,
                            color = BrandBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = d.completionPercent / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                    )
                }
            }
        }

        if (warnings.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5E6))) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Warning)
                            Spacer(Modifier.width(8.dp))
                            Text("Проверка файла", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        warnings.forEach { Text("• $it", color = BrandDark) }
                    }
                }
            }
        }

        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Структура замечаний", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusDonutChart(
                            open = d.open,
                            overdue = d.overdue,
                            closed = d.closed,
                            modifier = Modifier.size(138.dp)
                        )
                        Spacer(Modifier.width(18.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LegendRow("Открыто", d.open, Warning)
                            LegendRow("Просрочено", d.overdue, Danger)
                            LegendRow("Устранено", d.closed, Success)
                        }
                    }
                }
            }
        }

        if (categories.isNotEmpty()) {
            item {
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Топ категорий", fontWeight = FontWeight.Bold)
                        Text("По количеству замечаний", color = Muted)
                        Spacer(Modifier.height(14.dp))
                        BarList(categories.map { it.name to it.total }, BrandBlue)
                    }
                }
            }
        }

        if (urgent.isNotEmpty()) {
            item {
                Text("Самые срочные просрочки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            items(urgent) { defect ->
                DefectCard(defect)
            }
        }

        if (detected.isNotEmpty()) {
            item {
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Распознанные колонки", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        detected.forEach { (field, column) ->
                            Text("$field → $column", style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApartmentsScreen(defects: List<Defect>) {
    val summaries = remember(defects) { defects.byApartment() }
    var search by remember { mutableStateOf("") }
    val filtered = remember(summaries, search) {
        if (search.isBlank()) summaries else summaries.filter { it.apartment.contains(search, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Квартиры", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Сначала показаны квартиры с просроченными работами", color = Muted)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                label = { Text("Номер квартиры") }
            )
        }

        items(filtered) { item ->
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Квартира ${item.apartment}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (item.overdue > 0) {
                            StatusPill("${item.overdue} просроч.", Danger)
                        } else if (item.open > 0) {
                            StatusPill("${item.open} открыто", Warning)
                        } else {
                            StatusPill("Готово", Success)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        SmallMetric("Всего", item.total)
                        SmallMetric("Открыто", item.open)
                        SmallMetric("Устранено", item.closed)
                    }
                    if (item.nearestDue != null || item.maxOverdueDays > 0) {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(10.dp))
                        if (item.maxOverdueDays > 0) {
                            Text(
                                "Максимальная просрочка: ${item.maxOverdueDays} дн.",
                                color = Danger,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                "Ближайший срок: ${item.nearestDue?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}",
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
private fun DefectsScreen(defects: List<Defect>) {
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(DefectFilter.ALL) }

    val filtered = remember(defects, search, filter) {
        defects.filter { d ->
            val statusOk = when (filter) {
                DefectFilter.ALL -> true
                DefectFilter.OVERDUE -> d.isOverdue()
                DefectFilter.OPEN -> !d.isClosed
                DefectFilter.CLOSED -> d.isClosed
            }
            val q = search.trim().lowercase()
            val textOk = q.isBlank() || listOf(
                d.apartment,
                d.description,
                d.category,
                d.responsible
            ).any { it.lowercase().contains(q) }
            statusOk && textOk
        }.sortedWith(
            compareByDescending<Defect> { it.isOverdue() }
                .thenByDescending { it.overdueDays() }
                .thenBy { it.apartment }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Что необходимо сделать", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Просроченные замечания автоматически поднимаются наверх", color = Muted)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                label = { Text("Поиск по квартире, работе, подрядчику") }
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DefectFilter.entries.forEach {
                    FilterChip(
                        selected = filter == it,
                        onClick = { filter = it },
                        label = { Text(it.label) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Найдено: ${filtered.size}", style = MaterialTheme.typography.labelMedium, color = Muted)
        }

        items(filtered) { defect ->
            DefectCard(defect)
        }
    }
}

@Composable
private fun ReportsScreen(defects: List<Defect>, onExport: () -> Unit) {
    val d = remember(defects) { defects.dashboard() }
    val categories = remember(defects) { defects.byCategory().take(10) }
    val responsible = remember(defects) {
        defects
            .filter { it.responsible.isNotBlank() && !it.isClosed }
            .groupingBy { it.responsible }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key to it.value }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Графики и отчётность", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Аналитика по текущему загруженному Excel", color = Muted)
        }

        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Выполнение", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${d.completionPercent}%",
                        style = MaterialTheme.typography.displaySmall,
                        color = BrandBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Text("устранено от общего количества", color = Muted)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = d.completionPercent / 100f,
                        modifier = Modifier.fillMaxWidth().height(10.dp)
                    )
                }
            }
        }

        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Статусы", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    StatusDonutChart(
                        open = d.open,
                        overdue = d.overdue,
                        closed = d.closed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                }
            }
        }

        if (categories.isNotEmpty()) {
            item {
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Категории замечаний", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(14.dp))
                        BarList(categories.map { it.name to it.total }, BrandCyan)
                    }
                }
            }
        }

        if (responsible.isNotEmpty()) {
            item {
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Открытые замечания по ответственным", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(14.dp))
                        BarList(responsible, Warning)
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
                            Text("Экспортировать отчёт в Excel", fontWeight = FontWeight.Bold)
                            Text("Сводка, квартиры, категории, список замечаний и диаграммы", color = Muted)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Скачать Excel-отчёт")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: ImageVector, color: Color) {
    ElevatedCard(
        modifier = Modifier.width(152.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Surface(
                color = color.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
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
    val overdue = defect.isOverdue()
    val statusColor = when {
        defect.isClosed -> Success
        overdue -> Danger
        else -> Warning
    }

    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (defect.apartment.isBlank()) "Квартира не указана" else "Квартира ${defect.apartment}",
                    fontWeight = FontWeight.Bold,
                    color = BrandBlue
                )
                StatusPill(
                    when {
                        defect.isClosed -> "Устранено"
                        overdue -> "Просрочка ${defect.overdueDays()} дн."
                        else -> "Открыто"
                    },
                    statusColor
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(defect.description, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)

            val details = mutableListOf<String>()
            if (defect.category.isNotBlank()) details += defect.category
            if (defect.responsible.isNotBlank()) details += defect.responsible
            if (details.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(details.joinToString(" • "), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            defect.dueDate?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Срок: ${it.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}",
                    color = if (overdue) Danger else Muted,
                    fontWeight = if (overdue) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StatusDonutChart(open: Int, overdue: Int, closed: Int, modifier: Modifier = Modifier) {
    val notOverdueOpen = (open - overdue).coerceAtLeast(0)
    val total = max(1, notOverdueOpen + overdue + closed)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            val stroke = size.minDimension * 0.16f
            val chartSize = Size(size.minDimension - stroke, size.minDimension - stroke)
            val topLeft = Offset((size.width - chartSize.width) / 2, (size.height - chartSize.height) / 2)
            var start = -90f

            listOf(
                Triple(notOverdueOpen, Warning, "open"),
                Triple(overdue, Danger, "overdue"),
                Triple(closed, Success, "closed")
            ).forEach { (value, color, _) ->
                if (value > 0) {
                    val sweep = value.toFloat() / total * 360f
                    drawArc(
                        color = color,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = chartSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt)
                    )
                    start += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text((open + closed).toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("всего", style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

@Composable
private fun LegendRow(label: String, value: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.width(92.dp), color = Muted)
        Text(value.toString(), fontWeight = FontWeight.Bold)
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
private fun MiniBrandMark() {
    Canvas(Modifier.size(34.dp)) {
        val w = size.width
        val h = size.height
        val p1 = Path().apply {
            moveTo(w * 0.12f, h * 0.25f)
            lineTo(w * 0.48f, h * 0.08f)
            lineTo(w * 0.48f, h * 0.36f)
            lineTo(w * 0.31f, h * 0.44f)
            lineTo(w * 0.31f, h * 0.72f)
            lineTo(w * 0.48f, h * 0.80f)
            lineTo(w * 0.48f, h * 0.94f)
            lineTo(w * 0.12f, h * 0.78f)
            close()
        }
        val p2 = Path().apply {
            moveTo(w * 0.53f, h * 0.08f)
            lineTo(w * 0.89f, h * 0.25f)
            lineTo(w * 0.89f, h * 0.78f)
            lineTo(w * 0.53f, h * 0.94f)
            lineTo(w * 0.53f, h * 0.78f)
            lineTo(w * 0.72f, h * 0.69f)
            lineTo(w * 0.72f, h * 0.35f)
            lineTo(w * 0.53f, h * 0.27f)
            close()
        }
        drawPath(p1, BrandBlue)
        drawPath(p2, BrandCyan)
    }
}

@Composable
private fun LargeBrandMark() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 30.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MiniBrandMark()
            Spacer(Modifier.height(8.dp))
            Text(
                "СУ555",
                color = BrandBlue,
                fontWeight = FontWeight.Black,
                fontSize = 34.sp,
                letterSpacing = 1.sp
            )
            Text(
                "ГРУППА КОМПАНИЙ",
                color = BrandCyan,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 1.3.sp
            )
        }
    }
}
