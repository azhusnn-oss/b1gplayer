package com.b1g.player.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.selection.SelectionContainer
import androidx.compose.ui.unit.dp

/**
 * Shown once after a crash. The trace is selectable and copyable because the usual
 * way this build gets debugged is someone pasting it into a chat.
 */
@Composable
fun CrashScreen(
    report: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("The app crashed last time", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Copy this and send it on — it says exactly what went wrong.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { clipboard.setText(AnnotatedString(report)) }) { Text("Copy") }
            OutlinedButton(onClick = onDismiss) { Text("Continue") }
        }

        SelectionContainer(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = report,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}
