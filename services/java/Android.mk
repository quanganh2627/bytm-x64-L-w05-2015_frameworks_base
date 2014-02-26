LOCAL_PATH:= $(call my-dir)

# the library
# ============================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
            $(call all-subdir-java-files) \
	    com/android/server/EventLogTags.logtags \
	    com/android/server/am/EventLogTags.logtags \
	    com/android/server/wifi/ICsmWifiOffloadSystemService.aidl

LOCAL_STATIC_JAVA_LIBRARIES := CwsServiceMgr CsmClient
ifeq ($(strip $(INTEL_FEATURE_ARKHAM)),true)
LOCAL_ARKHAM_PATH := vendor/intel/PRIVATE/arkham/aosp
ifneq ($(LOCAL_ARKHAM_PATH)/frameworks/enabled/base/services, $(wildcard $(LOCAL_ARKHAM_PATH)/frameworks/enabled/base/services))
$(info Building with arkham-services prebuilt lib)
LOCAL_STATIC_JAVA_LIBRARIES += arkham-services
else
$(info Building with arkham-services source code)
LOCAL_SRC_FILES += $(call all-java-files-under,../../../../$(LOCAL_ARKHAM_PATH)/frameworks/enabled/base/services/)
endif
else
LOCAL_ARKHAM_PATH := vendor/intel/arkham
LOCAL_SRC_FILES += $(call all-java-files-under,../../../../$(LOCAL_ARKHAM_PATH)/frameworks/disabled/base/services/)
LOCAL_SRC_FILES += $(call all-java-files-under,../../../../vendor/intel/arkham/frameworks/disabled/base/core/)
endif


LOCAL_MODULE:= services

LOCAL_JAVA_LIBRARIES += android.policy conscrypt telephony-common com.intel.multidisplay com.intel.config

LOCAL_JAVA_LIBRARIES += com.intel.asf


include $(BUILD_JAVA_LIBRARY)

include $(BUILD_DROIDDOC)

# ============================================================
#generate static java lib for arkham external build.
ifeq ($(strip $(INTEL_FEATURE_ARKHAM)),true)
ifeq ($(LOCAL_ARKHAM_PATH)/frameworks/enabled/base/services, $(wildcard $(LOCAL_ARKHAM_PATH)/frameworks/enabled/base/services))

backup_list:= LOCAL_SRC_FILES LOCAL_JAVA_LIBRARIES LOCAL_STATIC_JAVA_LIBRARIES
$(foreach item, $(backup_list), $(eval backup_$(item):=$($(item))))

include $(CLEAR_VARS)
$(foreach item, $(backup_list), $(eval $(item):=$(backup_$(item))))

LOCAL_MODULE:=services_static

include $(BUILD_STATIC_JAVA_LIBRARY)
droid: $(LOCAL_INTERMEDIATE_TARGETS)
endif
endif
