# CameraOpenGLApp - Project Notes

This project is a full Android Studio project skeleton demonstrating:
- Camera2 capture (YUV_420_888) -> NV21 conversion
- JNI native processing (OpenCV): NV21 -> BGR -> Gray -> Canny -> RGBA
- OpenGL ES 2.0 renderer that uploads RGBA texture per frame
- Save processed frame to Downloads/processed_sample.png

IMPORTANT:
- You must install Android NDK and CMake via SDK Manager.
- This project references OpenCV in native code. To build with OpenCV:
  1. Download OpenCV Android SDK from https://opencv.org/releases/
  2. Copy the `sdk/native/jni/include` and libs into app/src/main/jniLibs and adjust CMakeLists.txt
  3. Edit CMakeLists.txt to find OpenCV package or add include/link paths.

How to open:
- Open this folder in Android Studio. Let it sync; install missing SDK/NDK.
- Build & Run on a real Android device (camera required).
