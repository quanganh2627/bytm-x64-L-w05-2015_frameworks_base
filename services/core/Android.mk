LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := services.core

LOCAL_SRC_FILES += \
    $(call all-java-files-under,java) \
    java/com/android/server/EventLogTags.logtags \
    java/com/android/server/am/EventLogTags.logtags

LOCAL_JAVA_LIBRARIES += android.policy telephony-common com.intel.config

LOCAL_STATIC_JAVA_LIBRARIES += CwsServiceMgr CsmClient com.intel.aa \
    CwsCellularCoexMgrService

include $(BUILD_STATIC_JAVA_LIBRARY)
