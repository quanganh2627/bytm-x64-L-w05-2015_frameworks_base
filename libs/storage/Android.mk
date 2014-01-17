LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	IMountServiceListener.cpp \
	IMountShutdownObserver.cpp \
	IObbActionListener.cpp \
	IMountService.cpp

ifeq ($(strip $(INTEL_FEATURE_ASF))), true)
ifneq ($(PLATFORM_ASF_VERSION), 1)
        LOCAL_C_INCLUDES := $(TARGET_OUT_HEADERS)/libsecurityfileservice/include
        LOCAL_SHARED_LIBRARIES := libsecurityfileservice libstlport
        LOCAL_CPPFLAGS += -DPLATFORM_ASF_VERSION=$(PLATFORM_ASF_VERSION)
        include external/stlport/libstlport.mk
endif
endif

LOCAL_MODULE:= libstorage
include $(BUILD_STATIC_LIBRARY)
