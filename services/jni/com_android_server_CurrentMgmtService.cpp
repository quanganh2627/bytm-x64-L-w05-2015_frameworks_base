/*
 * Copyright (C) 2009 The Android Open Source Project
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

#define LOG_TAG "CurrentMgmtService"

#include "jni.h"
#include "JNIHelp.h"
#include "android_runtime/AndroidRuntime.h"

#include <utils/misc.h>
#include <utils/Log.h>
#include <hardware/bcu.h>

#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <assert.h>

namespace android {

static struct bcu_module *gBcuModule;

static void nativeInit(JNIEnv* env, jobject obj) {

    ALOGE("BCU JNI init");
    assert(!gBcuModule);
    int err;

    err = hw_get_module(BCU_HARDWARE_MODULE_ID, (hw_module_t const**)&gBcuModule);

    if (!err) {
        gBcuModule->init(gBcuModule);
    } else {
        ALOGE("Couldn't load %s module (%s)", BCU_HARDWARE_MODULE_ID, strerror(-err));
    }
}

static int readFromFile(const char *path, char *buf, size_t size)
{
    if (!path)
        return -1;

    int fd = open(path, O_RDONLY, 0);
    if (fd < 0) {
        ALOGE("Could not open '%s'", path);
        return -1;
    }

    ssize_t count = read(fd, buf, size);
    if (count > 0) {
        while (count > 0 && buf[count-1] == '\n')
            count--;
        buf[count] = '\0';
    } else {
        buf[0] = '\0';
    }

    close (fd);
    return count;
}

static int writeToFile(const char *path, int val)
{
     const int SIZE = 20;
     int ret, fd, len;
     char value[SIZE];

     if (!path)
         return -1;

     fd = open(path, O_RDWR, 0);
     if (fd < 0) {
         ALOGE("Could not open '%s'", path);
         return -1;
     }

     len = snprintf(value, SIZE, "%d\n", val);
     ret = write(fd, value, len);

     close(fd);
     return (ret == len) ? 0 : -1;

}

static jint writeSysfs(JNIEnv* env, jobject obj, jstring jPath, jint jVal)
{
    int ret;
    const char *path = NULL;

    path = jPath ? env->GetStringUTFChars(jPath, NULL) : NULL;
    if (!path) {
        jniThrowNullPointerException(env, "path");
        return -EINVAL;
    }

    ret = writeToFile(path, jVal);
    env->ReleaseStringUTFChars(jPath, path);
    return ret;
}

static jstring readSysfs(JNIEnv* env, jobject obj, jstring jPath)
{
    const char *path = NULL;
    const int SIZE = 1024;
    char buf[SIZE];

    path = jPath ? env->GetStringUTFChars(jPath, NULL) : NULL;
    if (!path) {
        jniThrowNullPointerException(env, "path");
        return NULL;
    }

    if (readFromFile(path, buf, SIZE) > 0) {
        env->ReleaseStringUTFChars(jPath, path);
        return env->NewStringUTF(buf);
    } else {
        env->ReleaseStringUTFChars(jPath, path);
        return NULL;
    }
}

static void nativeSubsystemThrottle(JNIEnv *env, jobject clazz, jint subsystem, jint level)
{
    if (gBcuModule) {
        gBcuModule->setThrottleLevel((enum sub_system)subsystem, level);
    }
}

static JNINativeMethod method_table[] = {
    { "nativeInit", "()V", (void*) nativeInit },
    { "nativeSubsystemThrottle", "(II)V", (void*)nativeSubsystemThrottle },
    { "native_readSysfs", "(Ljava/lang/String;)Ljava/lang/String;", (void*)readSysfs},
    { "native_writeSysfs", "(Ljava/lang/String;I)I", (void*)writeSysfs},
};

#define FIND_CLASS(var, className) \
        var = env->FindClass(className); \
        LOG_FATAL_IF(! var, "Unable to find class " className);

int register_android_server_CurrentMgmtService(JNIEnv *env)
{
    int res;
    res = jniRegisterNativeMethods(env, "com/android/server/cms/CurrentMgmtService",
            method_table, NELEM(method_table));
    LOG_FATAL_IF(res < 0, "Unable to register native methods.");
    return 0;
}

} // namespace android
