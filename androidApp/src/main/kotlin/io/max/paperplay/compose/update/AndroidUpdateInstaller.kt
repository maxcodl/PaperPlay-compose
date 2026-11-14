package io.max.paperplay.compose.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import io.max.paperplay.core.update.Downloaded
import io.max.paperplay.core.update.InstallBlocked
import io.max.paperplay.core.update.UpdateInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Downloads the APK into the app's cache and hands it to Android's package installer, which asks the person to confirm. */
class AndroidUpdateInstaller(
    private val context: Context,
    /** True when the person chose Shizuku; the choice lives in the update settings. */
    private val wantsShizuku: () -> Boolean,
    private val chooseSystem: () -> Unit,
) : UpdateInstaller {
    override val shizuku = ShizukuInstaller(context)

    private val dir get() = File(context.cacheDir, "updates")

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
        if (wantsShizuku()) return shizuku.install(path, chooseSystem)
        if (!context.packageManager.canRequestPackageInstalls()) {
            val open = {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            Toast.makeText(context, "Allow PaperPlay to install updates", Toast.LENGTH_LONG).show()
            open() // ask now; the page keeps checking and installs as soon as it is allowed
            return InstallBlocked("Turn on \"Allow from this source\" for PaperPlay; the install continues by itself.", "Open settings", open)
        }
        withContext(Dispatchers.IO) { commit(File(path)) }
        return null
    }

    override fun delete(path: String) {
        File(path).delete()
    }

    private fun commit(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input -> session.openWrite("update.apk", 0, apk.length()).use { out -> input.copyTo(out); session.fsync(out) } }
            val result = Intent(context, UpdateInstallReceiver::class.java)
            val pending = PendingIntent.getBroadcast(context, id, result, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
    }
}
