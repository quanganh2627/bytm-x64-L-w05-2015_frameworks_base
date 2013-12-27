#define LOG_TAG "Zygote"

#include "jni.h"
#include <JNIHelp.h>
#include "android_runtime/AndroidRuntime.h"
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
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

    LOGD("Finished Houdini ABI setting.\n");

    return;
}

#define APP_WITH_ABI2      "/data/data/.appwithABI2"

static jboolean com_android_internal_os_ZygoteConnection_isABI2App (JNIEnv *env, jobject clazz, jint uid)
{
    int pkg_ABI2 = open(APP_WITH_ABI2, O_RDONLY,0444);
    if (pkg_ABI2 != -1) {
        int pkguid = 0;
        while (read(pkg_ABI2, &pkguid, 4) > 0) {
            if (uid == pkguid) {
                return true;
            }
        }
    }
    return false;
}

/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    { "settingHoudiniABI", "()V",
        (void *) com_android_internal_os_ZygoteConnection_settingHoudiniABI },
    { "isABI2App", "(I)Z",
        (void *) com_android_internal_os_ZygoteConnection_isABI2App },
};

int register_com_android_internal_os_ZygoteConnection(JNIEnv* env)
{
    return AndroidRuntime::registerNativeMethods(env,
            "com/android/internal/os/ZygoteConnection", gMethods, NELEM(gMethods));
}

}; // namespace android
