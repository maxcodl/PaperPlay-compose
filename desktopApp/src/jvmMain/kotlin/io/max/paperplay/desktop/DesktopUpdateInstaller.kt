package io.max.paperplay.desktop

import io.max.paperplay.core.update.Downloaded
import io.max.paperplay.core.update.InstallBlocked
import io.max.paperplay.core.update.UpdateInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Windows: downloads the setup exe to the temp folder and starts it; the installer replaces the running app. */
class DesktopUpdateInstaller : UpdateInstaller {
    private val dir = File(System.getProperty("java.io.tmpdir"), "paperplay-update")

    override suspend fun download(url: String, name: String, onProgress: (Float) -> Unit): Downloaded = withContext(Dispatchers.IO) {
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() } // only the update being fetched is kept
        val file = File(dir, name)
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("User-Agent", "PaperPlay")
            if (conn.responseCode != 200) throw IllegalStateException("The download answered ${conn.responseCode}")
            val total = conn.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            Downloaded(file.path, digest.digest().joinToString("") { "%02x".format(it) })
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun install(path: String): InstallBlocked? {
        withContext(Dispatchers.IO) { ProcessBuilder(path).start() }
        return null
    }

    override fun delete(path: String) {
        File(path).delete()
    }
}
