package com.vidma.downloader.ui.components.core

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPath
import androidx.compose.ui.graphics.vector.addPathNodes
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

    /** Material `pause` — two rounded playback bars. */
    val Pause: ImageVector by lazy {
        vidmaVector("VidmaPause", "M6 19h4V5H6v14zm8-14v14h4V5h-4z")
    }

    /** Material `public` — globe. */
    val Public: ImageVector by lazy {
        vidmaVector(
            "VidmaPublic",
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93" +
                " 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45" +
                " 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z",
        )
    }

    /** Material `content_paste` — clipboard. */
    val ContentPaste: ImageVector by lazy {
        vidmaVector(
            "VidmaContentPaste",
            "M19 2h-4.18C14.4.84 13.3 0 12 0c-1.3 0-2.4.84-2.82 2H5c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h14c1.1 0" +
                " 2-.9 2-2V4c0-1.1-.9-2-2-2zm-7 0c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zm7 18H5V4h2v3h10V4h2v16z",
        )
    }

    /** Material `tune` — mixer/sliders. */
    val Tune: ImageVector by lazy {
        vidmaVector(
            "VidmaTune",
            "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z",
        )
    }

    private fun vidmaVector(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = addPathNodes(pathData),
                fill = SolidColor(Color.Black),
            )
        }.build()
}
