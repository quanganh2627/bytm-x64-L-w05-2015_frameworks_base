LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_JAVA_LIBRARIES := telephony-common \
                        com.intel.config

LOCAL_PACKAGE_NAME := SettingsProvider
LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true

ifeq ($(strip $(INTEL_FEATURE_ARKHAM)),true)
ARKHAM_DIR := vendor/intel/arkham/frameworks/enabled/base/packages/SettingsProvider
LOCAL_MODULE := $(LOCAL_PACKAGE_NAME)
LOCAL_MODULE_CLASS := APPS
intermediates := $(call local-intermediates-dir)
ARKHAM_MANIFEST := $(addprefix $(intermediates)/,AndroidManifest.xml)
LOCAL_FULL_MANIFEST_FILE := $(ARKHAM_MANIFEST)
LOCAL_GENERATED_SOURCES := $(ARKHAM_MANIFEST)
LOCAL_MODULE :=
LOCAL_MODULE_CLASS :=
MANIFEST_SOURCE := $(ANDROID_BUILD_TOP)/$(LOCAL_PATH)/AndroidManifest.xml
$(ARKHAM_MANIFEST) : PRIVATE_CUSTOM_TOOL := sed  -f $(ARKHAM_DIR)/AndroidManifest.sed $(MANIFEST_SOURCE) > $(ARKHAM_MANIFEST)
$(ARKHAM_MANIFEST) : PRIVATE_TOP := $(ANDROID_BUILD_TOP)
$(ARKHAM_MANIFEST) : $(ARKHAM_DIR)/AndroidManifest.sed $(MANIFEST_SOURCE)
	$(transform-generated-source)
LOCAL_SRC_FILES += \
        $(call find-other-java-files,../../../../$(ARKHAM_DIR)/src,)
endif

include $(BUILD_PACKAGE)

########################
include $(call all-makefiles-under,$(LOCAL_PATH))
