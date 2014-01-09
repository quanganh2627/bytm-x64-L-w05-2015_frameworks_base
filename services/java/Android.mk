LOCAL_PATH:= $(call my-dir)

# the library
# ============================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
            $(call all-subdir-java-files) \
	    com/android/server/EventLogTags.logtags \
	    com/android/server/am/EventLogTags.logtags \
	    com/android/server/wifi/ICsmWifiOffloadSystemService.aidl

ifeq ($(strip $(INTEL_FEATURE_ARKHAM)),true)
LOCAL_SRC_FILES += $(call all-java-files-under,../../../../vendor/intel/PRIVATE/arkham/aosp/frameworks/enabled/base/services/)
else
LOCAL_SRC_FILES += $(call all-java-files-under,../../../../vendor/intel/arkham/frameworks/disabled/base/services/)
endif

LOCAL_STATIC_JAVA_LIBRARIES := CwsServiceMgr CsmClient

LOCAL_MODULE:= services

LOCAL_JAVA_LIBRARIES := android.policy conscrypt telephony-common com.intel.multidisplay com.intel.config

LOCAL_JAVA_LIBRARIES += com.intel.asf


include $(BUILD_JAVA_LIBRARY)

include $(BUILD_DROIDDOC)
