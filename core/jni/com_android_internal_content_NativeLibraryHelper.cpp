/*
 * Copyright (C) 2011 The Android Open Source Project
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

#define LOG_TAG "NativeLibraryHelper"
//#define LOG_NDEBUG 0

#include <android_runtime/AndroidRuntime.h>

#include <utils/Log.h>
#include <androidfw/ZipFileRO.h>
#include <ScopedUtfChars.h>

#include <zlib.h>

#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <vector>
#include <string>

#define APK_LIB "lib/"
#define APK_LIB_LEN (sizeof(APK_LIB) - 1)

#define LIB_PREFIX "/lib"
#define LIB_PREFIX_LEN (sizeof(LIB_PREFIX) - 1)

#define APK_ASSETS "assets/"
#define APK_ASSETS_LEN (sizeof(APK_ASSETS) - 1)

#define LIB_SUFFIX ".so"
#define LIB_SUFFIX_LEN (sizeof(LIB_SUFFIX) - 1)

#define GDBSERVER "gdbserver"
#define GDBSERVER_LEN (sizeof(GDBSERVER) - 1)

#define TMP_FILE_PATTERN "/tmp.XXXXXX"
#define TMP_FILE_PATTERN_LEN (sizeof(TMP_FILE_PATTERN) - 1)

namespace android {

// These match PackageManager.java install codes
typedef enum {
    INSTALL_SUCCEEDED = 1,
#ifdef WITH_HOUDINI
    INSTALL_ABI2_SUCCEEDED = 2,
    INSTALL_ABI_SUCCEEDED = 99,
    INSTALL_MISMATCH_ABI2_SUCCEEDED = 100,
    INSTALL_MISMATCH_UPGRADEABI_SUCCEEDED = 101,
    INSTALL_UPGRADEABI_SUCCEEDED = 102,
    INSTALL_IMPLICIT_ABI2_SUCCEEDED = 103,
    INSTALL_IMPLICIT_UPGRADEABI_SUCCEEDED = 104,
#endif
    INSTALL_FAILED_INVALID_APK = -2,
    INSTALL_FAILED_INSUFFICIENT_STORAGE = -4,
#ifdef WITH_HOUDINI
    INSTALL_FAILED_CPU_ABI_INCOMPATIBLE = -16,
#endif
    INSTALL_FAILED_CONTAINER_ERROR = -18,
    INSTALL_FAILED_INTERNAL_ERROR = -110,
} install_status_t;

typedef install_status_t (*iterFunc)(JNIEnv*, void*, ZipFileRO*, ZipEntryRO, const char*);

// Equivalent to isFilenameSafe
static bool
isFilenameSafe(const char* filename)
{
    off_t offset = 0;
    for (;;) {
        switch (*(filename + offset)) {
        case 0:
            // Null.
            // If we've reached the end, all the other characters are good.
            return true;

        case 'A' ... 'Z':
        case 'a' ... 'z':
        case '0' ... '9':
        case '+':
        case ',':
        case '-':
        case '.':
        case '/':
        case '=':
        case '_':
            offset++;
            break;

        default:
            // We found something that is not good.
            return false;
        }
    }
    // Should not reach here.
}

static bool
isFileDifferent(const char* filePath, size_t fileSize, time_t modifiedTime,
        long zipCrc, struct stat64* st)
{
    if (lstat64(filePath, st) < 0) {
        // File is not found or cannot be read.
        ALOGV("Couldn't stat %s, copying: %s\n", filePath, strerror(errno));
        return true;
    }

    if (!S_ISREG(st->st_mode)) {
        return true;
    }

    if (st->st_size != fileSize) {
        return true;
    }

    // For some reason, bionic doesn't define st_mtime as time_t
    if (time_t(st->st_mtime) != modifiedTime) {
        ALOGV("mod time doesn't match: %ld vs. %ld\n", st->st_mtime, modifiedTime);
        return true;
    }

    int fd = TEMP_FAILURE_RETRY(open(filePath, O_RDONLY));
    if (fd < 0) {
        ALOGV("Couldn't open file %s: %s", filePath, strerror(errno));
        return true;
    }

    long crc = crc32(0L, Z_NULL, 0);
    unsigned char crcBuffer[16384];
    ssize_t numBytes;
    while ((numBytes = TEMP_FAILURE_RETRY(read(fd, crcBuffer, sizeof(crcBuffer)))) > 0) {
        crc = crc32(crc, crcBuffer, numBytes);
    }
    close(fd);

    ALOGV("%s: crc = %lx, zipCrc = %lx\n", filePath, crc, zipCrc);

    if (crc != zipCrc) {
        return true;
    }

    return false;
}

static install_status_t
sumFiles(JNIEnv* env, void* arg, ZipFileRO* zipFile, ZipEntryRO zipEntry, const char* fileName)
{
    size_t* total = (size_t*) arg;
    size_t uncompLen;

    if (!zipFile->getEntryInfo(zipEntry, NULL, &uncompLen, NULL, NULL, NULL, NULL)) {
        return INSTALL_FAILED_INVALID_APK;
    }

    *total += uncompLen;

    return INSTALL_SUCCEEDED;
}

#ifdef WITH_HOUDINI
static install_status_t
listFiles(JNIEnv* env, void* arg, ZipFileRO* zipFile, ZipEntryRO zipEntry, const char* fileName)
{
    return INSTALL_SUCCEEDED;
}
#endif

/*
 * Copy the native library if needed.
 *
 * This function assumes the library and path names passed in are considered safe.
 */
static install_status_t
copyFileIfChanged(JNIEnv *env, void* arg, ZipFileRO* zipFile, ZipEntryRO zipEntry, const char* fileName)
{
    jstring* javaNativeLibPath = (jstring*) arg;
    ScopedUtfChars nativeLibPath(env, *javaNativeLibPath);

    size_t uncompLen;
    long when;
    long crc;
    time_t modTime;

    if (!zipFile->getEntryInfo(zipEntry, NULL, &uncompLen, NULL, NULL, &when, &crc)) {
        ALOGD("Couldn't read zip entry info\n");
        return INSTALL_FAILED_INVALID_APK;
    } else {
        struct tm t;
        ZipFileRO::zipTimeToTimespec(when, &t);
        modTime = mktime(&t);
    }

    // Build local file path
    const size_t fileNameLen = strlen(fileName);
    char localFileName[nativeLibPath.size() + fileNameLen + 2];

    if (strlcpy(localFileName, nativeLibPath.c_str(), sizeof(localFileName)) != nativeLibPath.size()) {
        ALOGD("Couldn't allocate local file name for library");
        return INSTALL_FAILED_INTERNAL_ERROR;
    }

    *(localFileName + nativeLibPath.size()) = '/';

    if (strlcpy(localFileName + nativeLibPath.size() + 1, fileName, sizeof(localFileName)
                    - nativeLibPath.size() - 1) != fileNameLen) {
        ALOGD("Couldn't allocate local file name for library");
        return INSTALL_FAILED_INTERNAL_ERROR;
    }

    // Only copy out the native file if it's different.
    struct stat st;
    if (!isFileDifferent(localFileName, uncompLen, modTime, crc, &st)) {
        return INSTALL_SUCCEEDED;
    }

    char localTmpFileName[nativeLibPath.size() + TMP_FILE_PATTERN_LEN + 2];
    if (strlcpy(localTmpFileName, nativeLibPath.c_str(), sizeof(localTmpFileName))
            != nativeLibPath.size()) {
        ALOGD("Couldn't allocate local file name for library");
        return INSTALL_FAILED_INTERNAL_ERROR;
    }

    *(localFileName + nativeLibPath.size()) = '/';

    if (strlcpy(localTmpFileName + nativeLibPath.size(), TMP_FILE_PATTERN,
                    TMP_FILE_PATTERN_LEN - nativeLibPath.size()) != TMP_FILE_PATTERN_LEN) {
        ALOGI("Couldn't allocate temporary file name for library");
        return INSTALL_FAILED_INTERNAL_ERROR;
    }

    int fd = mkstemp(localTmpFileName);
    if (fd < 0) {
        ALOGI("Couldn't open temporary file name: %s: %s\n", localTmpFileName, strerror(errno));
        return INSTALL_FAILED_CONTAINER_ERROR;
    }

    if (!zipFile->uncompressEntry(zipEntry, fd)) {
        ALOGI("Failed uncompressing %s to %s\n", fileName, localTmpFileName);
        close(fd);
        unlink(localTmpFileName);
        return INSTALL_FAILED_CONTAINER_ERROR;
    }

    close(fd);

    // Set the modification time for this file to the ZIP's mod time.
    struct timeval times[2];
    times[0].tv_sec = st.st_atime;
    times[1].tv_sec = modTime;
    times[0].tv_usec = times[1].tv_usec = 0;
    if (utimes(localTmpFileName, times) < 0) {
        ALOGI("Couldn't change modification time on %s: %s\n", localTmpFileName, strerror(errno));
        unlink(localTmpFileName);
        return INSTALL_FAILED_CONTAINER_ERROR;
    }

    // Set the mode to 755
    static const mode_t mode = S_IRUSR | S_IWUSR | S_IXUSR | S_IRGRP |  S_IXGRP | S_IROTH | S_IXOTH;
    if (chmod(localTmpFileName, mode) < 0) {
        ALOGI("Couldn't change permissions on %s: %s\n", localTmpFileName, strerror(errno));
        unlink(localTmpFileName);
        return INSTALL_FAILED_CONTAINER_ERROR;
    }

    // Finally, rename it to the final name.
    if (rename(localTmpFileName, localFileName) < 0) {
        ALOGI("Couldn't rename %s to %s: %s\n", localTmpFileName, localFileName, strerror(errno));
        unlink(localTmpFileName);
        return INSTALL_FAILED_CONTAINER_ERROR;
    }

    ALOGV("Successfully moved %s to %s\n", localTmpFileName, localFileName);

    return INSTALL_SUCCEEDED;
}

static install_status_t
iterateOverNativeFiles(JNIEnv *env, jstring javaFilePath, jstring javaCpuAbi, jstring javaCpuAbi2,
        iterFunc callFunc, void* callArg) {
    ScopedUtfChars filePath(env, javaFilePath);
    ScopedUtfChars cpuAbi(env, javaCpuAbi);
    ScopedUtfChars cpuAbi2(env, javaCpuAbi2);

    ZipFileRO zipFile;

    if (zipFile.open(filePath.c_str()) != NO_ERROR) {
        ALOGI("Couldn't open APK %s\n", filePath.c_str());
        return INSTALL_FAILED_INVALID_APK;
    }

    const int N = zipFile.getNumEntries();

    char fileName[PATH_MAX];
    bool hasPrimaryAbi = false;
#ifdef WITH_HOUDINI
    bool useSecondaryAbi = false;
    bool noMatchAbi = false;
#endif

    for (int i = 0; i < N; i++) {
        const ZipEntryRO entry = zipFile.findEntryByIndex(i);
        if (entry == NULL) {
            continue;
        }

        // Make sure this entry has a filename.
        if (zipFile.getEntryFileName(entry, fileName, sizeof(fileName))) {
            continue;
        }

        // Make sure we're in the lib directory of the ZIP.
        if (strncmp(fileName, APK_LIB, APK_LIB_LEN)) {
            continue;
        }

        // Make sure the filename is at least to the minimum library name size.
        const size_t fileNameLen = strlen(fileName);
        static const size_t minLength = APK_LIB_LEN + 2 + LIB_PREFIX_LEN + 1 + LIB_SUFFIX_LEN;
        if (fileNameLen < minLength) {
            continue;
        }

        const char* lastSlash = strrchr(fileName, '/');
        ALOG_ASSERT(lastSlash != NULL, "last slash was null somehow for %s\n", fileName);

        // Check to make sure the CPU ABI of this file is one we support.
        const char* cpuAbiOffset = fileName + APK_LIB_LEN;
        const size_t cpuAbiRegionSize = lastSlash - cpuAbiOffset;

        ALOGV("Comparing ABIs %s and %s versus %s\n", cpuAbi.c_str(), cpuAbi2.c_str(), cpuAbiOffset);
        if (cpuAbi.size() == cpuAbiRegionSize
                && *(cpuAbiOffset + cpuAbi.size()) == '/'
                && !strncmp(cpuAbiOffset, cpuAbi.c_str(), cpuAbiRegionSize)) {
            ALOGV("Using primary ABI %s\n", cpuAbi.c_str());
            hasPrimaryAbi = true;
        } else if (cpuAbi2.size() == cpuAbiRegionSize
                && *(cpuAbiOffset + cpuAbi2.size()) == '/'
                && !strncmp(cpuAbiOffset, cpuAbi2.c_str(), cpuAbiRegionSize)) {

            /*
             * If this library matches both the primary and secondary ABIs,
             * only use the primary ABI.
             */
            if (hasPrimaryAbi) {
                ALOGV("Already saw primary ABI, skipping secondary ABI %s\n", cpuAbi2.c_str());
                continue;
            } else {
#ifdef WITH_HOUDINI
                useSecondaryAbi = true;
#endif
                ALOGV("Using secondary ABI %s\n", cpuAbi2.c_str());
            }
        } else {
#ifdef WITH_HOUDINI
            noMatchAbi = true;
#endif
            ALOGV("abi didn't match anything: %s (end at %zd)\n", cpuAbiOffset, cpuAbiRegionSize);
            continue;
        }

        // If this is a .so file, check to see if we need to copy it.
        if ((!strncmp(fileName + fileNameLen - LIB_SUFFIX_LEN, LIB_SUFFIX, LIB_SUFFIX_LEN)
                    && !strncmp(lastSlash, LIB_PREFIX, LIB_PREFIX_LEN)
                    && isFilenameSafe(lastSlash + 1))
                || !strncmp(lastSlash + 1, GDBSERVER, GDBSERVER_LEN)) {

            install_status_t ret = callFunc(env, callArg, &zipFile, entry, lastSlash + 1);

            if (ret != INSTALL_SUCCEEDED) {
                ALOGV("Failure for entry %s", lastSlash + 1);
                return ret;
            }
        }
    }

#ifdef WITH_HOUDINI
    if (!hasPrimaryAbi && useSecondaryAbi)
        return INSTALL_ABI2_SUCCEEDED;
#endif

    return INSTALL_SUCCEEDED;
}

static jint
com_android_internal_content_NativeLibraryHelper_copyNativeBinaries(JNIEnv *env, jclass clazz,
        jstring javaFilePath, jstring javaNativeLibPath, jstring javaCpuAbi, jstring javaCpuAbi2)
{
    return (jint) iterateOverNativeFiles(env, javaFilePath, javaCpuAbi, javaCpuAbi2,
            copyFileIfChanged, &javaNativeLibPath);
}

static jlong
com_android_internal_content_NativeLibraryHelper_sumNativeBinaries(JNIEnv *env, jclass clazz,
        jstring javaFilePath, jstring javaCpuAbi, jstring javaCpuAbi2)
{
    size_t totalSize = 0;

    iterateOverNativeFiles(env, javaFilePath, javaCpuAbi, javaCpuAbi2, sumFiles, &totalSize);

    return totalSize;
}

#ifdef WITH_HOUDINI

#define MAX_LIB_NAME_LEN    128
#define ASSETS_LIB_LIST_FILE    "/system/lib/arm/.assets_lib_list"

static std::vector<std::string> lib_list;
static bool lib_list_inited = false;

static void init_lib_list()
{
    char line[MAX_LIB_NAME_LEN];
    int i = 0;

    FILE* fp = fopen(ASSETS_LIB_LIST_FILE, "r");
    if (NULL == fp) {
        ALOGE("Couldn't open file %s: %s", ASSETS_LIB_LIST_FILE, strerror(errno));
        return;
    }

    while (fgets(line, MAX_LIB_NAME_LEN, fp)) {
        if ('#' == line[0])
            continue;
        line[strlen(line) - 1] = '\0';
        lib_list.push_back(line);
    }

    // ALOGD("assets_lib_list:");
    // for (i = 0; i < lib_list.size(); ++i) {
    //     ALOGD("%s\n", lib_list[i].c_str());
    // }

    fclose(fp);
    return;
}

static const char* intel_arch[] = {
    "intel",
    "Intel",
    "INTEL",
    "x86",
    "X86",
    NULL
};

static const char * sep_char = "-_.";

static size_t passArchStr (std::string str)
{
    int j = 0;
    size_t pos = ~0;

    for (j=0; intel_arch[j] != NULL; j++) {
        size_t i = str.find(intel_arch[j]);

        if (i != std::string::npos && i < pos) {
            // pass seperate char
            while (i>0 && strchr(sep_char, str[i-1]))
                i--;
            pos = i;
        }
    }

    return pos;
}


static bool checkLibName(std::vector<std::string> stdAbi, std::vector<std::string> primAbi)
{
    if (0 == primAbi.size()
            || 0 == stdAbi.size())
        return false;

    for (size_t i=0; i<primAbi.size(); i++) {
        size_t m = ~0;
        std::string primStr(primAbi[i]);

        m = passArchStr(primStr);
        // Not find arch string
        if (~0 == m || 0 == m)
            return false;

        std::string primSubStr = primStr.substr(0, m);

        size_t j = 0;
        for (j=0; j<stdAbi.size(); j++) {
            size_t n = ~0;
            std::string stdStr(stdAbi[j]);

            n = stdStr.find(primSubStr);
            if (0 == n)
                break;
        }

        // Find one mismatch lib
        if (j >= stdAbi.size())
            return false;
    }

    return true;
}


/*
 * Scan apk to figure out which abi libs should be used on intel platform
 * Return values:
 *  INSTALL_MISMATCH_ABI2_SUCCEEDED - lib mismatch and should use abi2 libs
 *  INSTALL_MISMATCH_UPGRADEABI_SUCCEEDED - lib mismatch and should use upgradeabi libs
 *  INSTALL_ABI_SUCCEEDED - should install abi lib
 *  INSTALL_ABI2_SUCCEEDED - should install abi2 libs
 *  INSTALL_UPGRADEABI_SUCCEEDED - should install upgrade abi libs
 *  INSTALL_SUCCEEDED - install java app
 *
 */
static jint
com_android_internal_content_NativeLibraryHelper_listNativeBinaries(JNIEnv *env, jclass clazz,
        jstring javaFilePath, jstring javaCpuAbi, jstring javaCpuAbi2,jstring javaUpgradeAbi)
{

    ScopedUtfChars filePath(env, javaFilePath);
    ScopedUtfChars cpuAbi(env, javaCpuAbi);
    ScopedUtfChars cpuAbi2(env, javaCpuAbi2);
    ScopedUtfChars upgradeAbi(env, javaUpgradeAbi);

    std::vector<std::string> cpuAbi_lib;
    std::vector<std::string> cpuAbi2_lib;
    std::vector<std::string> upgradeAbi_lib;

    ZipFileRO zipFile;

    if (filePath.c_str() == NULL) {
        ALOGE("APK file patch is NULLI\n");
        return INSTALL_FAILED_INVALID_APK;
    }

    if (zipFile.open(filePath.c_str()) != NO_ERROR) {
        ALOGI("Couldn't open APK %s\n", filePath.c_str());
        return INSTALL_FAILED_INVALID_APK;
    }

    const int N = zipFile.getNumEntries();

    char fileName[PATH_MAX];
    bool hasPrimaryAbi = false;
    bool hasSecondaryAbi = false;
    bool hasUpgradeAbi = false;
    bool hasX86Assert = false;
    bool noMatchAbi = false;
    int priAbiLib = 0;
    int secAbiLib = 0;
    int upgradeAbiLib = 0;

    for (int i = 0; i < N; i++) {
        const ZipEntryRO entry = zipFile.findEntryByIndex(i);
        if (entry == NULL) {
            continue;
        }

        // Make sure this entry has a filename.
        if (zipFile.getEntryFileName(entry, fileName, sizeof(fileName))) {
            continue;
        }

        // Make sure we're in the assets directory of the ZIP.
        if (!strncmp(fileName, APK_ASSETS, APK_ASSETS_LEN)) {
            // Make sure the filename is at least to the minimum library name size.
            const size_t fileNameLen = strlen(fileName);
            static const size_t minLength = APK_ASSETS_LEN + 2 + LIB_PREFIX_LEN + 1 + LIB_SUFFIX_LEN;
            if (fileNameLen < minLength) {
                continue;
            }

            const char* lastSlash = strrchr(fileName, '/');
            ALOG_ASSERT(lastSlash != NULL, "last slash was null somehow for %s\n", fileName);
            if (lastSlash == NULL) {
                ALOGE("last slash was null\n");
                continue;
            }

            // Make sure it's in the root of assets folder.
            if ((lastSlash - fileName) != (APK_ASSETS_LEN - 1))
                continue;

            if (strncmp(fileName + fileNameLen - LIB_SUFFIX_LEN, LIB_SUFFIX, LIB_SUFFIX_LEN)
                    || strncmp(lastSlash, LIB_PREFIX, LIB_PREFIX_LEN)
                    || !isFilenameSafe(lastSlash + 1))
                continue;

            if (false == lib_list_inited) {
                init_lib_list();
                lib_list_inited = true;
            }

            for (size_t j = 0; j < lib_list.size(); ++j) {
                if (!strncmp(lastSlash + LIB_PREFIX_LEN, lib_list[j].c_str(), lib_list[j].length())) {
                    //find x86 assert lib
                    hasX86Assert = true;
                    continue;
                }
            }
        }

        // Make sure we're in the lib directory of the ZIP.
        if (strncmp(fileName, APK_LIB, APK_LIB_LEN)) {
            continue;
        }

        // Make sure the filename is at least to the minimum library name size.
        const size_t fileNameLen = strlen(fileName);
        static const size_t minLength = APK_LIB_LEN + 2 + LIB_PREFIX_LEN + 1 + LIB_SUFFIX_LEN;
        if (fileNameLen < minLength) {
            continue;
        }

        const char* lastSlash = strrchr(fileName, '/');
        ALOG_ASSERT(lastSlash != NULL, "last slash was null somehow for %s\n", fileName);

        // Check to make sure the CPU ABI of this file is one we support.
        const char* cpuAbiOffset = fileName + APK_LIB_LEN;
        const size_t cpuAbiRegionSize = lastSlash - cpuAbiOffset;
        const char* libName = lastSlash + 1;

        ALOGV("Comparing ABIs %s and %s versus %s\n", cpuAbi.c_str(), cpuAbi2.c_str(), cpuAbiOffset);
        if (cpuAbi.size() == cpuAbiRegionSize
                && *(cpuAbiOffset + cpuAbi.size()) == '/'
                && !strncmp(cpuAbiOffset, cpuAbi.c_str(), cpuAbiRegionSize)) {
            ALOGV("Finding primary ABI %s\n", cpuAbi.c_str());
            hasPrimaryAbi = true;
            priAbiLib++;
            for (int j=0; intel_arch[j] != NULL; j++) {
                if ((libName != NULL) && (strstr(libName, intel_arch[j]) != NULL)) {
                    cpuAbi_lib.push_back(libName);
                    break;
                }
            }
        } else if (cpuAbi2.size() == cpuAbiRegionSize
                && *(cpuAbiOffset + cpuAbi2.size()) == '/'
                && !strncmp(cpuAbiOffset, cpuAbi2.c_str(), cpuAbiRegionSize)) {
                    ALOGV("Finding secondary ABI %s\n", cpuAbi2.c_str());
                    hasSecondaryAbi = true;
                    secAbiLib++;
                    cpuAbi2_lib.push_back(libName);
        } else if (upgradeAbi.size() == cpuAbiRegionSize
                && *(cpuAbiOffset + upgradeAbi.size()) == '/'
                && !strncmp(cpuAbiOffset, upgradeAbi.c_str(), cpuAbiRegionSize)) {
                    ALOGV("Finding upgrade ABI %s\n", upgradeAbi.c_str());
                    hasUpgradeAbi = true;
                    upgradeAbiLib++;
                    upgradeAbi_lib.push_back(libName);
        } else {
                ALOGV("abi didn't match anything: %s (end at %zd)\n", cpuAbiOffset, cpuAbiRegionSize);
                noMatchAbi = true;
        }
    }

    if ((hasPrimaryAbi == false && hasSecondaryAbi == true && hasX86Assert == true)) {
        return INSTALL_IMPLICIT_ABI2_SUCCEEDED;
    }

    if ((hasPrimaryAbi == false && hasUpgradeAbi == true && hasX86Assert == true)) {
        return INSTALL_IMPLICIT_UPGRADEABI_SUCCEEDED;
    }

    if (hasPrimaryAbi == true && hasSecondaryAbi == true && priAbiLib != secAbiLib) {
        if (checkLibName(cpuAbi2_lib, cpuAbi_lib)) {
            return INSTALL_SUCCEEDED;
        }
        return INSTALL_MISMATCH_ABI2_SUCCEEDED;
    }

    if (hasPrimaryAbi == true && hasSecondaryAbi == false && hasUpgradeAbi == true && priAbiLib != upgradeAbiLib) {
        if (checkLibName(upgradeAbi_lib, cpuAbi_lib)) {
            return INSTALL_SUCCEEDED;
        }
        return INSTALL_MISMATCH_UPGRADEABI_SUCCEEDED;
    }

    if ((hasPrimaryAbi == true && hasSecondaryAbi == true && priAbiLib == secAbiLib) ||
            (hasPrimaryAbi == true && hasSecondaryAbi == false && hasUpgradeAbi == true &&
                    priAbiLib == upgradeAbiLib) || (hasPrimaryAbi == true &&
                            hasSecondaryAbi == false && hasUpgradeAbi == false)) {
        return INSTALL_ABI_SUCCEEDED;
    }

    if (hasPrimaryAbi == false && hasSecondaryAbi == false && hasUpgradeAbi == true) {
        return INSTALL_UPGRADEABI_SUCCEEDED;
    }

    if (hasPrimaryAbi == false && hasSecondaryAbi == true) {
        return INSTALL_ABI2_SUCCEEDED;
    }

    return INSTALL_SUCCEEDED;
}
#endif

static JNINativeMethod gMethods[] = {
    {"nativeCopyNativeBinaries",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I",
            (void *)com_android_internal_content_NativeLibraryHelper_copyNativeBinaries},
    {"nativeSumNativeBinaries",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)J",
            (void *)com_android_internal_content_NativeLibraryHelper_sumNativeBinaries},
#ifdef WITH_HOUDINI
    {"nativeListNativeBinaries",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I",
            (void *)com_android_internal_content_NativeLibraryHelper_listNativeBinaries},
#endif
};


int register_com_android_internal_content_NativeLibraryHelper(JNIEnv *env)
{
    return AndroidRuntime::registerNativeMethods(env,
                "com/android/internal/content/NativeLibraryHelper", gMethods, NELEM(gMethods));
}

};
