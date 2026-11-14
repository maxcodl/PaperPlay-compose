package io.max.paperplay.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.max.paperplay.core.update.InstallBlocked
import io.max.paperplay.core.update.UpdateService
import io.max.paperplay.core.update.UpdateState
import kotlinx.coroutines.launch

private const val REPO = "https://github.com/maxcodl/PaperPlay-compose"

/** Settings > About and updates: the version, the update check and its choices, links. */
@Composable
internal fun AboutPage(backend: CoreBackend) {
    val updates = backend.services.updates
    val uri = LocalUriHandler.current
    SettingsRow("PaperPlay", icon = Icons.Rounded.Info, subtitle = "Version ${updates.installed}")
    SectionLabel("Updates")
    UpdateCard(updates)
    var beta by remember { mutableStateOf(updates.prefs.beta) }
    var auto by remember { mutableStateOf(updates.prefs.auto) }
    SwitchRow("Beta updates", beta, { updates.prefs.beta = it; beta = it }, Icons.Rounded.NewReleases,
        "Also offer pre-releases. They arrive sooner and may have rough edges")
    SwitchRow("Check automatically", auto, { updates.prefs.auto = it; auto = it }, subtitle = "At most once a day, when the app starts")
    SectionLabel("Project")
    SettingsRow("Source code", icon = Icons.Rounded.Code, subtitle = "github.com/maxcodl/PaperPlay-compose", onClick = { uri.openUri(REPO) })
    SettingsRow("Releases", icon = Icons.Rounded.SystemUpdate, subtitle = "Every version with its notes", onClick = { uri.openUri("$REPO/releases") })
    SettingsRow("Change log", icon = Icons.Rounded.History, subtitle = "What changed in each stable release", onClick = { uri.openUri("$REPO/blob/main/CHANGELOG.md") })
    MadeBy()
}

@Composable
private fun UpdateCard(updates: UpdateService) {
    val state by updates.state.collectAsState()
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var blocked by remember { mutableStateOf<InstallBlocked?>(null) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (val s = state) {
            UpdateState.Idle -> Text("Not checked yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            UpdateState.Checking -> { Text("Checking for updates"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            is UpdateState.UpToDate -> Text("You have the latest version.", color = MaterialTheme.colorScheme.primary)
            is UpdateState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
            is UpdateState.Available -> {
                Text("Version ${s.release.version} is available", style = MaterialTheme.typography.titleMedium)
                ReleaseNotes(s.release.notes)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.asset != null) Button({ scope.launch { updates.download() } }) { Text("Download") }
                    else Button({ uri.openUri(s.release.pageUrl) }) { Text("Open release page") }
                    OutlinedButton({ updates.skip() }) { Text("Skip this version") }
                }
            }
            is UpdateState.Downloading -> {
                Text("Downloading ${s.release.version}")
                LinearProgressIndicator({ s.fraction }, Modifier.fillMaxWidth())
            }
            is UpdateState.Ready -> {
                Text("Version ${s.release.version} is downloaded and checked.", style = MaterialTheme.typography.titleMedium)
                Button({ scope.launch { blocked = updates.install() } }) { Text("Install") }
                blocked?.let { b ->
                    Text(b.message, color = MaterialTheme.colorScheme.error)
                    b.action?.let { act -> OutlinedButton({ act() }) { Text(b.actionLabel ?: "Open settings") } }
                }
            }
        }
        if (state !is UpdateState.Checking && state !is UpdateState.Downloading) {
            OutlinedButton({ blocked = null; scope.launch { updates.check() } }) { Text("Check for updates") }
        }
    }
}

/** The release notes as plain lines: markdown headings in bold, bullets as they are. */
@Composable
private fun ReleaseNotes(notes: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        notes.lines().filter { it.isNotBlank() }.take(40).forEach { line ->
            if (line.startsWith("#")) Text(line.trimStart('#', ' '), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
            else Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
