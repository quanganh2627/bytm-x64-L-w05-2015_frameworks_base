/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.content;

import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.pm.PackageParser.PackageLite;
import android.os.Build;
import android.os.SystemProperties;
import android.util.Slog;

import com.android.internal.os.CheckExt;
import com.android.internal.os.ICheckExt;

import java.io.File;

/**
 * Native libraries helper.
 *
 * @hide
 */
public class NativeLibraryHelper {
    private static final String TAG = "NativeHelper";

    private static final boolean DEBUG_NATIVE = false;
    private static final boolean ENABLE_HOUDINI = Build.CPU_ABI.equals("x86") &&
            (Build.CPU_ABI2.length()!=0);

    private static native long nativeSumNativeBinaries(String file, String cpuAbi, String cpuAbi2);

    /**
     * Sums the size of native binaries in an APK.
     *
     * @param apkFile APK file to scan for native libraries
     * @return size of all native binary files in bytes
     */
    public static long sumNativeBinariesLI(File apkFile) {
        final String cpuAbi = Build.CPU_ABI;
        final String cpuAbi2 = Build.CPU_ABI2;

        if (ENABLE_HOUDINI) {
            final String abiUpgrade = SystemProperties.get("ro.product.cpu.upgradeabi",
                    "armeabi");
            final int INSTALL_ABI_SUCCEEDED = 99;
            final int INSTALL_MISMATCH_ABI2_SUCCEEDED = 100;
            final int INSTALL_MISMATCH_UPGRADEABI_SUCCEEDED = 101;
            final int INSTALL_UPGRADEABI_SUCCEEDED = 102;

            int result = nativeListNativeBinaries(apkFile.getPath(), cpuAbi, cpuAbi2, abiUpgrade);
            switch(result) {
                case INSTALL_MISMATCH_ABI2_SUCCEEDED:
                case INSTALL_MISMATCH_UPGRADEABI_SUCCEEDED:
                    // workaround to handle lib mismatch cases
                    ICheckExt check = new CheckExt();
                    String pkgName = getPackageName(apkFile.getPath());
                    if (check.doCheck(pkgName, new String("white"))) {
                        return nativeSumNativeBinaries(apkFile.getPath(), cpuAbi, cpuAbi);
                    } else {
                        if (result == INSTALL_MISMATCH_ABI2_SUCCEEDED) {
                            return nativeSumNativeBinaries(apkFile.getPath(), cpuAbi2, cpuAbi2);
                        } else {
                            return nativeSumNativeBinaries(apkFile.getPath(), abiUpgrade,
                                    abiUpgrade);
                        }
                    }
                case PackageManager.INSTALL_ABI2_SUCCEEDED:
                    return nativeSumNativeBinaries(apkFile.getPath(), cpuAbi2, cpuAbi2);
                case INSTALL_UPGRADEABI_SUCCEEDED:
                    return nativeSumNativeBinaries(apkFile.getPath(), abiUpgrade, abiUpgrade);
                case INSTALL_ABI_SUCCEEDED:
                    return nativeSumNativeBinaries(apkFile.getPath(), cpuAbi, cpuAbi);
                default:
                    return nativeSumNativeBinaries(apkFile.getPath(), cpuAbi, cpuAbi2);
            }
        } else {
            return nativeSumNativeBinaries(apkFile.getPath(), cpuAbi, cpuAbi2);
        }
    }

    private static native int nativeListNativeBinaries(String file, String cpuAbi,
            String cpuAbi2, String upgradeAbi);

    /**
     * List the native binaries info in an APK.
     *
     * @param apkFile APK file to scan for native libraries
     * @return {@link PackageManager#INSTALL_SUCCEEDED} or
             {@link PackageManager#INSTALL_ABI2_SUCCEEDED}
     *         or another error code from that class if not
     */
    public static int listNativeBinariesLI(File apkFile, String pkgName) {
        final String cpuAbi = Build.CPU_ABI;
        final String cpuAbi2 = Build.CPU_ABI2;

        if (ENABLE_HOUDINI) {
            final String abiUpgrade = SystemProperties.get("ro.product.cpu.upgradeabi",
                    "armeabi");
            final int INSTALL_ABI_SUCCEEDED = 99;
            final int INSTALL_MISMATCH_ABI2_SUCCEEDED = 100;
            final int INSTALL_MISMATCH_UPGRADEABI_SUCCEEDED = 101;
            final int INSTALL_UPGRADEABI_SUCCEEDED = 102;
            final int INSTALL_IMPLICIT_ABI2_SUCCEEDED = 103;
            final int INSTALL_IMPLICIT_UPGRADEABI_SUCCEEDED = 104;


            int result = nativeListNativeBinaries(apkFile.getPath(), cpuAbi, cpuAbi2, abiUpgrade);
            switch(result) {
                case INSTALL_MISMATCH_ABI2_SUCCEEDED:
                case INSTALL_MISMATCH_UPGRADEABI_SUCCEEDED:
                    // workaround to handle lib mismatch cases
                    ICheckExt check = new CheckExt();
                    if (check.doCheck(pkgName, new String("white"))) {
                        return PackageManager.INSTALL_SUCCEEDED;
                    } else {
                        return PackageManager.INSTALL_ABI2_SUCCEEDED;
                    }
                case PackageManager.INSTALL_ABI2_SUCCEEDED:
                case INSTALL_UPGRADEABI_SUCCEEDED:
                    return PackageManager.INSTALL_ABI2_SUCCEEDED;
                case INSTALL_ABI_SUCCEEDED:
                    return PackageManager.INSTALL_ABI2_SUCCEEDED;
                case INSTALL_IMPLICIT_ABI2_SUCCEEDED:
                case INSTALL_IMPLICIT_UPGRADEABI_SUCCEEDED:
                    return PackageManager.INSTALL_IMPLICIT_ABI_SUCCEEDED;
                default:
                    return result;
            }
        } else {
            return PackageManager.INSTALL_SUCCEEDED;
        }
    }


    private native static int nativeCopyNativeBinaries(String filePath, String sharedLibraryPath,
            String cpuAbi, String cpuAbi2);

    /**
     * Copies native binaries to a shared library directory.
     *
     * @param apkFile APK file to scan for native libraries
     * @param sharedLibraryDir directory for libraries to be copied to
     * @return {@link PackageManager#INSTALL_SUCCEEDED} or
             {@link PackageManager#INSTALL_ABI2_SUCCEEDED}
     *         if successful or another error code from that class if not
     */
    public static int copyNativeBinariesIfNeededLI(File apkFile, File sharedLibraryDir) {
        final String cpuAbi = Build.CPU_ABI;
        final String cpuAbi2 = Build.CPU_ABI2;

        if (ENABLE_HOUDINI) {
            final String abiUpgrade = SystemProperties.get("ro.product.cpu.upgradeabi",
                    "armeabi");
            final int INSTALL_ABI_SUCCEEDED = 99;
            final int INSTALL_MISMATCH_ABI2_SUCCEEDED = 100;
            final int INSTALL_MISMATCH_UPGRADEABI_SUCCEEDED = 101;
            final int INSTALL_UPGRADEABI_SUCCEEDED = 102;
            final int INSTALL_IMPLICIT_ABI2_SUCCEEDED = 103;
            final int INSTALL_IMPLICIT_UPGRADEABI_SUCCEEDED = 104;

            int result = nativeListNativeBinaries(apkFile.getPath(), cpuAbi, cpuAbi2, abiUpgrade);
            switch(result) {
                case INSTALL_MISMATCH_ABI2_SUCCEEDED:
                case INSTALL_MISMATCH_UPGRADEABI_SUCCEEDED:
                    // workaround to handle lib mismatch cases
                    String pkgName = getPackageName(apkFile.getPath());
                    ICheckExt check = new CheckExt();
                    if (check.doCheck(pkgName, new String("white"))) {
                        nativeCopyNativeBinaries(apkFile.getPath(), sharedLibraryDir.getPath(),
                                cpuAbi, cpuAbi);
                        return PackageManager.INSTALL_SUCCEEDED;
                    } else {
                        if (result == INSTALL_MISMATCH_ABI2_SUCCEEDED) {
                            nativeCopyNativeBinaries(apkFile.getPath(), sharedLibraryDir.getPath(),
                                    cpuAbi2, cpuAbi2);
                        } else {
                            nativeCopyNativeBinaries(apkFile.getPath(), sharedLibraryDir.getPath(),
                                    abiUpgrade, abiUpgrade);
                        }
                        return PackageManager.INSTALL_ABI2_SUCCEEDED;
                    }
                case PackageManager.INSTALL_ABI2_SUCCEEDED:
                    nativeCopyNativeBinaries(apkFile.getPath(), sharedLibraryDir.getPath(),
                            cpuAbi2, cpuAbi2);
                    return PackageManager.INSTALL_ABI2_SUCCEEDED;
                case INSTALL_UPGRADEABI_SUCCEEDED:
                    nativeCopyNativeBinaries(apkFile.getPath(), sharedLibraryDir.getPath(),
                            abiUpgrade, abiUpgrade);
                    return PackageManager.INSTALL_ABI2_SUCCEEDED;
                case INSTALL_ABI_SUCCEEDED:
                    nativeCopyNativeBinaries(apkFile.getPath(), sharedLibraryDir.getPath(),
                            cpuAbi, cpuAbi);
                    return PackageManager.INSTALL_SUCCEEDED;
                case INSTALL_IMPLICIT_ABI2_SUCCEEDED:
                    nativeCopyNativeBinaries(apkFile.getPath(), sharedLibraryDir.getPath(),
                            cpuAbi2, cpuAbi2);
                    return PackageManager.INSTALL_ABI2_SUCCEEDED;
                case INSTALL_IMPLICIT_UPGRADEABI_SUCCEEDED:
                    nativeCopyNativeBinaries(apkFile.getPath(), sharedLibraryDir.getPath(),
                            abiUpgrade, abiUpgrade);
                    return PackageManager.INSTALL_ABI2_SUCCEEDED;
                default:
                    return nativeCopyNativeBinaries(apkFile.getPath(), sharedLibraryDir.getPath(),
                            cpuAbi, cpuAbi2);
            }
        } else {
            return nativeCopyNativeBinaries(apkFile.getPath(), sharedLibraryDir.getPath(),
                    cpuAbi, cpuAbi2);
        }
    }

    // Convenience method to call removeNativeBinariesFromDirLI(File)
    public static boolean removeNativeBinariesLI(String nativeLibraryPath) {
        return removeNativeBinariesFromDirLI(new File(nativeLibraryPath));
    }

    // Remove the native binaries of a given package. This simply
    // gets rid of the files in the 'lib' sub-directory.
    public static boolean removeNativeBinariesFromDirLI(File nativeLibraryDir) {
        if (DEBUG_NATIVE) {
            Slog.w(TAG, "Deleting native binaries from: " + nativeLibraryDir.getPath());
        }

        boolean deletedFiles = false;

        /*
         * Just remove any file in the directory. Since the directory is owned
         * by the 'system' UID, the application is not supposed to have written
         * anything there.
         */
        if (nativeLibraryDir.exists()) {
            final File[] binaries = nativeLibraryDir.listFiles();
            if (binaries != null) {
                for (int nn = 0; nn < binaries.length; nn++) {
                    if (DEBUG_NATIVE) {
                        Slog.d(TAG, "    Deleting " + binaries[nn].getName());
                    }

                    if (!binaries[nn].delete()) {
                        Slog.w(TAG, "Could not delete native binary: " + binaries[nn].getPath());
                    } else {
                        deletedFiles = true;
                    }
                }
            }
            // Do not delete 'lib' directory itself, or this will prevent
            // installation of future updates.
        }

        return deletedFiles;
    }

    private static String getPackageName(String packageFilePath) {
        PackageLite pkg = PackageParser.parsePackageLite(packageFilePath, 0);
        if (pkg != null)
            return pkg.packageName;
        return null;
    }
}
