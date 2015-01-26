# This file is included by the top level services directory to collect source
# files
LOCAL_REL_DIR := core/jni

ifeq ($(strip $(INTEL_FEATURE_ASF)),true)
    LOCAL_CPPFLAGS += -DINTEL_FEATURE_ASF
    LOCAL_CPPFLAGS += -DPLATFORM_ASF_VERSION=$(PLATFORM_ASF_VERSION)
else
    LOCAL_CPPFLAGS += -DPLATFORM_ASF_VERSION=0
endif

LOCAL_CFLAGS += -Wno-unused-parameter

LOCAL_SRC_FILES += \
    $(LOCAL_REL_DIR)/com_android_server_AlarmManagerService.cpp \
    $(LOCAL_REL_DIR)/com_android_server_am_BatteryStatsService.cpp \
    $(LOCAL_REL_DIR)/com_android_server_AssetAtlasService.cpp \
    $(LOCAL_REL_DIR)/com_android_server_connectivity_Vpn.cpp \
    $(LOCAL_REL_DIR)/com_android_server_ConsumerIrService.cpp \
    $(LOCAL_REL_DIR)/com_android_server_hdmi_HdmiCecController.cpp \
    $(LOCAL_REL_DIR)/com_android_server_input_InputApplicationHandle.cpp \
    $(LOCAL_REL_DIR)/com_android_server_input_InputManagerService.cpp \
    $(LOCAL_REL_DIR)/com_android_server_input_InputWindowHandle.cpp \
    $(LOCAL_REL_DIR)/com_android_server_lights_LightsService.cpp \
    $(LOCAL_REL_DIR)/com_android_server_location_GpsLocationProvider.cpp \
    $(LOCAL_REL_DIR)/com_android_server_location_FlpHardwareProvider.cpp \
    $(LOCAL_REL_DIR)/com_android_server_power_PowerManagerService.cpp \
    $(LOCAL_REL_DIR)/com_android_server_SerialService.cpp \
    $(LOCAL_REL_DIR)/com_android_server_SystemServer.cpp \
    $(LOCAL_REL_DIR)/com_android_server_tv_TvInputHal.cpp \
    $(LOCAL_REL_DIR)/com_android_server_UsbDeviceManager.cpp \
    $(LOCAL_REL_DIR)/com_android_server_UsbHostManager.cpp \
    $(LOCAL_REL_DIR)/com_android_server_VibratorService.cpp \
    $(LOCAL_REL_DIR)/com_android_server_PersistentDataBlockService.cpp \
    $(LOCAL_REL_DIR)/onload.cpp

include external/stlport/libstlport.mk

LOCAL_C_INCLUDES += \
    $(JNI_H_INCLUDE) \
    frameworks/base/services \
    frameworks/base/libs \
    frameworks/base/core/jni \
    frameworks/native/services \
    libcore/include \
    libcore/include/libsuspend \
	$(call include-path-for, libhardware)/hardware \
	$(call include-path-for, libhardware_legacy)/hardware_legacy \

LOCAL_SHARED_LIBRARIES += \
    libandroid_runtime \
    libandroidfw \
    libbinder \
    libcutils \
    liblog \
    libhardware \
    libhardware_legacy \
    libnativehelper \
    libutils \
    libui \
    libinput \
    libinputflinger \
    libinputservice \
    libsensorservice \
    libskia \
    libgui \
    libusbhost \
    libsuspend \
    libEGL \
    libGLESv2 \
    libnetutils \

ifeq ($(TARGET_HAS_MULTIPLE_DISPLAY),true)
    LOCAL_SHARED_LIBRARIES += \
        libmultidisplay \
        libbinder \
        libmultidisplayjni
    LOCAL_CFLAGS += -DTARGET_HAS_MULTIPLE_DISPLAY
endif
ifeq ($(strip $(INTEL_FEATURE_ASF)),true)
    LOCAL_C_INCLUDES += $(TARGET_OUT_HEADERS)/asfaosp
ifneq ($(strip $(PLATFORM_ASF_VERSION)),1)
ifneq ($(strip $(PLATFORM_ASF_VERSION)),0)
    LOCAL_SHARED_LIBRARIES += libsecuritydeviceserviceclient
    LOCAL_C_INCLUDES += $(TARGET_OUT_HEADERS)/libsecuritydeviceserviceclient
endif
endif
endif

