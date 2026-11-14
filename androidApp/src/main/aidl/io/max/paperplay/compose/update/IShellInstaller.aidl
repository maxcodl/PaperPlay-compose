package io.max.paperplay.compose.update;

import android.os.ParcelFileDescriptor;

interface IShellInstaller {
    /** Shizuku calls this to stop the service. */
    void destroy() = 16777114;

    /** Installs the APK read from [apk] with the shell's package manager. Returns "" on success, otherwise the error text. */
    String install(in ParcelFileDescriptor apk, long size) = 1;
}
