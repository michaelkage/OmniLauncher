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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.omni.launcher.access.AccessCapabilities
import dev.omni.launcher.access.AccessCapabilityProbe
import dev.omni.launcher.apps.AppCatalogue
import dev.omni.launcher.apps.LaunchableApp
import dev.omni.launcher.preset.ComponentRegistry
import dev.omni.launcher.preset.PresetDocument
import dev.omni.launcher.preset.PresetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appearance = getSharedPreferences("omni_launcher", Context.MODE_PRIVATE)
        setContent {
            var themeMode by remember { mutableStateOf(appearance.getString("theme_mode", "system") ?: "system") }
            var palette by remember { mutableStateOf(appearance.getString("theme_palette", "dynamic") ?: "dynamic") }
            OmniTheme(mode = themeMode, palette = palette) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    OmniHome(
                        themeMode = themeMode,
                        palette = palette,
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
    onThemeModeChange: (String) -> Unit,
    onPaletteChange: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { PresetRepository(context) }
    val preferences = remember { context.getSharedPreferences("omni_launcher", Context.MODE_PRIVATE) }
    var preset by remember { mutableStateOf<PresetDocument?>(null) }
    var selectedPresetId by remember { mutableStateOf(preferences.getString("active_preset", "minimal-wave") ?: "minimal-wave") }
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var presetMenuOpen by remember { mutableStateOf(false) }
    var accessDialogOpen by remember { mutableStateOf(false) }
    var appearanceDialogOpen by remember { mutableStateOf(false) }
    var customizeDialogOpen by remember { mutableStateOf(false) }
    var customizeLabels by remember { mutableStateOf(true) }
    var customizeColumns by remember { mutableStateOf(4f) }
    var customizeMaxApps by remember { mutableStateOf(40f) }
    var accessStatus by remember { mutableStateOf<AccessCapabilities?>(null) }
    var accessChecking by remember { mutableStateOf(false) }
    var exportDocument by remember { mutableStateOf<PresetDocument?>(null) }

    fun refreshApps() {
        runCatching { AppCatalogue(context).load() }
            .onSuccess { apps = it }
            .onFailure { error = "App catalogue: ${it.message ?: "refresh failed"}" }
    }

    fun selectPreset(id: String) {
        runCatching {
            if (id in repository.savedPresetIds()) repository.loadSavedPreset(id)
            else repository.loadBundledPreset(id)
        }.onSuccess {
            preset = it
            selectedPresetId = id
            preferences.edit().putString("active_preset", id).apply()
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
                    selectedPresetId = imported.metadata.id
                    preferences.edit().putString("active_preset", selectedPresetId).apply()
                    error = null
                }.onFailure { error = "Import failed: ${it.message ?: "invalid archive"}" }
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
        selectPreset(selectedPresetId)
        refreshApps()
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
                            text = { Text("Imported: $id") },
                            onClick = { presetMenuOpen = false; selectPreset(id) }
                        )
                    }
                }
                TextButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                    Text("Import")
                }
                TextButton(
                    enabled = preset != null,
                    onClick = {
                        exportDocument = preset
                        exportLauncher.launch("${preset?.metadata?.id ?: "omni"}.omni")
                    }
                ) { Text("Export") }
                TextButton(onClick = { appearanceDialogOpen = true }) { Text("Style") }
                TextButton(onClick = {
                    val current = preset
                    if (current != null) {
                        val all = flattenComponents(current.layout.components)
                        customizeLabels = all.firstOrNull { it.type in setOf("grid", "list", "dock", "app_list_container") }?.showLabels ?: true
                        customizeColumns = (all.firstOrNull { it.type == "grid" }?.columns ?: 4).toFloat()
                        customizeMaxApps = (all.firstOrNull { it.type in setOf("grid", "list", "dock", "app_list_container") }?.maxVisibleItems ?: 40).toFloat()
                        customizeDialogOpen = true
                    }
                }) { Text("Customize") }
                TextButton(onClick = { accessDialogOpen = true }) { Text("Access") }
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
                                selectedPresetId = updated.metadata.id
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
                        "plum" to "Plum"
                    ).forEach { (value, label) ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(
                                selected = palette == value,
                                onClick = { onPaletteChange(value) }
                            )
                            Text(label, modifier = Modifier.clickable { onPaletteChange(value) })
                        }
                    }
                    Text(
                        "Material You wallpaper colours are available on Android 12 and newer. Other palettes work as fallbacks.",
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
