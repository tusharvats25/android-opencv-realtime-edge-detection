A complete Android Studio project skeleton demonstrating a real-time camera processing pipeline:
Camera2 (YUV_420_888) → NV21 → JNI → OpenCV (NV21→BGR→Gray→Canny→RGBA) → OpenGL ES 2.0 rendering → Save PNG.

⚠️ Important prerequisites

Android Studio (recommended: latest stable/Hedgehog or newer)

Android SDK Tools, NDK and CMake (install via SDK Manager → SDK Tools)

A real Android device (emulator usually lacks camera performance and real camera hardware)

OpenCV Android SDK (download from https://opencv.org/releases/
)

This README provides step-by-step build/setup instructions, code pointers, common pitfalls & fixes, recommended commit history, testing checklist and submission notes.

Table of contents

Features (what this repo demonstrates)

Project layout (files & important paths)

Setup — OpenCV + NDK + CMake (step-by-step)

Build & run (Android Studio + command-line)

How the pipeline works (flow + key snippets)

Important implementation notes & optimizations

Screenshots / GIF (where to add them)

Testing checklist & expected behavior

Troubleshooting (common errors + fixes)

Suggested commit history (what reviewers expect)

Submission checklist & final notes

License

1 — Features implemented

Camera2 API live capture using ImageReader with ImageFormat.YUV_420_888.

Efficient conversion to NV21 in Kotlin for easy OpenCV consumption.

JNI bridge to call native C++ processing from Kotlin.

OpenCV native pipeline:

NV21 → cv::Mat (YUV) → BGR

BGR → grayscale

Canny edge detection

Canny result → RGBA (for texture upload)

Return processed RGBA byte buffer from native to Kotlin.

Render RGBA bytes to screen using OpenGL ES 2.0 (texture upload + quad).

Save a processed frame as PNG to Downloads/processed_sample.png.

Minimal TypeScript web viewer that can display the saved PNG (optional).

2 — Project layout (key files & locations)
android_full_project/
├─ app/
│  ├─ src/main/java/com/example/app/
│  │  ├─ MainActivity.kt           # app lifecycle + UI
│  │  ├─ CameraController.kt       # Camera2 + ImageReader + NV21 conversion
│  │  ├─ NativeBridge.java         # JNI loader + native method declaration
│  │  └─ GLRenderer.kt             # GLSurfaceView.Renderer implementation
│  ├─ src/main/cpp/
│  │  ├─ native-lib.cpp            # OpenCV processing (NV21→Canny→RGBA)
│  │  └─ CMakeLists.txt            # native build
│  └─ src/main/jniLibs/            # place OpenCV .so files here
│      ├─ arm64-v8a/
│      │  └─ libopencv_java4.so
│      └─ armeabi-v7a/
│         └─ libopencv_java4.so
├─ web/
│  ├─ index.html
│  └─ src/index.ts                 # TypeScript viewer
└─ screenshots/
   ├─ live_canny.png
   ├─ saved_png.png
   └─ web_viewer.png


Note: app/src/main/cpp/opencv/include/ is expected to contain OpenCV headers (copied from SDK).

3 — Setup (OpenCV + NDK + CMake) — step-by-step
A. Install Android NDK & CMake

Open Android Studio → SDK Manager → SDK Tools.

Check and install:

NDK (Side by side) (recommended stable)

CMake (min supported version e.g. 3.10+)

LLDB (optional, useful for native debugging)

B. Download OpenCV Android SDK

Go to https://opencv.org/releases/
 and download the latest OpenCV Android zip (e.g. OpenCV-android-sdk-4.x.x.zip).

Unzip it locally.

C. Copy OpenCV native libs + headers into the project

From the extracted SDK:

Copy the ABI .so files:

<open_cv_sdk>/sdk/native/libs/arm64-v8a/libopencv_java4.so
<open_cv_sdk>/sdk/native/libs/armeabi-v7a/libopencv_java4.so
<open_cv_sdk>/sdk/native/libs/x86/libopencv_java4.so   # if needed
...


→ Paste to:

app/src/main/jniLibs/arm64-v8a/libopencv_java4.so
app/src/main/jniLibs/armeabi-v7a/libopencv_java4.so


Copy headers:

<open_cv_sdk>/sdk/native/jni/include/


→ Paste into project:

app/src/main/cpp/opencv/include/


(so that within project the include path becomes app/src/main/cpp/opencv/include/opencv2/...)

D. Update CMakeLists.txt

Replace or update app/src/main/cpp/CMakeLists.txt to include OpenCV and link the imported library. Example (robust & minimal):

cmake_minimum_required(VERSION 3.6)
project(native-lib)

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# include OpenCV headers
include_directories(${CMAKE_SOURCE_DIR}/opencv/include)

# create library from native sources
add_library(native-lib SHARED native-lib.cpp)

# declare imported OpenCV shared library for the current ABI
add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(opencv_java4 PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../jniLibs/${ANDROID_ABI}/libopencv_java4.so
)

find_library(log-lib log)

target_link_libraries(
    native-lib
    opencv_java4
    ${log-lib}
)


Why this approach? It links the libopencv_java4.so from app/src/main/jniLibs/<ABI>/ so no global OpenCV_DIR is needed. The ${ANDROID_ABI} variable chooses the correct ABI at build time.

E. Load OpenCV .so before native-lib

In Java/Kotlin create a small loader to ensure libopencv_java4.so is loaded first:

// OpenCVLoader.java
package com.example.app;
public class OpenCVLoader {
    static { System.loadLibrary("opencv_java4"); }
}


Then in your native bridge let the loader run first:

static {
    new OpenCVLoader();     // force load opencv_java4
    System.loadLibrary("native-lib");
}

4 — Build & Run
A. Using Android Studio

Open the project root in Android Studio.

Android Studio will detect externalNativeBuild and CMake configuration; it may ask to install missing items—follow prompts.

Ensure your connected device has Developer options & USB debugging enabled.

Run the app.

B. Command line (optional)

Build:

./gradlew assembleDebug


Install APK on device:

adb install -r app/build/outputs/apk/debug/app-debug.apk

5 — How the pipeline works (concise + code pointers)
Flow (high-level)

CameraController opens Camera2 session and attaches ImageReader (YUV_420_888).

On frame available: convert the planes to an NV21 ByteArray.

Call JNI NativeBridge.nativeProcessFrame(nv21, width, height) returning a byte[] (RGBA).

GLRenderer accepts RGBA bytes as ByteBuffer and calls glTexImage2D / glTexSubImage2D to upload texture.

Renderer draws a textured quad to display processed frame.

On "Save frame" action: copy current RGBA → Bitmap → save as PNG to Downloads/processed_sample.png.

Web viewer loads the PNG for validation.

Key JNI signature (example)
// NativeBridge.java (static method)
public class NativeBridge {
    static { OpenCVLoader loader = new OpenCVLoader(); System.loadLibrary("native-lib"); }
    public static native byte[] nativeProcessFrame(byte[] nv21, int width, int height);
}

Key native C++ skeleton
extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_example_app_NativeBridge_nativeProcessFrame(JNIEnv *env, jclass clazz,
                                                    jbyteArray nv21Data, jint width, jint height) {
    jbyte *nv21 = env->GetByteArrayElements(nv21Data, NULL);
    cv::Mat yuv(height + height/2, width, CV_8UC1, (unsigned char*)nv21);
    cv::Mat bgr; cv::cvtColor(yuv, bgr, cv::COLOR_YUV2BGR_NV21);
    cv::Mat gray; cv::cvtColor(bgr, gray, cv::COLOR_BGR2GRAY);
    cv::Mat edges; cv::Canny(gray, edges, 50, 150);
    cv::Mat rgba; cv::cvtColor(edges, rgba, cv::COLOR_GRAY2RGBA);
    int sz = rgba.total() * rgba.elemSize();
    jbyteArray out = env->NewByteArray(sz);
    env->SetByteArrayRegion(out, 0, sz, (jbyte *)rgba.data);
    env->ReleaseByteArrayElements(nv21Data, nv21, JNI_ABORT);
    return out;
}


Important: Avoid creating new cv::Mat on every frame in production—reuse Mats & buffers to reduce allocations.

6 — Implementation notes & optimizations
NV21 conversion

NV21 organizes chroma as VU pairs; ensure pixelStride and rowStride are handled correctly when copying interleaved UV to NV21.

Performance

Use GLSurfaceView.RENDERMODE_WHEN_DIRTY and call requestRender() only on new processed frames.

Use glTexSubImage2D for updates if the texture format/size does not change.

Resize frames to 640×480 (or lower) for reliable FPS on mid-range devices.

Reuse ByteBuffer (direct) and cv::Mat across frames.

Memory safety

Use env->ReleaseByteArrayElements(..., JNI_ABORT) when you don’t modify the incoming byte array.

Avoid new allocations inside the JNI loop; allocate once and reuse.

OpenGL notes

Texture coordinate flip: Camera images often appear vertically flipped — adjust texture coords or flip in shader.

Use GLES20.GL_NEAREST for faster uploads (sufficient for edge images).

7 — Screenshots / GIF (what to include)

Place images under /screenshots/ and reference them in your README:

screenshots/live_canny.png — live camera view with Canny overlay

screenshots/saved_png.png — the saved processed_sample.png (clean image)

screenshots/web_viewer.png — web viewer showing the saved PNG

screenshots/full_flow.gif — (optional bonus) a 6–10s GIF showing: live view → save frame → open web viewer

How to make the GIF:

Use Android screen recorder or adb shell screenrecord /sdcard/record.mp4.

Pull the file: adb pull /sdcard/record.mp4 .

Convert to GIF:

ffmpeg -i record.mp4 -vf "fps=12,scale=720:-1:flags=lanczos" -loop 0 full_flow.gif


Place full_flow.gif in screenshots/.

8 — Testing checklist & expected behavior
Functional tests

 App launches, requests Camera permission.

 On permission grant, camera preview starts.

 Canny edge output visible and updates in real-time.

 Save Frame button writes /sdcard/Download/processed_sample.png.

 Saved PNG can be opened by device gallery/PC.

 Web viewer loads the saved PNG and displays correctly.

Performance tests

Measure FPS (log timestamps in CameraController when frame processed & rendered). Expect ~10–15 fps at 640×480 on mid-range devices.

Check memory usage → no unbounded growth.

9 — Troubleshooting (common errors & fixes)
1) A/g: Could not load opencv_java4

Ensure libopencv_java4.so exists under app/src/main/jniLibs/<ABI>/.

Ensure loader runs before native-lib:

static { new OpenCVLoader(); System.loadLibrary("native-lib"); }

2) CMake: OpenCV not found or undefined OpenCV symbols

Use the included CMakeLists.txt (imports .so) or configure OpenCV_DIR if using the SDK's OpenCVConfig.cmake.

Ensure header include path set to app/src/main/cpp/opencv/include.

3) App crashes on camera open

Confirm runtime CAMERA permission granted.

Confirm your chosen cameraId supports the requested resolution.

Check logcat stack trace.

4) Processed image appears rotated or flipped

Flip vertically in shader or swap texture coordinates.

5) JNI memory leak / poor performance

Release JNI arrays (ReleaseByteArrayElements) and avoid copying unless necessary.

Reuse buffers & Mats.

10 — Suggested commit history (what reviewers should see)

A clear incremental commit history proves your development process. Example sequence:

feat: initialize Android + web project structure
feat: add Camera2 preview pipeline
feat: implement YUV_420_888 → NV21 conversion
feat: add JNI bridge + CMake scaffold
feat: integrate OpenCV NV21→BGR
feat: add grayscale + canny processing
feat: return RGBA to Kotlin
feat: implement OpenGL ES texture renderer
feat: add Save Frame (PNG)
feat: create TypeScript web viewer
perf: reuse buffers and optimize JNI copies
docs: add README + screenshots + build notes
chore: final polish


If you used the auto-commit scripts, ensure commits include small, meaningful diffs (avoid committing unrelated files).

11 — Submission checklist (final)

Before submission:

 Push repo to GitHub with readable repo name (e.g. android-opencv-gl-jni-camera)

 Ensure multiple meaningful commits are present (no single “final commit”)

 README contains: features, setup, architecture, screenshots/GIF, build steps

 /screenshots contains real images + optional GIF

 App builds & runs on device (NDK + OpenCV configured)

 Saved PNG can be loaded in web viewer

 Fill the evaluation form: https://forms.gle/sBouUWUKxy7pf6mKA

12 — License

This project skeleton is provided for learning and evaluation purposes. Use and modify freely for interview/submission. No warranty implied.

Final tips

Keep your commit messages descriptive and focused (one change per commit).

Keep the README honest: indicate limitations (e.g., tested on device X, Android Y).

Record a short GIF — it’s highly persuasive for reviewers.

If you want, I can now:

Generate the exact README.md file with this content (ready to paste), or

Add example live_canny.png, saved_png.png, and web_viewer.png placeholders (images I generate for you), or

Produce the final CMakeLists.txt and NativeBridge + OpenCVLoader files ready to copy into your project.
