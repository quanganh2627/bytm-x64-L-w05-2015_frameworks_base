LOCAL_PATH:= $(call my-dir)

#include $(CLEAR_VARS)
#LOCAL_SRC_FILES := bctest.c binder.c
#LOCAL_MODULE := bctest
#include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)

LOCAL_CFLAGS    += -DOLA_FACEDETECTION

LOCAL_SHARED_LIBRARIES := liblog
LOCAL_SRC_FILES := service_manager.c binder.c
LOCAL_MODULE := servicemanager

ifeq ($(TARGET_BUILD_VARIANT), $(filter $(TARGET_BUILD_VARIANT), eng userdebug))
ifeq ($(INTEL_WIDI), true)
    LOCAL_CFLAGS += -DINTEL_WIDI=1
endif
endif

include $(BUILD_EXECUTABLE)
