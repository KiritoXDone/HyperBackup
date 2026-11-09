package io.github.kiritoxdone.hyperbackup.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process
import io.github.kiritoxdone.hyperbackup.domain.*

/** Public metadata for the current Android user; the root engine rechecks every execution target. */
class InstalledAppsReader(private val context: Context) {
    val userId: Int get() = Process.myUid() / 100_000
    fun read(): List<AppInstance> {
        val manager = context.packageManager
        return manager.getInstalledPackages(0).mapNotNull { info ->
            val app = info.applicationInfo ?: return@mapNotNull null
            val base = app.sourceDir?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (app.uid / 100_000 != userId) return@mapNotNull null
            AppInstance(AppKey(userId, info.packageName), app.loadLabel(manager).toString(), info.longVersionCode.toString(),
                info.versionName.orEmpty(), app.uid, app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                apkPaths = listOf(base) + app.splitSourceDirs.orEmpty().toList(), dataPath = app.dataDir.orEmpty(), dePath = app.deviceProtectedDataDir.orEmpty())
        }
    }
}
