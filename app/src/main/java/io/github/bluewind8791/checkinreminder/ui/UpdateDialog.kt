package io.github.bluewind8791.checkinreminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bluewind8791.checkinreminder.BuildConfig
import io.github.bluewind8791.checkinreminder.ui.theme.Palette
import io.github.bluewind8791.checkinreminder.update.Release
import io.github.bluewind8791.checkinreminder.update.UpdateClient
import kotlinx.coroutines.launch
import java.io.IOException

/** A newer release and what changed. 「업데이트」 downloads it, then the system asks to install. */
@Composable
fun UpdateDialog(release: Release, client: UpdateClient, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Null before the download starts.
    var progress by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val downloading = progress != null
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("새 버전 ${release.versionName}", color = Palette.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("지금 버전 ${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = Palette.textMuted)
                for (note in release.notes) {
                    Text("· $note", fontSize = 14.sp, color = Palette.textSub)
                }
                when {
                    error != null -> Text(error.orEmpty(), fontSize = 12.sp, color = Palette.red)
                    downloading -> Text("내려받는 중 $progress%", fontSize = 12.sp, color = Palette.textMuted)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !downloading,
                onClick = {
                    progress = 0
                    error = null
                    scope.launch {
                        try {
                            client.install(context.applicationContext, release) { progress = it }
                            onDismiss()
                        } catch (e: IOException) {
                            error = "내려받지 못했습니다 · ${e.javaClass.simpleName}"
                            progress = null
                        }
                    }
                },
            ) { Text("업데이트", color = if (downloading) Palette.textFaint else Palette.brand) }
        },
        dismissButton = {
            TextButton(enabled = !downloading, onClick = onDismiss) {
                Text("나중에", color = Palette.textSub)
            }
        },
        containerColor = Palette.card,
    )
}
