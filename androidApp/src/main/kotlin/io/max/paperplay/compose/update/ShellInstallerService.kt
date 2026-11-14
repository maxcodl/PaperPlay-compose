package io.max.paperplay.compose.update

import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import kotlin.system.exitProcess

/** Runs inside Shizuku's process as the shell user, which may install packages without the confirmation screen. */
class ShellInstallerService : IShellInstaller.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun install(apk: ParcelFileDescriptor, size: Long): String {
        val process = Runtime.getRuntime().exec(arrayOf("pm", "install", "-r", "-S", size.toString()))
        FileInputStream(apk.fileDescriptor).use { input -> process.outputStream.use { input.copyTo(it) } }
        apk.close()
        val text = process.inputStream.bufferedReader().readText() + process.errorStream.bufferedReader().readText()
        return if (process.waitFor() == 0 && text.contains("Success")) "" else text.trim().ifEmpty { "The install failed" }
    }
}
