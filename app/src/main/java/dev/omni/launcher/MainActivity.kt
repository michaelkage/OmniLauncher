package dev.omni.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.core.content.ContextCompat
import dev.omni.launcher.access.AccessCapabilities
import dev.omni.launcher.access.AccessCapabilityProbe
import dev.omni.launcher.apps.AppCatalogue
import dev.omni.launcher.apps.LaunchableApp
import dev.omni.launcher.preset.ComponentRegistry
import dev.omni.launcher.preset.PresetDocument
import dev.omni.launcher.preset.PresetRepository
import dev.omni.launcher.preset.appendComponent
import dev.omni.launcher.preset.componentsAtPath
import dev.omni.launcher.preset.updateComponentsAtPath
import dev.omni.launcher.preset.moveComponent
import dev.omni.launcher.preset.removeComponent
import dev.omni.launcher.theme.ThemePack
import dev.omni.launcher.theme.ThemePackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appearance = getSharedPreferences("omni_launcher", Context.MODE_PRIVATE)
        setContent {
            var themeMode by remember { mutableStateOf(appearance.getString("theme_mode", "system") ?: "system") }
            var palette by remember { mutableStateOf(appearance.getString("theme_palette", "dynamic") ?: "dynamic") }
            var customThemePack by remember { mutableStateOf<ThemePack?>(null) }
            LaunchedEffect(Unit) {
                val savedId = appearance.getString("custom_theme_pack_id", null)
                if (savedId != null) {
                    customThemePack = runCatching { ThemePackRepository(this@MainActivity).load(savedId) }.getOrNull()
                    if (customThemePack == null && palette == "custom") palette = "dynamic"
                }
            }
            OmniTheme(mode = themeMode, palette = palette, customPack = customThemePack) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    OmniHome(
                        themeMode = themeMode,
                        palette = palette,
                        customThemePack = customThemePack,
                        onCustomThemePackChange = { pack ->
                            customThemePack = pack
                            appearance.edit().putString("custom_theme_pack_id", pack?.metadata?.id).apply()
                            if (pack != null) {
                                palette = "custom"
                                appearance.edit().putString("theme_palette", "custom").apply()
                            } else if (palette == "custom") {
                                palette = "dynamic"
                                appearance.edit().putString("theme_palette", "dynamic").apply()
                            }
                        },
                        onThemeModeChange = {
                            themeMode = it
                            appearance.edit().putString("theme_mode", it).apply()
                        },
                        onPaletteChange = {
                            palette = it
                            appearance.edit().putString("theme_palette", it).apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OmniHome(
    themeMode: String,
    palette: String,
    customThemePack: ThemePack?,
    onCustomThemePackChange: (ThemePack?) -> Unit,
    onThemeModeChange: (String) -> Unit,
    onPaletteChange: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { PresetRepository(context) }
    val themePackRepository = remember { ThemePackRepository(context) }
    val preferences = remember { context.getSharedPreferences("omni_launcher", Context.MODE_PRIVATE) }
    var preset by remember { mutableStateOf<PresetDocument?>(null) }
    var selectedPresetId by remember { mutableStateOf(preferences.getString("active_preset", "minimal-wave") ?: "minimal-wave") }
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var presetMenuOpen by remember { mutableStateOf(false) }
    var moreMenuOpen by remember { mutableStateOf(false) }
    var accessDialogOpen by remember { mutableStateOf(false) }
    var appearanceDialogOpen by remember { mutableStateOf(false) }
    var themeManagerOpen by remember { mutableStateOf(false) }
    var installedThemePacks by remember { mutableStateOf<List<ThemePack>>(emptyList()) }
    var themePackBusy by remember { mutableStateOf(false) }
    var customizeDialogOpen by remember { mutableStateOf(false) }
    var layoutEditorOpen by remember { mutableStateOf(false) }
    var editorComponents by remember { mutableStateOf<List<dev.omni.launcher.preset.LayoutComponent>>(emptyList()) }
    var editorStackPath by remember { mutableStateOf<List<Int>>(emptyList()) }
    var draggingComponentIndex by remember { mutableStateOf<Int?>(null) }
    var dragTargetIndex by remember { mutableStateOf<Int?>(null) }
    var addComponentMenuOpen by remember { mutableStateOf(false) }
    var customizeLabels by remember { mutableStateOf(true) }
    var customizeColumns by remember { mutableStateOf(4f) }
    var customizeMaxApps by remember { mutableStateOf(40f) }
    var accessStatus by remember { mutableStateOf<AccessCapabilities?>(null) }
    var accessChecking by remember { mutableStateOf(false) }
    var exportDocument by remember { mutableStateOf<PresetDocument?>(null) }
    var themePackToExport by remember { mutableStateOf<ThemePack?>(null) }

    fun refreshApps() {
        runCatching { AppCatalogue(context).load() }
            .onSuccess { apps = it }
            .onFailure { error = "App catalogue: ${it.message ?: "refresh failed"}" }
    }

    fun selectPreset(selectionId: String) {
        val isSavedPreset = selectionId.startsWith("saved:")
        val presetId = if (isSavedPreset) selectionId.removePrefix("saved:") else selectionId
        runCatching {
            if (isSavedPreset) repository.loadSavedPreset(presetId)
            else repository.loadBundledPreset(presetId)
        }.onSuccess {
            preset = it
            selectedPresetId = if (isSavedPreset) "saved:$presetId" else presetId
            preferences.edit().putString("active_preset", selectedPresetId).apply()
            error = null
        }.onFailure { error = it.message ?: "Preset could not be loaded" }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("Could not open the selected archive.")
                        repository.importOmni(input)
                    }
                }
                result.onSuccess { imported ->
                    preset = imported
                    selectedPresetId = "saved:${imported.metadata.id}"
                    preferences.edit().putString("active_preset", selectedPresetId).apply()
                    error = null
                }.onFailure { error = "Import failed: ${it.message ?: "invalid archive"}" }
            }
        }
    }

    val themePackImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                themePackBusy = true
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("Could not open the selected theme pack.")
                        input.use { themePackRepository.import(it) }
                    }
                }
                result.onSuccess { pack ->
                    installedThemePacks = withContext(Dispatchers.IO) { themePackRepository.list().map { themePackRepository.load(it.id) } }
                    onCustomThemePackChange(pack)
                    error = "Imported theme: ${pack.metadata.name}."
                }.onFailure { error = "Theme import failed: ${it.message ?: "invalid theme pack"}" }
                themePackBusy = false            }
        }
    }

    val themePackExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val pack = themePackToExport
        themePackToExport = null
        if (uri != null && pack != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri)
                            ?: throw IllegalStateException("Could not create the theme file.")
                        output.use { themePackRepository.export(pack, it) }
                    }
                }
                result.onSuccess { error = "Exported theme: ${pack.metadata.name}." }
                    .onFailure { error = "Theme export failed: ${it.message ?: "could not write file"}" }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        val document = exportDocument
        exportDocument = null
        if (uri != null && document != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri)
                            ?: throw IllegalStateException("Could not create the destination file.")
                        repository.exportOmni(document, output)
                    }
                }
                result.onFailure { error = "Export failed: ${it.message ?: "could not write archive"}" }
                result.onSuccess { error = "Exported ${document.metadata.name}." }
            }
        }
    }

    LaunchedEffect(Unit) {
        val savedSelection = selectedPresetId.removePrefix("saved:")
        val migrationDone = preferences.getBoolean("preset_selection_namespace_v1_migrated", false)
        val isLegacySavedSelection = !migrationDone &&
            !selectedPresetId.startsWith("saved:") &&
            selectedPresetId in repository.savedPresetIds()
        preferences.edit().putBoolean("preset_selection_namespace_v1_migrated", true).apply()
        selectPreset(
            if (selectedPresetId.startsWith("saved:") || isLegacySavedSelection) "saved:$savedSelection"
            else selectedPresetId
        )
        refreshApps()
        installedThemePacks = withContext(Dispatchers.IO) { runCatching { themePackRepository.list().map { themePackRepository.load(it.id) } }.getOrDefault(emptyList()) }
    }

    DisposableEffect(context) {
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                refreshApps()
            }
        }
        ContextCompat.registerReceiver(context, receiver, packageFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("OMNI", style = MaterialTheme.typography.titleLarge)
                Text(preset?.metadata?.name ?: "Loading preset…", style = MaterialTheme.typography.labelMedium)
            }
            Row {
                TextButton(onClick = { presetMenuOpen = true }) { Text("Presets") }
                DropdownMenu(expanded = presetMenuOpen, onDismissRequest = { presetMenuOpen = false }) {
                    repository.bundledPresets.forEach { choice ->
                        DropdownMenuItem(
                            text = { Text(choice.title) },
                            onClick = { presetMenuOpen = false; selectPreset(choice.id) }
                        )
                    }
                    repository.savedPresetIds().forEach { id ->
                        DropdownMenuItem(
                            text = { Text("Saved: $id") },
                            onClick = { presetMenuOpen = false; selectPreset("saved:$id") }
                        )
                    }
                }
                TextButton(onClick = { moreMenuOpen = true }) { Text("More") }
                DropdownMenu(expanded = moreMenuOpen, onDismissRequest = { moreMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Import preset") },
                        onClick = { moreMenuOpen = false; importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }
                    )
                    DropdownMenuItem(
                        text = { Text("Export preset") },
                        enabled = preset != null,
                        onClick = {
                            moreMenuOpen = false
                            exportDocument = preset
                            exportLauncher.launch("${preset?.metadata?.id ?: "omni"}.omni")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Appearance") },
                        onClick = { moreMenuOpen = false; appearanceDialogOpen = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Theme manager") },
                        onClick = {
                            moreMenuOpen = false
                            scope.launch {
                                installedThemePacks = withContext(Dispatchers.IO) { runCatching { themePackRepository.list().map { themePackRepository.load(it.id) } }.getOrDefault(emptyList()) }
                                themeManagerOpen = true
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit layout") },
                        enabled = preset != null,
                        onClick = {
                            moreMenuOpen = false
                            val current = preset
                            if (current != null) {
                                editorComponents = current.layout.components
                                editorStackPath = emptyList()
                                layoutEditorOpen = true
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Customize") },
                        enabled = preset != null,
                        onClick = {
                            moreMenuOpen = false
                            val current = preset
                            if (current != null) {
                                val all = flattenComponents(current.layout.components)
                                customizeLabels = all.firstOrNull { it.type in setOf("grid", "list", "dock", "app_list_container") }?.showLabels ?: true
                                customizeColumns = (all.firstOrNull { it.type == "grid" }?.columns ?: 4).toFloat()
                                customizeMaxApps = (all.firstOrNull { it.type in setOf("grid", "list", "dock", "app_list_container") }?.maxVisibleItems ?: 40).toFloat()
                                customizeDialogOpen = true
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Access capabilities") },
                        onClick = { moreMenuOpen = false; accessDialogOpen = true }
                    )
                }
            }
        }

        if (error != null) {
            Text(error!!, color = if (error!!.startsWith("Exported")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }

        val document = preset
        if (document == null) {
            Text("No valid preset is active. Choose another preset or inspect the validation error.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(document.layout.components) { _, component ->
                    ComponentRegistry.Render(component, apps)
                }
            }
        }
    }



    if (layoutEditorOpen) {
        AlertDialog(
            onDismissRequest = { layoutEditorOpen = false },
            title = { Text("Visual layout editor") },
            text = {
                val currentComponents = runCatching {
                    componentsAtPath(editorComponents, editorStackPath)
                }.getOrDefault(emptyList())
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                if (editorStackPath.isEmpty()) "Root layout"
                                else "Inside stack · depth ${editorStackPath.size}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "Edit components at this level. Open a stack to edit its children.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (editorStackPath.isNotEmpty()) {
                            TextButton(onClick = { editorStackPath = editorStackPath.dropLast(1) }) {
                                Text("Back")
                            }
                        }
                    }
                    Row {
                        TextButton(onClick = { addComponentMenuOpen = true }) { Text("Add component") }
                        DropdownMenu(
                            expanded = addComponentMenuOpen,
                            onDismissRequest = { addComponentMenuOpen = false }
                        ) {
                            listOf(
                                "clock_widget" to "Clock",
                                "grid" to "App grid",
                                "list" to "App list",
                                "dock" to "Dock",
                                "stack" to "Stack"
                            ).forEach { (type, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        val component = when (type) {
                                            "clock_widget" -> dev.omni.launcher.preset.LayoutComponent(type = type, style = "minimal_text_24h")
                                            "grid" -> dev.omni.launcher.preset.LayoutComponent(type = type, columns = 4, maxVisibleItems = 40)
                                            "list" -> dev.omni.launcher.preset.LayoutComponent(type = type, maxVisibleItems = 40)
                                            "dock" -> dev.omni.launcher.preset.LayoutComponent(type = type, maxVisibleItems = 5)
                                            else -> dev.omni.launcher.preset.LayoutComponent(type = type)
                                        }
                                        editorComponents = updateComponentsAtPath(editorComponents, editorStackPath) {
                                            appendComponent(it, component)
                                        }
                                        addComponentMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                    if (currentComponents.isEmpty()) {
                        Text("Nothing here yet. Add a component to this level.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 390.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(currentComponents) { index, component ->
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                        .background(
                                            color = when {
                                                draggingComponentIndex == index -> MaterialTheme.colorScheme.secondaryContainer
                                                dragTargetIndex == index && draggingComponentIndex != index -> MaterialTheme.colorScheme.tertiaryContainer
                                                else -> Color.Transparent
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${index + 1}. ${component.type.replace('_', ' ')}",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        if (component.type == "stack") {
                                            TextButton(onClick = {
                                                editorStackPath = editorStackPath + index
                                            }) { Text("Open stack") }
                                        }
                                    }
                                    if (component.type in setOf("grid", "list", "app_list_container", "dock")) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                        ) {
                                            Text("Show app labels", style = MaterialTheme.typography.bodySmall)
                                            Switch(
                                                checked = component.showLabels,
                                                onCheckedChange = { checked ->
                                                    editorComponents = updateComponentsAtPath(editorComponents, editorStackPath) { items ->
                                                        items.toMutableList().also { it[index] = component.copy(showLabels = checked) }
                                                    }
                                                }
                                            )
                                        }
                                        Text("Visible apps: ${component.maxVisibleItems ?: 40}", style = MaterialTheme.typography.bodySmall)
                                        Slider(
                                            value = (component.maxVisibleItems ?: 40).toFloat(),
                                            onValueChange = { value ->
                                                editorComponents = updateComponentsAtPath(editorComponents, editorStackPath) { items ->
                                                    items.toMutableList().also { it[index] = component.copy(maxVisibleItems = value.toInt()) }
                                                }
                                            },
                                            valueRange = 8f..80f,
                                            steps = 17
                                        )
                                    }
                                    if (component.type == "grid") {
                                        Text("Grid columns: ${component.columns}", style = MaterialTheme.typography.bodySmall)
                                        Slider(
                                            value = component.columns.toFloat(),
                                            onValueChange = { value ->
                                                editorComponents = updateComponentsAtPath(editorComponents, editorStackPath) { items ->
                                                    items.toMutableList().also { it[index] = component.copy(columns = value.toInt()) }
                                                }
                                            },
                                            valueRange = 1f..8f,
                                            steps = 6
                                        )
                                    }
                                    Text("Spacing: ${component.spacing} dp", style = MaterialTheme.typography.bodySmall)
                                    Slider(
                                        value = component.spacing.toFloat(),
                                        onValueChange = { value ->
                                            editorComponents = updateComponentsAtPath(editorComponents, editorStackPath) { items ->
                                                items.toMutableList().also { it[index] = component.copy(spacing = value.toInt()) }
                                            }
                                        },
                                        valueRange = 0f..64f,
                                        steps = 15
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            "Hold ↕ Drag",
                                            modifier = Modifier.pointerInput(index) {
                                                var totalDrag = 0f
                                                val threshold = 42.dp.toPx()
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        totalDrag = 0f
                                                        draggingComponentIndex = index
                                                        dragTargetIndex = index
                                                    },
                                                    onDragEnd = {
                                                        val destination = (dragTargetIndex ?: index)
                                                            .coerceIn(0, currentComponents.lastIndex)
                                                        val offset = destination - index
                                                        if (offset != 0) {
                                                            editorComponents = updateComponentsAtPath(
                                                                editorComponents,
                                                                editorStackPath
                                                            ) { items -> moveComponent(items, index, offset) }
                                                        }
                                                        draggingComponentIndex = null
                                                        dragTargetIndex = null
                                                    },
                                                    onDragCancel = {
                                                        totalDrag = 0f
                                                        draggingComponentIndex = null
                                                        dragTargetIndex = null
                                                    }
                                                ) { change, dragAmount ->
                                                    change.consume()
                                                    totalDrag += dragAmount.y
                                                    val requestedOffset = (totalDrag / threshold).roundToInt()
                                                    dragTargetIndex = (index + requestedOffset)
                                                        .coerceIn(0, currentComponents.lastIndex)
                                                }
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        TextButton(
                                            enabled = index > 0,
                                            onClick = {
                                                editorComponents = updateComponentsAtPath(editorComponents, editorStackPath) {
                                                    moveComponent(it, index, -1)
                                                }
                                            }
                                        ) { Text("Move up") }
                                        TextButton(
                                            enabled = index < currentComponents.lastIndex,
                                            onClick = {
                                                editorComponents = updateComponentsAtPath(editorComponents, editorStackPath) {
                                                    moveComponent(it, index, 1)
                                                }
                                            }
                                        ) { Text("Move down") }
                                        TextButton(onClick = {
                                            editorComponents = updateComponentsAtPath(editorComponents, editorStackPath) {
                                                removeComponent(it, index)
                                            }
                                            if (editorStackPath.isNotEmpty()) {
                                                val parentPath = editorStackPath.dropLast(1)
                                                val parentItems = componentsAtPath(editorComponents, parentPath)
                                                if (editorStackPath.last() !in parentItems.indices) {
                                                    editorStackPath = parentPath
                                                }
                                            }
                                        }) { Text("Remove") }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val current = preset
                    if (current != null) {
                        val updated = current.copy(layout = current.layout.copy(components = editorComponents))
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    val issues = repository.validate(updated)
                                    require(issues.isEmpty()) {
                                        issues.joinToString("\n") { "${it.path}: ${it.message}" }
                                    }
                                    repository.saveImportedPreset(updated)
                                }
                            }
                            result.onSuccess {
                                preset = updated
                                selectedPresetId = "saved:${updated.metadata.id}"
                                preferences.edit().putString("active_preset", selectedPresetId).apply()
                                error = "Saved visual layout."
                                layoutEditorOpen = false
                            }.onFailure { error = "Could not save layout: ${it.message}" }
                        }
                    }
                }) { Text("Save layout") }
            },
            dismissButton = {
                TextButton(onClick = { layoutEditorOpen = false }) { Text("Cancel") }
            }
        )
    }

    if (customizeDialogOpen) {
        AlertDialog(
            onDismissRequest = { customizeDialogOpen = false },
            title = { Text("Customize launcher") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("App labels", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(if (customizeLabels) "Show app names" else "Hide app names")
                        Switch(checked = customizeLabels, onCheckedChange = { customizeLabels = it })
                    }
                    Text("Grid columns: ${customizeColumns.toInt()}", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = customizeColumns,
                        onValueChange = { customizeColumns = it },
                        valueRange = 3f..8f,
                        steps = 4
                    )
                    Text("Maximum apps per component: ${customizeMaxApps.toInt()}", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = customizeMaxApps,
                        onValueChange = { customizeMaxApps = it },
                        valueRange = 8f..80f,
                        steps = 17
                    )
                    Text(
                        "These settings update the active preset and are saved on this device. You can still export it as an .omni file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val current = preset
                    if (current != null) {
                        val updated = current.copy(
                            layout = current.layout.copy(
                                components = customizeComponents(
                                    current.layout.components,
                                    customizeLabels,
                                    customizeColumns.toInt(),
                                    customizeMaxApps.toInt()
                                )
                            )
                        )
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) { repository.saveImportedPreset(updated) }
                            }
                            result.onSuccess {
                                preset = updated
                                selectedPresetId = "saved:${updated.metadata.id}"
                                preferences.edit().putString("active_preset", selectedPresetId).apply()
                                error = "Saved launcher customizations."
                                customizeDialogOpen = false
                            }.onFailure { error = "Could not save customizations: ${it.message}" }
                        }
                    }
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { customizeDialogOpen = false }) { Text("Cancel") }
            }
        )
    }

    if (themeManagerOpen) {
        AlertDialog(
            onDismissRequest = { if (!themePackBusy) themeManagerOpen = false },
            title = { Text("Theme manager") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Import a JSON theme pack, then tap an installed theme to apply it.")
                    TextButton(
                        enabled = !themePackBusy,
                        onClick = { themePackImportLauncher.launch(arrayOf("application/json", "text/plain")) }
                    ) { Text(if (themePackBusy) "Importing…" else "Import theme pack") }
                    if (installedThemePacks.isEmpty()) {                        Text("No imported themes yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(installedThemePacks, key = { _, item -> item.metadata.id }) { _, pack ->
                                val metadata = pack.metadata
                                val isActive = customThemePack?.metadata?.id == metadata.id && palette == "custom"
                                Column(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        scope.launch {
                                            onCustomThemePackChange(pack)
                                            error = "Applied theme: ${pack.metadata.name}."
                                        }
                                    }.padding(vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(metadata.name, style = MaterialTheme.typography.titleSmall)
                                    Text("by ${metadata.author} · v${metadata.version}", style = MaterialTheme.typography.bodySmall)
                                    Text("Light", style = MaterialTheme.typography.labelSmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        listOf(pack.palettes.light.primary, pack.palettes.light.secondary, pack.palettes.light.tertiary, pack.palettes.light.background, pack.palettes.light.surface).forEach { value ->
                                            androidx.compose.foundation.layout.Box(
                                                Modifier.size(22.dp).background(Color(android.graphics.Color.parseColor(value)))
                                            )
                                        }
                                    }
                                    Text("Dark", style = MaterialTheme.typography.labelSmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        listOf(pack.palettes.dark.primary, pack.palettes.dark.secondary, pack.palettes.dark.tertiary, pack.palettes.dark.background, pack.palettes.dark.surface).forEach { value ->
                                            androidx.compose.foundation.layout.Box(
                                                Modifier.size(22.dp).background(Color(android.graphics.Color.parseColor(value)))
                                            )
                                        }
                                    }
                                    Text(if (isActive) "Currently applied · tap to apply" else "Tap to apply", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Row {
                                        TextButton(onClick = {
                                            themePackToExport = pack
                                            themePackExportLauncher.launch("${metadata.id}.json")
                                        }) { Text("Export") }
                                        TextButton(onClick = {
                                        scope.launch {
                                            val removed = withContext(Dispatchers.IO) { themePackRepository.remove(metadata.id) }
                                            installedThemePacks = withContext(Dispatchers.IO) { themePackRepository.list().map { themePackRepository.load(it.id) } }
                                            if (removed && customThemePack?.metadata?.id == metadata.id) onCustomThemePackChange(null)
                                            error = if (removed) "Removed theme ${metadata.name}." else "Theme was not found."
                                        }
                                    }) { Text("Remove") }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { themeManagerOpen = false }) { Text("Done") } }
        )
    }

    if (appearanceDialogOpen) {
        AlertDialog(
            onDismissRequest = { appearanceDialogOpen = false },
            title = { Text("Appearance") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Theme", style = MaterialTheme.typography.titleSmall)
                    listOf("system" to "Follow device", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(
                                selected = themeMode == value,
                                onClick = { onThemeModeChange(value) }
                            )
                            Text(label, modifier = Modifier.clickable { onThemeModeChange(value) })
                        }
                    }
                    Text("Colour style", style = MaterialTheme.typography.titleSmall)
                    listOf(
                        "dynamic" to "Material You (wallpaper)",
                        "ocean" to "Ocean",
                        "forest" to "Forest",
                        "plum" to "Plum",
                        "custom" to "Imported theme"
                    ).filter { it.first != "custom" || customThemePack != null }.forEach { (value, label) ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(
                                selected = palette == value,
                                onClick = { onPaletteChange(value) }
                            )
                            Text(label, modifier = Modifier.clickable { onPaletteChange(value) })
                        }
                    }
                    Text(
                        if (palette == "custom" && customThemePack != null) "Using imported theme: ${customThemePack.metadata.name}." else "Material You wallpaper colours are available on Android 12 and newer. Other palettes work as fallbacks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { appearanceDialogOpen = false }) { Text("Done") }
            }
        )
    }

    if (accessDialogOpen) {
        AlertDialog(
            onDismissRequest = { accessDialogOpen = false },
            title = { Text("Optional system access") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Standard launcher features do not require elevated access.")
                    Text(
                        when {
                            accessChecking -> "Checking access…"
                            accessStatus == null -> "Shizuku and root are optional and independent."
                            else -> buildString {
                                append("Shizuku app: ")
                                append(if (accessStatus?.shizukuInstalled == true) "detected" else "not detected")
                                append("\nRoot grant: ")
                                append(if (accessStatus?.rootGranted == true) "available" else "not checked or not granted")
                            }
                        }
                    )
                    TextButton(
                        enabled = !accessChecking,
                        onClick = {
                            accessChecking = true
                            scope.launch {
                                accessStatus = AccessCapabilityProbe.inspect(context)
                                accessChecking = false
                            }
                        }
                    ) { Text("Detect Shizuku") }
                    TextButton(
                        enabled = !accessChecking,
                        onClick = {
                            accessChecking = true
                            scope.launch {
                                accessStatus = AccessCapabilityProbe.inspect(context, requestRootPrompt = true)
                                accessChecking = false
                            }
                        }
                    ) { Text("Request root check") }
                }
            },
            confirmButton = { TextButton(onClick = { accessDialogOpen = false }) { Text("Close") } }
        )
    }
}


private fun flattenComponents(components: List<dev.omni.launcher.preset.LayoutComponent>): List<dev.omni.launcher.preset.LayoutComponent> =
    components.flatMap { listOf(it) + flattenComponents(it.children) }

private fun customizeComponents(
    components: List<dev.omni.launcher.preset.LayoutComponent>,
    showLabels: Boolean,
    columns: Int,
    maxApps: Int
): List<dev.omni.launcher.preset.LayoutComponent> = components.map { component ->
    val isAppComponent = component.type in setOf("grid", "list", "dock", "app_list_container")
    component.copy(
        showLabels = if (isAppComponent) showLabels else component.showLabels,
        columns = if (component.type == "grid") columns.coerceIn(3, 8) else component.columns,
        maxVisibleItems = if (isAppComponent) maxApps.coerceIn(8, 80) else component.maxVisibleItems,
        children = customizeComponents(component.children, showLabels, columns, maxApps)
    )
}