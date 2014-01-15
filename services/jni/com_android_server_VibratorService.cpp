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

#define LOG_TAG "VibratorService"

#include "jni.h"
#include "JNIHelp.h"
#include "android_runtime/AndroidRuntime.h"

#include <utils/misc.h>
#include <utils/Log.h>
#include <hardware/vibrator.h>

#include <stdio.h>
#include <assert.h>

namespace android {

static struct vibrator_module *gVibraModule;

static void vibratorControlInit()
{
    assert(!gVibraModule);
    int err;

    err = hw_get_module(VIBRATOR_HARDWARE_MODULE_ID, (hw_module_t const**)&gVibraModule);

    if (err) {
      ALOGE("Couldn't load %s module (%s)", VIBRATOR_HARDWARE_MODULE_ID, strerror(-err));
    }
}

static jboolean vibratorExists(JNIEnv *env, jobject clazz)
{
    return (gVibraModule && gVibraModule->vibrator_exists() > 0) ? JNI_TRUE : JNI_FALSE;
}

static void vibratorOn(JNIEnv *env, jobject clazz, jlong timeout_ms)
{
    if (gVibraModule) {
        gVibraModule->vibrator_on(timeout_ms);
    }
}

static void vibratorOff(JNIEnv *env, jobject clazz)
{
    if (gVibraModule) {
        gVibraModule->vibrator_off();
    }
}

static jlong getVibratorMinTimeout(JNIEnv *env, jobject clazz)
{
    if (gVibraModule) {
         return (jlong)gVibraModule->get_vibrator_min_timeout();
    }
    return 0;
}

static JNINativeMethod method_table[] = {
    { "vibratorExists", "()Z", (void*)vibratorExists },
    { "vibratorOn", "(J)V", (void*)vibratorOn },
    { "vibratorOff", "()V", (void*)vibratorOff },
    { "getVibratorMinTimeout", "()J", (void*)getVibratorMinTimeout },
};

int register_android_server_VibratorService(JNIEnv *env)
{
    // Load vibrator hardware module
    vibratorControlInit();

    return jniRegisterNativeMethods(env, "com/android/server/VibratorService",
            method_table, NELEM(method_table));
}

}; // namespace android
