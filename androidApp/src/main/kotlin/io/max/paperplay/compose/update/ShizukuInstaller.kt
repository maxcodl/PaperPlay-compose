package io.max.paperplay.compose.update

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.widget.Toast
import io.max.paperplay.core.update.InstallBlocked
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import java.io.File
import kotlin.coroutines.resume

/** Installs through Shizuku: no confirmation screen, but Shizuku must be running and must have allowed PaperPlay. */
class ShizukuInstaller(private val context: Context) {
    val installed: Boolean
        get() = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0); true }.getOrDefault(false)

    val running: Boolean get() = Shizuku.pingBinder()

    val allowed: Boolean get() = running && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    /** Opens Shizuku's own grant dialog and waits for the answer. */
    suspend fun askPermission(): Boolean = suspendCancellableCoroutine { cont ->
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != REQUEST) return
                Shizuku.removeRequestPermissionResultListener(this)
                if (cont.isActive) cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        cont.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
        Shizuku.requestPermission(REQUEST)
    }

    fun openShizuku() {
        context.packageManager.getLaunchIntentForPackage(PACKAGE)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
    }

    /** Null when the install went through; otherwise why not, after a toast. [useSystem] is offered as the way out. */
    suspend fun install(path: String, useSystem: () -> Unit): InstallBlocked? {
        val systemButton = "Use the system installer"
        if (!running) {
            Toast.makeText(context, "Start Shizuku first", Toast.LENGTH_LONG).show()
            return InstallBlocked("Shizuku is not running. Start it, then tap Install again.", "Open Shizuku") { openShizuku() }
        }
        if (!allowed && !askPermission()) {
            Toast.makeText(context, "Shizuku permission denied", Toast.LENGTH_LONG).show()
            return InstallBlocked("Shizuku did not allow PaperPlay.", systemButton, useSystem)
        }
        val error = runInShell(File(path))
        if (error.isNotEmpty()) return InstallBlocked("Shizuku could not install it: $error", systemButton, useSystem)
        Toast.makeText(context, "Update installed", Toast.LENGTH_LONG).show()
        return null
    }

    private suspend fun runInShell(apk: File): String {
        val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellInstallerService::class.java.name))
            .processNameSuffix("installer").daemon(false).version(1)
        var connection: ServiceConnection? = null
        try {
            val service = suspendCancellableCoroutine<IShellInstaller> { cont ->
                val c = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        if (cont.isActive) cont.resume(IShellInstaller.Stub.asInterface(binder))
                    }
                    override fun onServiceDisconnected(name: ComponentName) = Unit
                }
                connection = c
                Shizuku.bindUserService(args, c)
            }
            return ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY).use { service.install(it, apk.length()) }
        } finally {
            connection?.let { Shizuku.unbindUserService(args, it, true) }
        }
    }

    private companion object {
        const val PACKAGE = "moe.shizuku.privileged.api"
        const val REQUEST = 4217
    }
}
