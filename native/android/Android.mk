BASE_PATH := $(call my-dir)
LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

# our source files
#
LOCAL_SRC_FILES:= \
    asset_manager.cpp \
    configuration.cpp \
    input.cpp \
    looper.cpp \
    native_activity.cpp \
    native_window.cpp \
    obb.cpp \
    sensor.cpp \
    storage_manager.cpp

LOCAL_SHARED_LIBRARIES := \
    liblog \
    libcutils \
    libandroidfw \
    libinput \
    libutils \
    libbinder \
    libui \
    libgui \
    libandroid_runtime

LOCAL_STATIC_LIBRARIES := \
    libstorage

LOCAL_C_INCLUDES += \
    frameworks/base/native/include \
    frameworks/base/core/jni/android

ifeq ($(strip $(INTEL_FEATURE_ASF))), true)
ifneq ($(PLATFORM_ASF_VERSION), 1)
    LOCAL_SHARED_LIBRARIES += libsecurityfileservice
    LOCAL_C_INCLUDES += $(TARGET_OUT_HEADERS)/libsecurityfileservice/include
endif
endif

LOCAL_MODULE:= libandroid

include $(BUILD_SHARED_LIBRARY)
