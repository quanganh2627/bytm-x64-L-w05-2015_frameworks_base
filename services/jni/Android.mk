LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

ifeq ($(strip $(INTEL_FEATURE_ASF)),true)
    LOCAL_CPPFLAGS += -DPLATFORM_ASF_VERSION=$(PLATFORM_ASF_VERSION)
else
    LOCAL_CPPFLAGS += -DPLATFORM_ASF_VERSION=0
endif

LOCAL_SRC_FILES:= \
    com_android_server_AlarmManagerService.cpp \
    com_android_server_AssetAtlasService.cpp \
    com_android_server_ConsumerIrService.cpp \
    com_android_server_input_InputApplicationHandle.cpp \
    com_android_server_input_InputManagerService.cpp \
    com_android_server_input_InputWindowHandle.cpp \
    com_android_server_LightsService.cpp \
    com_android_server_power_PowerManagerService.cpp \
    com_android_server_SerialService.cpp \
    com_android_server_SystemServer.cpp \
    com_android_server_UsbDeviceManager.cpp \
    com_android_server_UsbHostManager.cpp \
    com_android_server_VibratorService.cpp \
    com_android_server_CurrentMgmtService.cpp \
    com_android_server_location_GpsLocationProvider.cpp \
    com_android_server_location_FlpHardwareProvider.cpp \
    com_android_server_connectivity_Vpn.cpp \
    com_android_server_thermal_ThermalManager.cpp\
    onload.cpp

LOCAL_C_INCLUDES += \
    $(JNI_H_INCLUDE) \
    frameworks/base/services \
    frameworks/base/core/jni \
    frameworks/native/services \
    external/skia/include/core \
    libcore/include \
    libcore/include/libsuspend \
	$(call include-path-for, libhardware)/hardware \
	$(call include-path-for, libhardware_legacy)/hardware_legacy \

LOCAL_SHARED_LIBRARIES := \
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
    libinputservice \
    libsensorservice \
    libskia \
    libgui \
    libusbhost \
    libsuspend \
    libEGL \
    libGLESv2

ifeq ($(strip $(INTEL_FEATURE_ASF)),true)
ifneq ($(strip $(PLATFORM_ASF_VERSION)),1)
ifneq ($(strip $(PLATFORM_ASF_VERSION)),0)
    LOCAL_SHARED_LIBRARIES += libsecuritydeviceserviceclient
    LOCAL_C_INCLUDES += $(TARGET_OUT_HEADERS)/libsecuritydeviceserviceclient
endif
endif
endif

LOCAL_CFLAGS += -DEGL_EGLEXT_PROTOTYPES -DGL_GLEXT_PROTOTYPES

ifeq ($(TARGET_HAS_MULTIPLE_DISPLAY),true)
ifeq ($(USE_MDS_LEGACY),true)
    LOCAL_CFLAGS += -DUSE_MDS_LEGACY
endif
    LOCAL_SHARED_LIBRARIES += \
        libmultidisplay \
        libbinder \
        libmultidisplayjni
    LOCAL_CFLAGS += -DTARGET_HAS_MULTIPLE_DISPLAY
endif

ifeq ($(WITH_MALLOC_LEAK_CHECK),true)
    LOCAL_CFLAGS += -DMALLOC_LEAK_CHECK
endif

LOCAL_MODULE:= libandroid_servers

include $(BUILD_SHARED_LIBRARY)
