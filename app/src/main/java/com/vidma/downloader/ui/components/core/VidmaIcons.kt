package com.vidma.downloader.ui.components.core

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Tiny icons that are used often but are not part of Compose's small core
 * icon set. Keeping these paths local avoids pulling the several-thousand-icon
 * extended artifact into the lean APK.
 */
object VidmaIcons {
    val Download: ImageVector by lazy {
        ImageVector.Builder(
            name = "VidmaDownload",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(11f, 3f)
                horizontalLineTo(13f)
                verticalLineTo(13.2f)
                lineTo(16.6f, 9.6f)
                lineTo(18f, 11f)
                lineTo(12f, 17f)
                lineTo(6f, 11f)
                lineTo(7.4f, 9.6f)
                lineTo(11f, 13.2f)
                close()
                moveTo(5f, 19f)
                horizontalLineTo(19f)
                verticalLineTo(21f)
                horizontalLineTo(5f)
                close()
            }
        }.build()
    }
}
