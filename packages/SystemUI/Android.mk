LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src) \
    src/com/android/systemui/EventLogTags.logtags

ifeq ($(strip $(INTEL_FEATURE_ARKHAM)),true)
LOCAL_SRC_FILES += \
        $(call find-other-java-files, ../../../../vendor/intel/arkham/frameworks/enabled/base/packages/SystemUI/src/,)
else
LOCAL_SRC_FILES += \
        $(call find-other-java-files, ../../../../vendor/intel/arkham/frameworks/disabled/base/packages/SystemUI/src/,)
endif

LOCAL_JAVA_LIBRARIES := telephony-common \
                        com.intel.config

LOCAL_PACKAGE_NAME := SystemUI
LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))
