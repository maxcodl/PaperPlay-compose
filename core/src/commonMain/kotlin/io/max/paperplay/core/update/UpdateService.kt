package io.max.paperplay.core.update

import io.max.paperplay.core.integrations.HttpFetcher
import io.max.paperplay.core.integrations.HttpRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the update check found and how far an update has got. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState

    /** [asset] is null where there is no installer to run (Linux): the release page is offered instead. */
    data class Available(val release: ReleaseInfo, val asset: ReleaseAsset?) : UpdateState
    data class Downloading(val release: ReleaseInfo, val fraction: Float) : UpdateState
    data class Ready(val release: ReleaseInfo, val path: String) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class Downloaded(val path: String, val sha256: String)

/** Why an install did not start, in words for the person; null from [UpdateInstaller.install] means it started. */
class InstallBlocked(val message: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

/** What the About page needs to show and set up Shizuku. */
interface ShizukuControl {
    val installed: Boolean
    val running: Boolean
    /** Shizuku has allowed this app. */
    val allowed: Boolean

    /** Asks Shizuku to allow this app (its own dialog), or opens Shizuku when it is not running. */
    fun ask()
}

/** The part that differs by platform: fetching the file and handing it to the system. */
interface UpdateInstaller {
    /** Android only: Shizuku, an optional way to install without the system confirmation screen. Null where there is no such choice. */
    val shizuku: ShizukuControl? get() = null

    /** Streams [url] to a file named [name] and returns where it went and its SHA-256 (lower-case hex). Throws on failure. */
    suspend fun download(url: String, name: String, onProgress: (Float) -> Unit): Downloaded

    /** Starts the install. Null when it started; otherwise why it could not (a missing permission, for example). */
    suspend fun install(path: String): InstallBlocked?

    fun delete(path: String)
}

/** The person's update choices, kept as settings of this device. */
class UpdatePrefs(private val read: (String) -> String?, private val write: (String, String) -> Unit) {
    var beta: Boolean
        get() = read("update_channel") == "beta"
        set(v) = write("update_channel", if (v) "beta" else "stable")
    var auto: Boolean
        get() = read("update_auto") != "false"
        set(v) = write("update_auto", v.toString())
    var shizuku: Boolean
        get() = read("update_installer") == "shizuku"
        set(v) = write("update_installer", if (v) "shizuku" else "system")
    var skipped: String?
        get() = read("update_skipped")?.takeIf { it.isNotEmpty() }
        set(v) = write("update_skipped", v.orEmpty())
    var lastCheck: Long
        get() = read("update_last_check")?.toLongOrNull() ?: 0L
        set(v) = write("update_last_check", v.toString())
}

/** Looks for a newer release on GitHub, downloads it, checks its SHA-256 against the published list and hands it to the installer. */
class UpdateService(
    private val http: HttpFetcher,
    private val installer: UpdateInstaller?,
    val prefs: UpdatePrefs,
    private val platform: UpdatePlatform,
    val installed: Version,
    private val now: () -> Long,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    val shizuku get() = installer?.shizuku

    /** True when automatic checks are on and the last one is over a day old. */
    fun autoCheckDue() = prefs.auto && now() - prefs.lastCheck > DAY_MS

    suspend fun check() {
        _state.value = UpdateState.Checking
        try {
            val r = http.send(
                HttpRequest(
                    FEED_URL,
                    headers = mapOf("accept" to "application/vnd.github+json", "user-agent" to "PaperPlay/$installed"),
                ),
            )
            if (r.status != 200) throw IllegalStateException("GitHub answered ${r.status}")
            prefs.lastCheck = now()
            val found = ReleaseFeed.newest(ReleaseFeed.parse(r.text), installed, prefs.beta, prefs.skipped)
            _state.value = if (found == null) UpdateState.UpToDate(now()) else UpdateState.Available(found, ReleaseFeed.assetFor(found, platform))
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.message ?: "The update check failed")
        }
    }

    /** Does not offer this release again; a newer one still shows. */
    fun skip() {
        val s = _state.value as? UpdateState.Available ?: return
        prefs.skipped = s.release.tag
        _state.value = UpdateState.UpToDate(now())
    }

    suspend fun download() {
        val s = _state.value as? UpdateState.Available ?: return
        val asset = s.asset ?: return
        val inst = installer ?: return
        _state.value = UpdateState.Downloading(s.release, 0f)
        try {
            if (!ReleaseFeed.isTrusted(asset.url)) throw IllegalStateException("The file is not from this project's releases")
            val sums = s.release.assets.firstOrNull { it.name == ReleaseFeed.CHECKSUMS }
                ?: throw IllegalStateException("This release has no checksum list")
            if (!ReleaseFeed.isTrusted(sums.url)) throw IllegalStateException("The checksum list is not from this project's releases")
            val expected = ReleaseFeed.parseChecksums(http.send(HttpRequest(sums.url)).text)[asset.name]
                ?: throw IllegalStateException("No checksum for ${asset.name}")
            val file = inst.download(asset.url, asset.name) { _state.value = UpdateState.Downloading(s.release, it) }
            if (!file.sha256.equals(expected, ignoreCase = true)) {
                inst.delete(file.path)
                throw IllegalStateException("The downloaded file does not match its checksum")
            }
            _state.value = UpdateState.Ready(s.release, file.path)
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.message ?: "The download failed")
        }
    }

    /** Starts the install of a downloaded update. Null when it started; otherwise the reason it could not. */
    suspend fun install(): InstallBlocked? {
        val s = _state.value as? UpdateState.Ready ?: return null
        return installer?.install(s.path)
    }

    private companion object {
        const val FEED_URL = "https://api.github.com/repos/maxcodl/PaperPlay-compose/releases?per_page=30"
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
