package io.max.paperplay.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
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
    InstallMethod(updates)
    SwitchRow("Check automatically", auto, { updates.prefs.auto = it; auto = it }, subtitle = "At most once a day, when the app starts")
    SectionLabel("Project")
    SettingsRow("Source code", icon = Icons.Rounded.Code, subtitle = "github.com/maxcodl/PaperPlay-compose", onClick = { uri.openUri(REPO) })
    SettingsRow("Releases", icon = Icons.Rounded.SystemUpdate, subtitle = "Every version with its notes", onClick = { uri.openUri("$REPO/releases") })
    SettingsRow("Change log", icon = Icons.Rounded.History, subtitle = "What changed in each stable release", onClick = { uri.openUri("$REPO/blob/main/CHANGELOG.md") })
    MadeBy()
}

/** Android: System installer or Shizuku, with Shizuku's own state and setup directly below. */
@Composable
private fun InstallMethod(updates: UpdateService) {
    val shizuku = updates.shizuku ?: return
    var useShizuku by remember { mutableStateOf(updates.prefs.shizuku) }
    var problem by remember { mutableStateOf<String?>(null) }
    // Shizuku can be started or allowed in another app, so its state is read again while the page is open.
    var status by remember { mutableStateOf(shizukuStatus(shizuku)) }
    androidx.compose.runtime.LaunchedEffect(shizuku) {
        while (true) {
            status = shizukuStatus(shizuku)
            useShizuku = updates.prefs.shizuku // the install button can switch back to the system installer
            kotlinx.coroutines.delay(1000)
        }
    }
    SectionLabel("Installation method")
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChoiceRow(
            listOf(false, true), useShizuku, { if (it) "Shizuku" else "System installer" },
            onSelect = { shizukuChosen ->
                if (shizukuChosen && !shizuku.installed) {
                    problem = "Install Shizuku first"
                } else {
                    problem = null
                    useShizuku = shizukuChosen
                    updates.prefs.shizuku = shizukuChosen
                    if (shizukuChosen && !shizuku.allowed) shizuku.ask()
                }
            },
        )
        Text(
            if (useShizuku) "Installs updates without the confirmation screen." else "Android asks you to confirm each update.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    SettingsRow(
        "Shizuku", icon = Icons.Rounded.Bolt, subtitle = status.first,
        onClick = if (status.second != null) ({ shizuku.ask() }) else null,
        trailing = { status.second?.let { label -> OutlinedButton({ shizuku.ask() }) { Text(label) } } },
    )
}

/** What Shizuku's state means for the person, and the button that moves it on (null when nothing is left to do). */
private fun shizukuStatus(s: io.max.paperplay.core.update.ShizukuControl): Pair<String, String?> = when {
    !s.installed -> "Not installed. Get Shizuku to use this option" to null
    !s.running -> "Installed but not running. Start it in the Shizuku app" to "Open Shizuku"
    !s.allowed -> "Running. PaperPlay is not allowed yet" to "Allow"
    else -> "Running and allowed" to null
}

@Composable
private fun UpdateCard(updates: UpdateService) {
    val state by updates.state.collectAsState()
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var blocked by remember { mutableStateOf<InstallBlocked?>(null) }
    // While the install waits for a permission, look for it once a second and carry on as soon as it is granted.
    val hooks = io.max.paperplay.ui.LocalAppEnv.current?.permissions
    androidx.compose.runtime.LaunchedEffect(blocked) {
        if (blocked == null || hooks == null) return@LaunchedEffect
        while (hooks.check().firstOrNull { it.id == "install" }?.granted == false) kotlinx.coroutines.delay(1000)
        blocked = updates.install()
    }
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
