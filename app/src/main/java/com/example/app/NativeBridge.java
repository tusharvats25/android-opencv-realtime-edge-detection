package com.example.app;

public class NativeBridge {
    static {
        System.loadLibrary("native-lib");
    }
    public static native byte[] nativeProcessFrame(byte[] nv21, int width, int height);
}
