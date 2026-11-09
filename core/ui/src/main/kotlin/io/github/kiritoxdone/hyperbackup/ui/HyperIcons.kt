package io.github.kiritoxdone.hyperbackup.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Outline actions used by the approved HyperBackup screen reference. */
object HyperIcons {
    private fun icon(name: String, paths: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(addPathNodes(paths), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()
    val Home by lazy { icon("Home", "M3 10L12 3L21 10 M5 9V21H10V14H14V21H19V9") }
    val History by lazy { icon("History", "M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12 M12 7V12L15 14") }
    val Cloud by lazy { icon("Cloud", "M7 19H18A4 4 0 0 0 18 11A6 6 0 0 0 6.5 9A5 5 0 0 0 7 19Z") }
    val Settings by lazy { icon("Settings", "M3 6H13 M17 6H21 M3 12H6 M10 12H21 M3 18H14 M18 18H21 M17 6A2 2 0 1 1 13 6A2 2 0 1 1 17 6 M10 12A2 2 0 1 1 6 12A2 2 0 1 1 10 12 M18 18A2 2 0 1 1 14 18A2 2 0 1 1 18 18") }
    val Backup by lazy { icon("Backup", "M12 16V4 M7 9L12 4L17 9 M4 20H20") }
    val Restore by lazy { icon("Restore", "M12 4V16 M7 11L12 16L17 11 M4 20H20") }
    val Shield by lazy { icon("Shield", "M12 3L20 6V12C20 17 16.5 20 12 21C7.5 20 4 17 4 12V6Z M8.5 12L11 14.5L15.5 9.5") }
    val User by lazy { icon("User", "M16 8A4 4 0 1 1 8 8A4 4 0 1 1 16 8 M4 20A8 8 0 0 1 20 20") }
    val Folder by lazy { icon("Folder", "M3 7V19A2 2 0 0 0 5 21H19A2 2 0 0 0 21 19V9A2 2 0 0 0 19 7H11L9 4H5A2 2 0 0 0 3 6Z") }
    val Tag by lazy { icon("Tag", "M3 3H11L21 13L13 21L3 11Z M7 7H7.01") }
    val Chevron by lazy { icon("Chevron", "M9 6L15 12L9 18") }
    val Search by lazy { icon("Search", "M16 10A6 6 0 1 1 4 10A6 6 0 1 1 16 10 M15 15L21 21") }
}
