package dev.omni.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import dev.omni.launcher.apps.AppCatalogue
import dev.omni.launcher.apps.LaunchableApp
import dev.omni.launcher.preset.LayoutComponent
import dev.omni.launcher.preset.PresetDocument
import dev.omni.launcher.preset.PresetRepository
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OmniHome()
                }
            }
        }
    }
}

@Composable
private fun OmniHome() {
    val context = LocalContext.current
    var preset by remember { mutableStateOf<PresetDocument?>(null) }
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { PresetRepository(context).loadBundledPreset() }
            .onSuccess { preset = it }
            .onFailure { error = it.message ?: "Could not load preset" }

        runCatching { AppCatalogue(context).load() }
            .onSuccess { apps = it }
            .onFailure { if (error == null) error = it.message ?: "Could not load apps" }
    }

    val document = preset
    if (document == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(error ?: "Loading Omni…")
        }
        return
    }

    val root = document.layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = root.padding.left.dp,
                top = root.padding.top.dp,
                end = root.padding.right.dp,
                bottom = root.padding.bottom.dp
            )
    ) {
        root.components.forEach { component ->
            when (component.type) {
                "clock_widget" -> ClockComponent(component)
                "app_list_container" -> AppListComponent(component, apps)
            }
        }
    }
}

@Composable
private fun ClockComponent(component: LayoutComponent) {
    val formatter = if (component.style == "minimal_text_24h") {
        DateTimeFormatter.ofPattern("HH:mm")
    } else {
        DateTimeFormatter.ofPattern("h:mm a")
    }
    Text(
        text = LocalTime.now().format(formatter),
        fontSize = 48.sp,
        fontWeight = FontWeight.Light,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(modifier = Modifier.height(28.dp))
}

@Composable
private fun AppListComponent(component: LayoutComponent, apps: List<LaunchableApp>) {
    val context = LocalContext.current
    val visibleApps = component.maxVisibleItems
        ?.let { apps.take(it.coerceAtLeast(0)) }
        ?: apps

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(visibleApps, key = { it.packageName + "/" + it.activityName }) { app ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(Intent().setComponent(app.componentName))
                    }
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = app.label,
                    modifier = Modifier.padding(start = 16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
