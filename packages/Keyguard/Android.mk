# Copyright (C) 2013 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src) $(call all-subdir-Iaidl-files)

ifeq ($(strip $(INTEL_FEATURE_ARKHAM)),true)
LOCAL_SRC_FILES += $(call all-java-files-under, ../../../../vendor/intel/arkham/frameworks/enabled/base/packages/Keyguard/src)
else
LOCAL_SRC_FILES += $(call all-java-files-under, ../../../../vendor/intel/arkham/frameworks/disabled/base/packages/Keyguard/src)
endif

LOCAL_JAVA_LIBRARIES := services com.intel.config


ifeq ($(strip $(INTEL_FEATURE_LPAL)),true)
LOCAL_SRC_FILES += $(call all-java-files-under, ../../../../vendor/intel/PRIVATE/lpal/frameworks/enabled/base/packages/Keyguard/src)
LOCAL_STATIC_JAVA_LIBRARIES := vtsvmanager
else
LOCAL_SRC_FILES += $(call all-java-files-under, lpal/)
endif


LOCAL_PACKAGE_NAME := Keyguard

LOCAL_CERTIFICATE := platform

LOCAL_PRIVILEGED_MODULE := true

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_PACKAGE)

#include $(call all-makefiles-under,$(LOCAL_PATH))
