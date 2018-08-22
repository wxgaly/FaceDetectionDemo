LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

OPENCV_INSTALL_MODULES:=off
OPENCV_LIB_TYPE:=SHARED
ifeq ("$(wildcard $(OPENCV_MK_PATH))","")
include ../../../../native/jni/OpenCV.mk
else
include $(OPENCV_MK_PATH)
endif

LOCAL_SRC_FILES  := DetectionBasedTracker_jni.cpp
LOCAL_C_INCLUDES += $(LOCAL_PATH)
LOCAL_LDLIBS     += -llog -ldl

LOCAL_MODULE     := detection_based_tracker

include $(BUILD_SHARED_LIBRARY)
