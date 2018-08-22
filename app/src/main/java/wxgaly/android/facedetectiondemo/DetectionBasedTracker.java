package wxgaly.android.facedetectiondemo;

import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;

public class DetectionBasedTracker
{

    public DetectionBasedTracker(String cascadeName, int minFaceSize) {
        mNativeObj = nativeCreateObject(cascadeName, minFaceSize);
    }

    public void start() {
        nativeStart(mNativeObj);
    }

    public void stop() {
        nativeStop(mNativeObj);
    }

    public void setMinFaceSize(int size) {
        nativeSetFaceSize(mNativeObj, size);
    }

    public void detect(Mat imageGray, MatOfRect faces) {
        nativeDetect(mNativeObj, imageGray.getNativeObjAddr(), faces.getNativeObjAddr());
    }

    public void release() {
        nativeDestroyObject(mNativeObj);
        mNativeObj = 0;
    }

    private long mNativeObj = 0;

    public native long nativeCreateObject(String cascadeName, int minFaceSize);
    public native void nativeDestroyObject(long thiz);
    public native void nativeStart(long thiz);
    public native void nativeStop(long thiz);
    public native void nativeSetFaceSize(long thiz, int size);
    public native void nativeDetect(long thiz, long inputImage, long faces);
}
