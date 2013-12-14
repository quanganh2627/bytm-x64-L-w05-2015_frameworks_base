LOCAL_PATH:= $(call my-dir)

# the library
# ============================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

ifeq ($(strip $(INTEL_FEATURE_ARKHAM)),true)
LOCAL_SRC_FILES += $(call all-java-files-under, ../../../vendor/intel/arkham/frameworks/enabled/base/policy/src/)
else
LOCAL_SRC_FILES += $(call all-java-files-under, ../../../vendor/intel/arkham/frameworks/disabled/base/policy/src/)
endif
LOCAL_JAVA_LIBRARIES := com.intel.config
            
LOCAL_MODULE := android.policy

include $(BUILD_JAVA_LIBRARY)

# additionally, build unit tests in a separate .apk
include $(call all-makefiles-under,$(LOCAL_PATH))
