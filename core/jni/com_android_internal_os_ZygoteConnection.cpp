#define LOG_TAG "Zygote"

#include "jni.h"
#include <JNIHelp.h>
#include "android_runtime/AndroidRuntime.h"
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <dlfcn.h>
#include "cutils/properties.h"

namespace android {

// re-define Field and StaticField struct here
struct F {
    void *clz;
    char *name;
    char *sig;
    int flags;
};

struct sF : F {
    void *p;
};

static void com_android_internal_os_ZygoteConnection_settingHoudiniABI (JNIEnv *env, jobject clazz)
{
    jclass c = env->FindClass("android/os/Build");
    jfieldID f1 = env->GetStaticFieldID(c, "CPU_ABI", "Ljava/lang/String;");
    jfieldID f2 = env->GetStaticFieldID(c, "CPU_ABI2", "Ljava/lang/String;");
    jfieldID f3 = env->GetStaticFieldID(c, "HOUDINI_ABI", "Ljava/lang/String;");
    jfieldID f4 = env->GetStaticFieldID(c, "HOUDINI_ABI2", "Ljava/lang/String;");

    struct sF* abi = (struct sF*)f1;
    struct sF* abi2 = (struct sF*)f2;
    struct sF* houdini_abi = (struct sF*)f3;
    struct sF* houdini_abi2 = (struct sF*)f4;
    abi->p = houdini_abi->p;
    abi2->p = houdini_abi2->p;

    return;
}

#define APP_WITH_ABI2               "/data/data/.appwithABI2"
#define APP_WITH_IMPLICIT_ABI       "/data/.appwithImplicitABI"
#define APP_ABI2_FLAG               1
#define APP_IMPLICIT_ABI_FLAG       2

static jint com_android_internal_os_ZygoteConnection_isABI2App (JNIEnv *env, jobject clazz, jint appId)
{
    int app_abi_flag = 0;
    int fd = open(APP_WITH_ABI2, O_RDONLY,0444);
    if (fd != -1) {
        int pkgAppId = 0;
        while (read(fd, &pkgAppId, 4) > 0) {
            if (appId == pkgAppId) {
                app_abi_flag |= APP_ABI2_FLAG;
                break;
            }
        }
        close(fd);
    }

    if (!(app_abi_flag & APP_ABI2_FLAG))
        return (jint)app_abi_flag;

    fd = open(APP_WITH_IMPLICIT_ABI, O_RDONLY,0444);
    if (fd != -1) {
        int pkgAppId = 0;
        while (read(fd, &pkgAppId, 4) > 0) {
            if (appId == pkgAppId) {
                app_abi_flag |= APP_IMPLICIT_ABI_FLAG;
                break;
            }
        }
        close(fd);
    }

    return (jint)app_abi_flag;
}

#ifdef WITH_HOUDINI
extern void *houdini_handler;

static void com_android_internal_os_ZygoteConnection_unloadHoudini ()
{
    if (houdini_handler)
        dlclose(houdini_handler);
}
#endif

/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    { "settingHoudiniABI", "()V",
        (void *) com_android_internal_os_ZygoteConnection_settingHoudiniABI },
    { "isABI2App", "(I)I",
        (void *) com_android_internal_os_ZygoteConnection_isABI2App },
#ifdef WITH_HOUDINI
    { "unloadHoudini", "()V",
        (void *) com_android_internal_os_ZygoteConnection_unloadHoudini },
#endif
};

int register_com_android_internal_os_ZygoteConnection(JNIEnv* env)
{
    return AndroidRuntime::registerNativeMethods(env,
            "com/android/internal/os/ZygoteConnection", gMethods, NELEM(gMethods));
}

}; // namespace android
