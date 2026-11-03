package dev.omni.launcher.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

class AppCatalogue(context: Context) {
    private val packageManager = context.packageManager

    @Suppress("DEPRECATION")
    fun load(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            packageManager.queryIntentActivities(intent, 0)
        }

        return resolved.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            LaunchableApp(
                label = info.loadLabel(packageManager).toString(),
                packageName = activity.packageName,
                activityName = activity.name,
                icon = info.loadIcon(packageManager)
            )
        }.distinctBy { it.componentName }.sortedBy { it.label.lowercase() }
    }
}
