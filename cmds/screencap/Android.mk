LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	screencap.cpp

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libutils \
	libbinder \
	libskia \
    libui \
    libgui

ifeq ($(strip $(INTEL_FEATURE_ASF)),true)
    LOCAL_CPPFLAGS += -DINTEL_FEATURE_ASF
    LOCAL_CPPFLAGS += -DPLATFORM_ASF_VERSION=$(PLATFORM_ASF_VERSION)
    LOCAL_C_INCLUDES += $(TARGET_OUT_HEADERS)/asfaosp
ifneq ($(strip $(PLATFORM_ASF_VERSION)),1)
ifneq ($(strip $(PLATFORM_ASF_VERSION)),0)
    LOCAL_SHARED_LIBRARIES += libsecuritydeviceserviceclient
    LOCAL_C_INCLUDES += $(TARGET_OUT_HEADERS)/libsecuritydeviceserviceclient
endif
endif
else
   LOCAL_CPPFLAGS += -DPLATFORM_ASF_VERSION=0
endif

LOCAL_MODULE:= screencap

LOCAL_MODULE_TAGS := optional

include $(BUILD_EXECUTABLE)
