package dev.omni.launcher.preset

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.omni.launcher.apps.LaunchableApp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private typealias ComponentRenderer = @Composable (LayoutComponent, List<LaunchableApp>) -> Unit

object ComponentRegistry {
    private val renderers: Map<String, ComponentRenderer> = mapOf(
        "clock_widget" to { component, _ -> ClockRenderer(component) },
        "app_list_container" to { component, apps -> ListRenderer(component, apps) },
        "list" to { component, apps -> ListRenderer(component, apps) },
        "grid" to { component, apps -> GridRenderer(component, apps) },
        "dock" to { component, apps -> DockRenderer(component, apps) },
        "stack" to { component, apps -> StackRenderer(component, apps) }
    )

    val registeredTypes: Set<String> get() = renderers.keys

    @Composable
    fun Render(component: LayoutComponent, apps: List<LaunchableApp>) {
        val renderer = renderers[component.type]
        if (renderer == null) {
            Text("Unsupported component: ${component.type}", color = MaterialTheme.colorScheme.error)
        } else {
            renderer(component, apps)
        }
    }
}

@Composable
private fun ClockRenderer(component: LayoutComponent) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }
    val pattern = if (component.style == "minimal_text_24h") "HH:mm" else "h:mm a"
    Text(
        text = now.format(DateTimeFormatter.ofPattern(pattern)),
        fontSize = 48.sp,
        fontWeight = FontWeight.Light,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun ListRenderer(component: LayoutComponent, apps: List<LaunchableApp>) {
    var query by remember { mutableStateOf("") }
    val visible = remember(apps, component.filter, component.maxVisibleItems, query) {
        filteredApps(component, apps, query)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppSearchField(query = query, onQueryChange = { query = it })
        if (visible.isEmpty()) {
            Text("No apps found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(component.spacing.dp)
            ) {
                items(visible, key = { it.packageName + "/" + it.activityName }) { app ->
                    AppRow(app, component.showLabels)
                }
            }
        }
    }
}

@Composable
private fun GridRenderer(component: LayoutComponent, apps: List<LaunchableApp>) {
    var query by remember { mutableStateOf("") }
    val visible = remember(apps, component.filter, component.maxVisibleItems, query) {
        filteredApps(component, apps, query)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppSearchField(query = query, onQueryChange = { query = it })
        if (visible.isEmpty()) {
            Text("No apps found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(component.columns.coerceIn(1, 8)),
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(component.spacing.dp),
                horizontalArrangement = Arrangement.spacedBy(component.spacing.dp)
            ) {
                gridItems(visible, key = { it.packageName + "/" + it.activityName }) { app ->
                    AppTile(app, component.showLabels)
                }
            }
        }
    }
}

@Composable
private fun DockRenderer(component: LayoutComponent, apps: List<LaunchableApp>) {
    val visible = remember(apps, component.filter, component.maxVisibleItems) {
        filteredApps(component, apps).take(component.maxVisibleItems ?: 5)
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(component.spacing.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        visible.forEach { app -> AppTile(app, component.showLabels) }
    }
}

@Composable
private fun StackRenderer(component: LayoutComponent, apps: List<LaunchableApp>) {
    Column(verticalArrangement = Arrangement.spacedBy(component.spacing.dp)) {
        if (component.children.isEmpty()) {
            Text("Empty stack", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            component.children.forEach { child ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        ComponentRegistry.Render(child, apps)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: LaunchableApp, showLabel: Boolean) {
    val context = LocalContext.current
    val iconBitmap = remember(app.icon) { app.icon.toBitmap(96, 96).asImageBitmap() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.componentName)) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.Image(
            bitmap = iconBitmap,
            contentDescription = app.label,
            modifier = Modifier.size(40.dp)
        )
        if (showLabel) {
            Text(
                app.label,
                modifier = Modifier.padding(start = 14.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun AppTile(app: LaunchableApp, showLabel: Boolean) {
    val context = LocalContext.current
    val iconBitmap = remember(app.icon) { app.icon.toBitmap(96, 96).asImageBitmap() }
    Column(
        modifier = Modifier
            .clickable { context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.componentName)) }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        androidx.compose.foundation.Image(
            bitmap = iconBitmap,
            contentDescription = app.label,
            modifier = Modifier.size(48.dp)
        )
        if (showLabel) {
            Text(app.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun AppSearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Search apps") }
    )
}

internal fun matchesAppSearch(label: String, packageName: String, query: String): Boolean {
    val normalized = query.trim()
    return normalized.isEmpty() ||
        label.contains(normalized, ignoreCase = true) ||
        packageName.contains(normalized, ignoreCase = true)
}

private fun filteredApps(
    component: LayoutComponent,
    apps: List<LaunchableApp>,
    query: String = ""
): List<LaunchableApp> {
    val excluded = component.filter?.excludePackages.orEmpty().toSet()
    val filtered = apps.filterNot { it.packageName in excluded }
        .filter { matchesAppSearch(it.label, it.packageName, query) }
    return component.maxVisibleItems?.let { filtered.take(it.coerceIn(1, 500)) } ?: filtered
}
