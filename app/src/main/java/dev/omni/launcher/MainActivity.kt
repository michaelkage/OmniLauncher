package dev.omni.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    OmniHome()
                }
            }
        }
    }
}

@Composable
private fun OmniHome() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { PresetRepository(context) }
    var preset by remember { mutableStateOf<PresetDocument?>(null) }
    var selectedPresetId by remember { mutableStateOf("minimal-wave") }
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var presetMenuOpen by remember { mutableStateOf(false) }
    var accessDialogOpen by remember { mutableStateOf(false) }
    var accessStatus by remember { mutableStateOf<AccessCapabilities?>(null) }
    var accessChecking by remember { mutableStateOf(false) }

    fun refreshApps() {
        runCatching { AppCatalogue(context).load() }
            .onSuccess { apps = it }
            .onFailure { error = "App catalogue: ${it.message ?: "refresh failed"}" }
    }

    fun selectPreset(id: String) {
        runCatching { repository.loadBundledPreset(id) }
            .onSuccess {
                preset = it
                selectedPresetId = id
                error = null
            }
            .onFailure { error = it.message ?: "Preset could not be loaded" }
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
        ContextCompat.registerReceiver(
            context,
            receiver,
            packageFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
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
                            onClick = {
                                presetMenuOpen = false
                                selectPreset(choice.id)
                            }
                        )
                    }
                }
                TextButton(onClick = { accessDialogOpen = true }) { Text("Access") }
            }
        }

        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
            confirmButton = {
                TextButton(onClick = { accessDialogOpen = false }) { Text("Close") }
            }
        )
    }
}
