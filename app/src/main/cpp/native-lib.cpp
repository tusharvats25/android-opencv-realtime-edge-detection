\
    #include <jni.h>
    #include <android/log.h>
    #include <opencv2/opencv.hpp>

    #define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "native-lib", __VA_ARGS__)
    #define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "native-lib", __VA_ARGS__)

    extern "C"
    JNIEXPORT jbyteArray JNICALL
    Java_com_example_app_NativeBridge_nativeProcessFrame(JNIEnv *env, jclass clazz, jbyteArray nv21Data, jint width, jint height) {
        if (nv21Data == nullptr) return nullptr;
        jbyte* nv21 = env->GetByteArrayElements(nv21Data, NULL);

        int w = width;
        int h = height;
        int frameSize = w * h;
        // create Mat from NV21 data
        cv::Mat yuv(h + h/2, w, CV_8UC1, (unsigned char*)nv21);
        cv::Mat bgr;
        cv::cvtColor(yuv, bgr, cv::COLOR_YUV2BGR_NV21);
        cv::Mat gray;
        cv::cvtColor(bgr, gray, cv::COLOR_BGR2GRAY);
        cv::Mat edges;
        cv::Canny(gray, edges, 50, 150);
        cv::Mat rgba;
        cv::cvtColor(edges, rgba, cv::COLOR_GRAY2RGBA);

        int sz = rgba.total() * rgba.elemSize();
        jbyteArray out = env->NewByteArray(sz);
        env->SetByteArrayRegion(out, 0, sz, (jbyte*)rgba.data);
        env->ReleaseByteArrayElements(nv21Data, nv21, JNI_ABORT);
        return out;
    }
