LOCAL_PATH:= $(call my-dir)

# the library
# ============================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

ifeq ($(strip $(INTEL_FEATURE_ARKHAM)),true)
LOCAL_ARKHAM_PATH := vendor/intel/PRIVATE/arkham/aosp
ifneq ($(LOCAL_ARKHAM_PATH)/frameworks/enabled/base/policy/src, $(wildcard $(LOCAL_ARKHAM_PATH)/frameworks/enabled/base/policy/src))
$(info Building with arkham-policy prebuilt lib)
LOCAL_STATIC_JAVA_LIBRARIES := arkham-policy
else
$(info Building with arkham-policy source code)
LOCAL_SRC_FILES += $(call all-java-files-under, ../../../$(LOCAL_ARKHAM_PATH)/frameworks/enabled/base/policy/src/)
endif
else
LOCAL_ARKHAM_PATH := vendor/intel/arkham
LOCAL_SRC_FILES += $(call all-java-files-under, ../../../$(LOCAL_ARKHAM_PATH)/frameworks/disabled/base/policy/src/)
endif
LOCAL_JAVA_LIBRARIES := com.intel.config

LOCAL_MODULE := android.policy

include $(BUILD_JAVA_LIBRARY)

# ============================================================
#generate static java lib for arkham external build.
ifeq ($(strip $(INTEL_FEATURE_ARKHAM)),true)
ifeq ($(LOCAL_ARKHAM_PATH)/frameworks/enabled/base/policy/src, $(wildcard $(LOCAL_ARKHAM_PATH)/frameworks/enabled/base/policy/src))
backup_list:= LOCAL_SRC_FILES LOCAL_JAVA_LIBRARIES LOCAL_STATIC_JAVA_LIBRARIES
$(foreach item, $(backup_list), $(eval backup_$(item):=$($(item))))

include $(CLEAR_VARS)
$(foreach item, $(backup_list), $(eval $(item):=$(backup_$(item))))

LOCAL_MODULE:=android.policy_static

include $(BUILD_STATIC_JAVA_LIBRARY)
droid: $(LOCAL_INTERMEDIATE_TARGETS)
endif
endif

# additionally, build unit tests in a separate .apk
include $(call all-makefiles-under,$(LOCAL_PATH))
