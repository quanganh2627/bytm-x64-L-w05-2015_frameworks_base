LOCAL_PATH:= $(call my-dir)

# the library
# ============================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
            $(call all-subdir-java-files) \
	    com/android/server/EventLogTags.logtags \
	    com/android/server/am/EventLogTags.logtags

LOCAL_MODULE:= services

LOCAL_JAVA_LIBRARIES := android.policy telephony-common

ifeq ($(TARGET_HAS_MULTIPLE_DISPLAY),true)
	LOCAL_STATIC_JAVA_LIBRARIES := displayobserver
else
	LOCAL_SRC_FILES += ../dummy/DisplayObserver.java
endif

include $(BUILD_JAVA_LIBRARY)

include $(BUILD_DROIDDOC)
