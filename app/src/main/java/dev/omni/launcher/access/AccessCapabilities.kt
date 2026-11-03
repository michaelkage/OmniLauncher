package dev.omni.launcher.access

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class AccessCapabilities(
    val shizukuInstalled: Boolean,
    val rootGranted: Boolean
)

object AccessCapabilityProbe {
    suspend fun inspect(context: Context, requestRootPrompt: Boolean = false): AccessCapabilities {
        val shizukuInstalled = try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    "moe.shizuku.privileged.api",
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

        val rootGranted = if (requestRootPrompt) {
            withContext(Dispatchers.IO) {
                try {
                    val process = ProcessBuilder("su", "-c", "id")
                        .redirectErrorStream(true)
                        .start()
                    val completed = process.waitFor(4, TimeUnit.SECONDS)
                    if (!completed) {
                        process.destroyForcibly()
                        false
                    } else {
                        process.exitValue() == 0
                    }
                } catch (_: Exception) {
                    false
                }
            }
        } else {
            false
        }

        return AccessCapabilities(shizukuInstalled, rootGranted)
    }
}
