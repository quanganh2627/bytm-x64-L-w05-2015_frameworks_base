LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	system_init.cpp

base = $(LOCAL_PATH)/../../..
native = $(LOCAL_PATH)/../../../../native

LOCAL_C_INCLUDES := \
	$(native)/services/sensorservice \
	$(native)/services/surfaceflinger \
	$(JNI_H_INCLUDE)

LOCAL_SHARED_LIBRARIES := \
	libandroid_runtime \
	libsensorservice \
	libsurfaceflinger \
    libinput \
	libutils \
	libbinder \
	libcutils

ifeq ($(TARGET_HAS_MULTIPLE_DISPLAY),true)
    LOCAL_C_INCLUDES += $(TARGET_OUT_HEADERS)/display
    LOCAL_SHARED_LIBRARIES += libmultidisplay
    LOCAL_CFLAGS += -DTARGET_HAS_MULTIPLE_DISPLAY
endif

LOCAL_MODULE:= libsystem_server

include $(BUILD_SHARED_LIBRARY)
