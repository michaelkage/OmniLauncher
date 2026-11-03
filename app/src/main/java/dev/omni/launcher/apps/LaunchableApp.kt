package dev.omni.launcher.apps

import android.content.ComponentName
import android.graphics.drawable.Drawable

data class LaunchableApp(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable
) {
    val componentName: ComponentName
        get() = ComponentName(packageName, activityName)
}
