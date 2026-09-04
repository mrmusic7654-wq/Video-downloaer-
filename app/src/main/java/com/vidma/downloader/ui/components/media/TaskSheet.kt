package com.vidma.downloader.ui.components.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vidma.downloader.domain.model.DownloadTask
import com.vidma.downloader.ui.theme.LocalVidmaPalette
import com.vidma.downloader.ui.theme.VidmaBase
import com.vidma.downloader.ui.theme.VidmaPalette

/** Full queue sheet — used by the active-download tray. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskQueueSheet(
    tasks: List<DownloadTask>,
    onDismiss: () -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDismissTask: (String) -> Unit,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        scrimColor = VidmaBase.Scrim.copy(alpha = 0.7f),
        dragHandle = null,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF161A34), Color(0xFF0A0C1E))),
                )
                .navigationBarsPadding(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 44.dp, height = 5.dp)
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp)),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (tasks.size == 1) "1 active download" else "${tasks.size} active downloads",
                    style = MaterialTheme.typography.headlineSmall.copy(color = VidmaBase.TextHigh),
                )
                Spacer(Modifier.height(14.dp))
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                ) {
                    items(tasks, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            onCancel = onCancel,
                            onRetry = onRetry,
                            onDismiss = onDismissTask,
                        )
                    }
                }
            }
        }
    }
}
