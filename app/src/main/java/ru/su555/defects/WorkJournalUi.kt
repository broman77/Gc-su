package ru.su555.defects

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NavigateBefore
import androidx.compose.material.icons.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val WBrandBlue = Color(0xFF0B4F86)
private val WBrandLightBlue = Color(0xFF62B5E5)
private val WBrandDark = Color(0xFF153347)
private val WBackground = Color(0xFFF4F7FA)
private val WMuted = Color(0xFF6A7882)
private val WDanger = Color(0xFFC62828)
private val WGreen = Color(0xFF92D050)
private val WOrange = Color(0xFFFFC000)
private val WBlue = Color(0xFF00B0F0)
private val WWhite = Color(0xFFE6EAEE)

private enum class WorkTab(val label: String, val icon: ImageVector) {
    OVERVIEW("Сводка", Icons.Outlined.Home),
    APARTMENTS("Квартиры", Icons.Outlined.Apartment),
    REPORTS("Отчёты", Icons.Outlined.BarChart)
}

private data class PendingPhoto(
    val defectId: Long,
    val file: File,
    val label: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkJournalApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val store = remember { LocalStore(context.applicationContext) }

    var defects by remember { mutableStateOf(store.activeDefects()) }
    var sourceName by remember { mutableStateOf(if (defects.isEmpty()) "" else "Локальная рабочая база") }
    var tabIndex by remember { mutableIntStateOf(0) }
    var selectedApartment by remember { mutableStateOf<ApartmentSummary?>(null) }
    var editingDefect by remember { mutableStateOf<Defect?>(null) }
    var isNewDefect by remember { mutableStateOf(false) }
    var historyDefect by remember { mutableStateOf<Defect?>(null) }
    var archiveTarget by remember { mutableStateOf<Defect?>(null) }
    var showArchive by remember { mutableStateOf(false) }
    var selectedContractor by remember { mutableStateOf<String?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPhoto by remember { mutableStateOf<PendingPhoto?>(null) }
    var exportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var loading by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var walkMode by remember { mutableStateOf(false) }
    var reminderEnabled by remember { mutableStateOf(ReminderScheduler.isEnabled(context)) }

    fun refresh() {
        val loaded = store.activeDefects()
        defects = loaded
        selectedApartment?.let { current ->
            selectedApartment = loaded.byApartment().firstOrNull {
                it.building == current.building &&
                    it.section == current.section &&
                    it.apartment == current.apartment
            }
        }
        refreshToken++
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        val bytes = exportBytes
        if (uri != null && bytes != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                            ?: error("Не удалось открыть файл")
                    }
                }.onSuccess {
                    snackbar.showSnackbar("Excel сохранён")
                }.onFailure {
                    snackbar.showSnackbar("Ошибка сохранения: " + (it.message ?: "неизвестно"))
                }
            }
        }
    }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val pending = pendingPhoto
        if (pending != null) {
            if (success) {
                store.addPhoto(pending.defectId, pending.file.absolutePath, pending.label)
                refreshToken++
                scope.launch { snackbar.showSnackbar("Фото сохранено") }
            } else {
                pending.file.delete()
            }
        }
        pendingPhoto = null
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ReminderScheduler.setEnabled(context, true)
            reminderEnabled = true
            scope.launch { snackbar.showSnackbar("Ежедневная сводка включена на 08:00") }
        } else {
            reminderEnabled = false
            scope.launch { snackbar.showSnackbar("Нет разрешения на уведомления") }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingImportUri = uri
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

    fun importExcel(uri: Uri) {
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
                        ?: error("Не удалось открыть Excel")
                }
                withContext(Dispatchers.IO) {
                    store.replaceAllFromExcel(result.defects)
                }
                sourceName = name
                selectedApartment = null
                walkMode = false
                refresh()
                result.warnings
            }.onSuccess { warnings ->
                snackbar.showSnackbar(
                    if (warnings.isEmpty()) "Excel загружен в локальную базу"
                    else "Excel загружен, предупреждений: " + warnings.size
                )
            }.onFailure {
                snackbar.showSnackbar(it.message ?: "Ошибка импорта")
            }
            loading = false
        }
    }

    fun exportAll() {
        if (defects.isEmpty()) return
        scope.launch {
            loading = true
            runCatching {
                withContext(Dispatchers.Default) { XlsxExporter.build(defects) }
            }.onSuccess {
                exportBytes = it
                val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                exportLauncher.launch("Мироновская_актуальный_реестр_$date.xlsx")
            }.onFailure {
                snackbar.showSnackbar("Ошибка Excel: " + (it.message ?: "неизвестно"))
            }
            loading = false
        }
    }

    fun capturePhoto(defect: Defect, label: String) {
        val dir = File(context.filesDir, "defect_photos").apply { mkdirs() }
        val file = File(
            dir,
            "d${defect.id}_${System.currentTimeMillis()}.jpg"
        )
        pendingPhoto = PendingPhoto(defect.id, file, label)
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
        takePhotoLauncher.launch(uri)
    }

    fun toggleReminder(enabled: Boolean) {
        if (!enabled) {
            ReminderScheduler.setEnabled(context, false)
            reminderEnabled = false
            return
        }

        if (
            Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ReminderScheduler.setEnabled(context, true)
            reminderEnabled = true
            scope.launch { snackbar.showSnackbar("Ежедневная сводка включена на 08:00") }
        }
    }

    val walkApartments = remember(defects) {
        defects.byApartment()
            .filter { it.open > 0 }
            .sortedWith(
                compareBy<ApartmentSummary> { it.building }
                    .thenBy { it.section }
                    .thenBy { it.apartment }
            )
    }

    fun openApartment(item: ApartmentSummary, walking: Boolean = false) {
        selectedApartment = item
        walkMode = walking
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (selectedApartment != null) {
                        IconButton(onClick = {
                            selectedApartment = null
                            walkMode = false
                        }) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
                title = {
                    val apartment = selectedApartment
                    if (apartment != null) {
                        Column {
                            Text("Квартира " + apartment.apartment, fontWeight = FontWeight.Bold)
                            Text(
                                "Корпус " + apartment.building + " · секция " + apartment.section,
                                style = MaterialTheme.typography.labelSmall,
                                color = WMuted
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WorkLogo(compact = true)
                            Spacer(Modifier.width(9.dp))
                            Column {
                                Text(
                                    "Устранение замечаний",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    sourceName.ifBlank { "Мироновская, д. 30" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WMuted,
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
                            Icon(Icons.Outlined.UploadFile, contentDescription = "Excel")
                        }
                        if (defects.isNotEmpty()) {
                            IconButton(onClick = ::exportAll) {
                                Icon(Icons.Outlined.Download, contentDescription = "Экспорт")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            if (defects.isNotEmpty() && selectedApartment == null) {
                NavigationBar(
                    containerColor = Color.White,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    WorkTab.entries.forEachIndexed { index, tab ->
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
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WBackground)
                .padding(inner)
        ) {
            when {
                defects.isEmpty() -> WorkEmptyScreen(loading, ::chooseExcel)
                selectedApartment != null -> {
                    val current = selectedApartment!!
                    val apartmentDefects = defects.defectsForApartment(
                        current.building,
                        current.section,
                        current.apartment
                    )
                    val walkIndex = walkApartments.indexOfFirst {
                        it.building == current.building &&
                            it.section == current.section &&
                            it.apartment == current.apartment
                    }
                    ApartmentWorkScreen(
                        apartment = current,
                        defects = apartmentDefects,
                        store = store,
                        refreshToken = refreshToken,
                        walkMode = walkMode,
                        walkPosition = walkIndex,
                        walkTotal = walkApartments.size,
                        onPrevious = {
                            if (walkIndex > 0) selectedApartment = walkApartments[walkIndex - 1]
                        },
                        onNext = {
                            if (walkIndex >= 0 && walkIndex < walkApartments.lastIndex) {
                                selectedApartment = walkApartments[walkIndex + 1]
                            }
                        },
                        onAdd = {
                            editingDefect = Defect(
                                building = current.building,
                                section = current.section,
                                apartment = current.apartment,
                                address = "к. ${current.building}, кв. ${current.apartment}",
                                element = "",
                                description = "",
                                responsible = "",
                                status = DefectStatus.OPEN,
                                dueDate = null,
                                sourceSheet = "Приложение",
                                sourceRow = 0
                            )
                            isNewDefect = true
                        },
                        onEdit = {
                            editingDefect = it
                            isNewDefect = false
                        },
                        onStatus = { defect, status ->
                            store.setStatus(defect.id, status)
                            refresh()
                        },
                        onHistory = { historyDefect = it },
                        onArchive = { archiveTarget = it },
                        onPhoto = ::capturePhoto,
                        onBulk = { responsible, status, dueDate ->
                            apartmentDefects.filter { !it.isClosed }.forEach { defect ->
                                store.updateDefect(
                                    defect.copy(
                                        responsible = responsible.ifBlank { defect.responsible },
                                        status = status ?: defect.status,
                                        dueDate = dueDate ?: defect.dueDate
                                    )
                                )
                            }
                            refresh()
                        }
                    )
                }
                else -> when (WorkTab.entries[tabIndex]) {
                    WorkTab.OVERVIEW -> WorkOverview(
                        defects = defects,
                        store = store,
                        onOpenApartments = { tabIndex = WorkTab.APARTMENTS.ordinal }
                    )
                    WorkTab.APARTMENTS -> WorkApartments(
                        defects = defects,
                        onOpen = { openApartment(it, false) },
                        onWalk = {
                            walkApartments.firstOrNull()?.let { openApartment(it, true) }
                        }
                    )
                    WorkTab.REPORTS -> WorkReports(
                        defects = defects,
                        store = store,
                        reminderEnabled = reminderEnabled,
                        onReminder = ::toggleReminder,
                        onExport = ::exportAll,
                        onArchive = { showArchive = true },
                        onContractor = { selectedContractor = it }
                    )
                }
            }

            if (loading) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Обработка…")
                    }
                }
            }
        }
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Загрузить новый Excel?") },
            text = {
                Text(
                    if (defects.isEmpty()) {
                        "Данные из Excel будут сохранены в приложении и останутся после закрытия."
                    } else {
                        "Новый Excel заменит текущую локальную базу, включая историю и привязанные фото. Перед заменой лучше выгрузить актуальный реестр."
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    pendingImportUri = null
                    importExcel(uri)
                }) { Text("Загрузить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("Отмена") }
            }
        )
    }

    editingDefect?.let { draft ->
        DefectEditorDialog(
            initial = draft,
            isNew = isNewDefect,
            onDismiss = { editingDefect = null },
            onSave = { saved ->
                if (isNewDefect) store.addDefect(saved) else store.updateDefect(saved)
                editingDefect = null
                refresh()
            }
        )
    }

    historyDefect?.let { defect ->
        HistoryDialog(
            defect = defect,
            history = remember(defect.id, refreshToken) { store.history(defect.id) },
            photos = remember(defect.id, refreshToken) { store.photos(defect.id) },
            onDismiss = { historyDefect = null },
            onRemovePhoto = {
                File(it.path).delete()
                store.removePhoto(it)
                refreshToken++
            }
        )
    }

    archiveTarget?.let { defect ->
        AlertDialog(
            onDismissRequest = { archiveTarget = null },
            title = { Text("Убрать замечание в архив?") },
            text = { Text(defect.description) },
            confirmButton = {
                Button(onClick = {
                    store.archiveDefect(defect.id)
                    archiveTarget = null
                    refresh()
                }) { Text("В архив") }
            },
            dismissButton = {
                TextButton(onClick = { archiveTarget = null }) { Text("Отмена") }
            }
        )
    }

    if (showArchive) {
        ArchiveDialog(
            items = remember(refreshToken) { store.archivedDefects() },
            onDismiss = { showArchive = false },
            onRestore = {
                store.restoreDefect(it.id)
                refresh()
            }
        )
    }

    selectedContractor?.let { contractor ->
        ContractorDialog(
            contractor = contractor,
            defects = defects.filter {
                splitResponsible(it.responsible).any { name ->
                    name.equals(contractor, ignoreCase = true)
                }
            },
            onDismiss = { selectedContractor = null }
        )
    }
}

@Composable
private fun WorkEmptyScreen(loading: Boolean, onImport: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WorkLogo(compact = false)
        Spacer(Modifier.height(24.dp))
        Text(
            "Рабочий журнал замечаний",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text("Мироновская, д. 30 · 568 квартир", color = WBrandBlue)
        Spacer(Modifier.height(16.dp))
        Text(
            "Загрузите Excel один раз. После этого замечания можно добавлять, редактировать, фотографировать и закрывать прямо в приложении.",
            color = WMuted
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onImport, enabled = !loading) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Загрузить Excel")
        }
    }
}

@Composable
private fun WorkOverview(
    defects: List<Defect>,
    store: LocalStore,
    onOpenApartments: () -> Unit
) {
    val d = remember(defects) { defects.dashboard() }
    val sections = remember(defects) { defects.bySection() }
    val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    val closed7 = remember(defects) {
        store.historySince(sevenDaysAgo).count {
            it.details.contains("→ Выполнено")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Сводка", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Рабочее состояние объекта на сегодня", color = WMuted)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { WorkMetric("С просрочкой", d.apartmentsWithOverdue.toString(), Icons.Outlined.WarningAmber, WDanger) }
                item { WorkMetric("В работе", d.apartmentsWithOpen.toString(), Icons.Outlined.Schedule, WOrange) }
                item { WorkMetric("Закрыты", d.apartmentsFullyDone.toString(), Icons.Outlined.CheckCircle, WGreen) }
                item { WorkMetric("Закрыто за 7 дней", closed7.toString(), Icons.Outlined.Assessment, WBrandBlue) }
            }
        }
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                Column(Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Готовность замечаний", fontWeight = FontWeight.Bold)
                            Text("${d.closed} из ${d.totalDefects} выполнено", color = WMuted)
                        }
                        Text(
                            "${d.completionPercent}%",
                            color = WBrandBlue,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = d.completionPercent / 100f,
                        modifier = Modifier.fillMaxWidth().height(9.dp),
                        color = WGreen
                    )
                    Spacer(Modifier.height(14.dp))
                    WorkLegend("Белые · не выполнено", d.plainOpen, WWhite)
                    WorkLegend("Оранжевые · внимание", d.attention, WOrange)
                    WorkLegend("Синие · отчёт не принят", d.reportedNotDone, WBlue)
                    WorkLegend("Зелёные · выполнено", d.closed, WGreen)
                }
            }
        }
        item {
            Button(onClick = onOpenApartments, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Apartment, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Перейти к квартирам")
            }
        }
        item {
            Text("Корпуса и секции", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        items(sections) { section ->
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(section.title, fontWeight = FontWeight.Bold, color = WBrandBlue)
                            Text(
                                "${section.apartmentsWithOpen} кв. в работе · ${section.apartmentsWithOverdue} с просрочкой",
                                color = if (section.apartmentsWithOverdue > 0) WDanger else WMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text("${section.completionPercent}%", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = section.completionPercent / 100f,
                        modifier = Modifier.fillMaxWidth().height(7.dp),
                        color = WGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkApartments(
    defects: List<Defect>,
    onOpen: (ApartmentSummary) -> Unit,
    onWalk: () -> Unit
) {
    val summaries = remember(defects) { defects.byApartment() }
    var search by remember { mutableStateOf("") }
    var building by remember { mutableIntStateOf(0) }
    var section by remember { mutableIntStateOf(0) }
    var onlyOverdue by remember { mutableStateOf(false) }

    val filtered = remember(summaries, search, building, section, onlyOverdue) {
        summaries.filter {
            (building == 0 || it.building == building) &&
                (section == 0 || it.section == section) &&
                (!onlyOverdue || it.hasOverdue) &&
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
            Text("Откройте квартиру или начните последовательный обход", color = WMuted)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                label = { Text("Номер квартиры") }
            )
            Spacer(Modifier.height(8.dp))
            WorkChoiceRow(
                labels = listOf("Все корпуса", "Корпус 1", "Корпус 2"),
                selected = building,
                onSelect = {
                    building = it
                    if (it == 0) section = 0
                }
            )
            Spacer(Modifier.height(8.dp))
            WorkChoiceRow(
                labels = listOf("Все секции", "Секция 1", "Секция 2"),
                selected = section,
                onSelect = { section = it }
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = onlyOverdue,
                    onClick = { onlyOverdue = !onlyOverdue },
                    label = { Text("Только просрочка") }
                )
                OutlinedButton(onClick = onWalk) {
                    Icon(Icons.Outlined.NavigateNext, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("Начать обход")
                }
            }
        }

        items(filtered) { item ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(item) },
                colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Квартира ${item.apartment}", fontWeight = FontWeight.Bold)
                            Text("Корпус ${item.building} · секция ${item.section}", color = WMuted)
                        }
                        when {
                            item.hasOverdue -> WorkPill("Просрочка", WDanger)
                            item.reportedNotDone > 0 -> WorkPill("Есть синие", WBlue)
                            item.attention > 0 -> WorkPill("Внимание", WOrange)
                            item.open > 0 -> WorkPill("В работе", WBrandDark)
                            else -> WorkPill("Готово", WGreen)
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = WMuted)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        WorkSmallMetric("Всего", item.total)
                        WorkSmallMetric("Открыто", item.open)
                        WorkSmallMetric("Выполнено", item.closed)
                    }
                    if (item.hasOverdue) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Просрочено замечаний: ${item.overdueDefects} · максимум ${item.maxOverdueDays} дн.",
                            color = WDanger,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApartmentWorkScreen(
    apartment: ApartmentSummary,
    defects: List<Defect>,
    store: LocalStore,
    refreshToken: Int,
    walkMode: Boolean,
    walkPosition: Int,
    walkTotal: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Defect) -> Unit,
    onStatus: (Defect, DefectStatus) -> Unit,
    onHistory: (Defect) -> Unit,
    onArchive: (Defect) -> Unit,
    onPhoto: (Defect, String) -> Unit,
    onBulk: (String, DefectStatus?, LocalDate?) -> Unit
) {
    var filter by remember { mutableStateOf<DefectStatus?>(null) }
    var showBulk by remember { mutableStateOf(false) }
    val filtered = remember(defects, filter) {
        if (filter == null) defects else defects.filter { it.status == filter }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (walkMode) {
            item {
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFEAF4FA))) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPrevious, enabled = walkPosition > 0) {
                            Icon(Icons.Outlined.NavigateBefore, contentDescription = "Предыдущая")
                        }
                        Text(
                            "Обход · ${(walkPosition + 1).coerceAtLeast(1)} из $walkTotal",
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = onNext,
                            enabled = walkPosition >= 0 && walkPosition < walkTotal - 1
                        ) {
                            Icon(Icons.Outlined.NavigateNext, contentDescription = "Следующая")
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        WorkSmallMetric("Всего", apartment.total)
                        WorkSmallMetric("Открыто", apartment.open)
                        WorkSmallMetric("Выполнено", apartment.closed)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (apartment.plainOpen > 0) WorkPill("${apartment.plainOpen} бел.", WWhite)
                        if (apartment.attention > 0) WorkPill("${apartment.attention} оранж.", WOrange)
                        if (apartment.reportedNotDone > 0) WorkPill("${apartment.reportedNotDone} син.", WBlue)
                        if (apartment.hasOverdue) WorkPill("Просрочка", WDanger)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onAdd) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("Добавить")
                        }
                        OutlinedButton(
                            onClick = { showBulk = true },
                            enabled = defects.any { !it.isClosed }
                        ) {
                            Text("Массово")
                        }
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
                    label = { Text("Все (${defects.size})") }
                )
                DefectStatus.entries.forEach { status ->
                    val count = defects.count { it.status == status }
                    if (count > 0) {
                        FilterChip(
                            selected = filter == status,
                            onClick = { filter = status },
                            label = { Text(shortWorkStatus(status) + " (" + count + ")") }
                        )
                    }
                }
            }
        }

        items(filtered, key = { it.id }) { defect ->
            val photos = remember(defect.id, refreshToken) { store.photos(defect.id) }
            DefectWorkCard(
                defect = defect,
                photos = photos,
                onEdit = { onEdit(defect) },
                onStatus = { onStatus(defect, it) },
                onHistory = { onHistory(defect) },
                onArchive = { onArchive(defect) },
                onPhoto = { label -> onPhoto(defect, label) }
            )
        }
    }

    if (showBulk) {
        BulkEditDialog(
            count = defects.count { !it.isClosed },
            onDismiss = { showBulk = false },
            onApply = { responsible, status, due ->
                onBulk(responsible, status, due)
                showBulk = false
            }
        )
    }
}

@Composable
private fun DefectWorkCard(
    defect: Defect,
    photos: List<DefectPhoto>,
    onEdit: () -> Unit,
    onStatus: (DefectStatus) -> Unit,
    onHistory: () -> Unit,
    onArchive: () -> Unit,
    onPhoto: (String) -> Unit
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        defect.element.ifBlank { "Замечание" },
                        fontWeight = FontWeight.Bold,
                        color = WBrandBlue
                    )
                    if (defect.responsible.isNotBlank()) {
                        Text("Подрядчик: " + defect.responsible, color = WMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                WorkPill(shortWorkStatus(defect.status), workStatusColor(defect.status))
            }
            Spacer(Modifier.height(9.dp))
            Text(defect.description)

            defect.dueDate?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Срок: " + it.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) +
                        if (defect.isOverdue()) " · +" + defect.overdueDays() + " дн." else "",
                    color = if (defect.isOverdue()) WDanger else WMuted,
                    fontWeight = if (defect.isOverdue()) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DefectStatus.entries.forEach { status ->
                    FilterChip(
                        selected = defect.status == status,
                        onClick = { onStatus(status) },
                        label = { Text(shortWorkStatus(status), fontSize = 11.sp) }
                    )
                }
            }

            if (photos.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(photos, key = { it.id }) { photo ->
                        val bitmap = remember(photo.path) { BitmapFactory.decodeFile(photo.path) }
                        if (bitmap != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = photo.label,
                                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Text(photo.label, fontSize = 10.sp, color = WMuted)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Изменить")
                }
                TextButton(onClick = onHistory) {
                    Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("История")
                }
                TextButton(onClick = { onPhoto("До / проверка") }) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Фото до")
                }
                TextButton(onClick = { onPhoto("После") }) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Фото после")
                }
                TextButton(onClick = onArchive) {
                    Icon(Icons.Outlined.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Архив")
                }
            }
        }
    }
}

@Composable
private fun WorkReports(
    defects: List<Defect>,
    store: LocalStore,
    reminderEnabled: Boolean,
    onReminder: (Boolean) -> Unit,
    onExport: () -> Unit,
    onArchive: () -> Unit,
    onContractor: (String) -> Unit
) {
    val d = remember(defects) { defects.dashboard() }
    val contractors = remember(defects) { defects.byResponsible().filter { it.open > 0 }.take(10) }
    val sections = remember(defects) { defects.bySection() }
    val archiveCount = remember(defects) { store.archivedCount() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Отчёт руководителю", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Квартиры, риски, подрядчики и динамика", color = WMuted)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { WorkMetric("С просрочкой", d.apartmentsWithOverdue.toString(), Icons.Outlined.WarningAmber, WDanger) }
                item { WorkMetric("В работе", d.apartmentsWithOpen.toString(), Icons.Outlined.Schedule, WOrange) }
                item { WorkMetric("Закрыты", d.apartmentsFullyDone.toString(), Icons.Outlined.CheckCircle, WGreen) }
                item { WorkMetric("Готовность", "${d.completionPercent}%", Icons.Outlined.BarChart, WBrandBlue) }
            }
        }
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                Column(Modifier.padding(18.dp)) {
                    Text("По секциям", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    sections.forEachIndexed { index, section ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(section.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${section.apartmentsWithOpen} кв. в работе · ${section.apartmentsWithOverdue} с просрочкой",
                                    color = WMuted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text("${section.completionPercent}%", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(5.dp))
                        LinearProgressIndicator(
                            progress = section.completionPercent / 100f,
                            modifier = Modifier.fillMaxWidth().height(7.dp),
                            color = WGreen
                        )
                        if (index != sections.lastIndex) Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
        if (contractors.isNotEmpty()) {
            item {
                Text("Подрядчики", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("МКД и СТМ входят в СМУ; электрика и сантехника — в УИР", color = WMuted)
            }
            items(contractors) { contractor ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().clickable { onContractor(contractor.name) },
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(contractor.name, fontWeight = FontWeight.Bold)
                            Text(
                                "Открыто ${contractor.open} · просрочено ${contractor.overdue}",
                                color = WMuted
                            )
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Notifications, contentDescription = null, tint = WBrandBlue)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Ежедневная сводка", fontWeight = FontWeight.Bold)
                        Text("В 08:00: просроченные квартиры и сроки на сегодня", color = WMuted)
                    }
                    Switch(checked = reminderEnabled, onCheckedChange = onReminder)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onArchive, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Archive, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("Архив ($archiveCount)")
                }
                Button(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("Excel")
                }
            }
        }
    }
}

@Composable
private fun DefectEditorDialog(
    initial: Defect,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Defect) -> Unit
) {
    var element by remember(initial.id, isNew) { mutableStateOf(initial.element) }
    var description by remember(initial.id, isNew) { mutableStateOf(initial.description) }
    var responsible by remember(initial.id, isNew) { mutableStateOf(initial.responsible) }
    var dueText by remember(initial.id, isNew) {
        mutableStateOf(initial.dueDate?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: "")
    }
    var status by remember(initial.id, isNew) { mutableStateOf(initial.status) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Новое замечание" else "Редактировать замечание") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = element,
                    onValueChange = { element = it },
                    label = { Text("Элемент / помещение") },
                    modifier = Modifier.width(460.dp)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание замечания") },
                    modifier = Modifier.width(460.dp),
                    minLines = 3
                )
                OutlinedTextField(
                    value = responsible,
                    onValueChange = { responsible = it },
                    label = { Text("Подрядчик") },
                    modifier = Modifier.width(460.dp)
                )
                OutlinedTextField(
                    value = dueText,
                    onValueChange = { dueText = it },
                    label = { Text("Срок, ДД.ММ.ГГГГ") },
                    modifier = Modifier.width(460.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DefectStatus.entries.forEach {
                        FilterChip(
                            selected = status == it,
                            onClick = { status = it },
                            label = { Text(shortWorkStatus(it), fontSize = 11.sp) }
                        )
                    }
                }
                if (error.isNotBlank()) Text(error, color = WDanger)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (description.isBlank()) {
                    error = "Введите описание замечания"
                    return@Button
                }
                val due = parseWorkDate(dueText)
                if (dueText.isNotBlank() && due == null) {
                    error = "Дата должна быть в формате ДД.ММ.ГГГГ"
                    return@Button
                }
                onSave(
                    initial.copy(
                        element = element.trim(),
                        description = description.trim(),
                        responsible = responsible.trim(),
                        status = status,
                        dueDate = due
                    )
                )
            }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun BulkEditDialog(
    count: Int,
    onDismiss: () -> Unit,
    onApply: (String, DefectStatus?, LocalDate?) -> Unit
) {
    var responsible by remember { mutableStateOf("") }
    var dueText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<DefectStatus?>(null) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Массовое изменение · $count замеч.") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Пустые поля не изменяются.", color = WMuted)
                OutlinedTextField(
                    value = responsible,
                    onValueChange = { responsible = it },
                    label = { Text("Новый подрядчик") }
                )
                OutlinedTextField(
                    value = dueText,
                    onValueChange = { dueText = it },
                    label = { Text("Новый срок, ДД.ММ.ГГГГ") }
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DefectStatus.entries.forEach {
                        FilterChip(
                            selected = status == it,
                            onClick = { status = if (status == it) null else it },
                            label = { Text(shortWorkStatus(it), fontSize = 11.sp) }
                        )
                    }
                }
                if (error.isNotBlank()) Text(error, color = WDanger)
            }
        },
        confirmButton = {
            Button(onClick = {
                val due = parseWorkDate(dueText)
                if (dueText.isNotBlank() && due == null) {
                    error = "Проверьте дату"
                    return@Button
                }
                onApply(responsible.trim(), status, due)
            }) { Text("Применить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun HistoryDialog(
    defect: Defect,
    history: List<HistoryItem>,
    photos: List<DefectPhoto>,
    onDismiss: () -> Unit,
    onRemovePhoto: (DefectPhoto) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("История замечания") },
        text = {
            LazyColumn(
                modifier = Modifier.height(500.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (photos.isNotEmpty()) {
                    item { Text("Фотографии", fontWeight = FontWeight.Bold) }
                    items(photos, key = { it.id }) { photo ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(photo.label, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onRemovePhoto(photo) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Удалить фото")
                            }
                        }
                    }
                    item { HorizontalDivider() }
                }
                item {
                    Text(defect.description, fontWeight = FontWeight.SemiBold)
                }
                items(history, key = { it.id }) { item ->
                    Column {
                        Text(
                            formatHistoryTime(item.timestamp) + " · " + item.action,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(item.details, color = WMuted)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun ArchiveDialog(
    items: List<Defect>,
    onDismiss: () -> Unit,
    onRestore: (Defect) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Архив · ${items.size}") },
        text = {
            if (items.isEmpty()) {
                Text("Архив пуст")
            } else {
                LazyColumn(
                    modifier = Modifier.height(500.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { defect ->
                        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Кв. ${defect.apartment} · ${defect.element}",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(defect.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { onRestore(defect) }) {
                                    Icon(Icons.Outlined.Restore, contentDescription = "Восстановить")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun ContractorDialog(
    contractor: String,
    defects: List<Defect>,
    onDismiss: () -> Unit
) {
    val open = defects.filter { !it.isClosed }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(contractor + " · открыто " + open.size) },
        text = {
            LazyColumn(
                modifier = Modifier.height(500.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(open) { defect ->
                    Column {
                        Text(
                            "Корпус ${defect.building} · секция ${defect.section} · кв. ${defect.apartment}",
                            color = WBrandBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(defect.description)
                        if (defect.isOverdue()) {
                            Text("Просрочка +" + defect.overdueDays() + " дн.", color = WDanger)
                        }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun WorkMetric(title: String, value: String, icon: ImageVector, color: Color) {
    ElevatedCard(
        modifier = Modifier.width(154.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(15.dp)) {
            Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (color == WWhite) WBrandDark else color,
                    modifier = Modifier.padding(8.dp).size(21.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(title, color = WMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WorkSmallMetric(title: String, value: Int) {
    Column {
        Text(value.toString(), fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.labelSmall, color = WMuted)
    }
}

@Composable
private fun WorkPill(text: String, color: Color) {
    val textColor = when (color) {
        WGreen -> Color(0xFF315A13)
        WOrange -> Color(0xFF704F00)
        WBlue -> Color(0xFF005A78)
        WWhite -> WBrandDark
        else -> color
    }
    Surface(
        color = color.copy(alpha = if (color == WWhite) 0.85f else 0.18f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun WorkLegend(label: String, value: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(12.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(9.dp))
        Text(label, modifier = Modifier.weight(1f), color = WMuted)
        Text(value.toString(), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WorkChoiceRow(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { index, label ->
            FilterChip(
                selected = selected == index,
                onClick = { onSelect(index) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun WorkLogo(compact: Boolean) {
    Surface(
        color = WBrandBlue,
        shape = RoundedCornerShape(if (compact) 7.dp else 18.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.su555_logo),
            contentDescription = "ООО ГК СУ-555",
            contentScale = ContentScale.Fit,
            modifier = if (compact) {
                Modifier.width(82.dp).height(31.dp).padding(horizontal = 5.dp, vertical = 3.dp)
            } else {
                Modifier.width(230.dp).height(96.dp).padding(horizontal = 16.dp, vertical = 12.dp)
            }
        )
    }
}

private fun shortWorkStatus(status: DefectStatus): String = when (status) {
    DefectStatus.DONE -> "Выполнено"
    DefectStatus.OPEN -> "Белые"
    DefectStatus.ATTENTION -> "Внимание"
    DefectStatus.REPORTED_NOT_DONE -> "Синие"
}

private fun workStatusColor(status: DefectStatus): Color = when (status) {
    DefectStatus.DONE -> WGreen
    DefectStatus.OPEN -> WWhite
    DefectStatus.ATTENTION -> WOrange
    DefectStatus.REPORTED_NOT_DONE -> WBlue
}

private fun parseWorkDate(raw: String): LocalDate? {
    val value = raw.trim()
    if (value.isBlank()) return null
    val patterns = listOf("dd.MM.yyyy", "d.M.yyyy", "yyyy-MM-dd")
    for (pattern in patterns) {
        runCatching {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern))
        }
    }
    return null
}

private fun formatHistoryTime(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))
