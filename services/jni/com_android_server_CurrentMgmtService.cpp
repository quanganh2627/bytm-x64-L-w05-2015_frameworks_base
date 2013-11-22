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

#include <stdio.h>
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


static void nativeSubsystemThrottle(JNIEnv *env, jobject clazz, jint subsystem, jint level)
{
    if (gBcuModule) {
        gBcuModule->setThrottleLevel((enum sub_system)subsystem, level);
    }
}

static JNINativeMethod method_table[] = {
    { "nativeInit", "()V", (void*) nativeInit },
    { "nativeSubsystemThrottle", "(II)V", (void*)nativeSubsystemThrottle },
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
