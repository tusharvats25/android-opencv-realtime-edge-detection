package com.example.app

import android.content.Context
import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.hardware.camera2.*
import android.util.Log
import android.view.Surface
import android.os.Handler
import android.os.HandlerThread
import android.opengl.GLSurfaceView

class CameraController(private val context: Context, private val renderer: com.example.app.GLRenderer, private val glView: GLSurfaceView) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private lateinit var imageReader: ImageReader
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private val width = 640
    private val height = 480

    fun startCamera() {
        startBackgroundThread()
        imageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val nv21 = yuv420ToNv21(image)
            image.close()
            // call native
            val rgba = NativeBridge.nativeProcessFrame(nv21, width, height)
            if (rgba != null) {
                renderer.updateFrame(rgba, width, height)
                glView.requestRender()
            }
        }, backgroundHandler)

        try {
            val camId = cameraManager.cameraIdList[0]
            if (context.checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
            cameraManager.openCamera(camId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    val surface = imageReader.surface
                    val targets = listOf(surface)
                    camera.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            this@CameraController.session = session
                            val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            req.addTarget(surface)
                            req.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                            session.setRepeatingRequest(req.build(), null, backgroundHandler)
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {}
                    }, backgroundHandler)
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close() }
                override fun onError(camera: CameraDevice, error: Int) { camera.close() }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e("CameraController","startCamera: ${e.message}")
        }
    }

    fun stopCamera() {
        session?.close()
        cameraDevice?.close()
        imageReader.close()
        stopBackgroundThread()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBG")
        backgroundThread?.start()
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread?.join()
        backgroundThread = null
        backgroundHandler = null
    }

    private fun yuv420ToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4

        val nv21 = ByteArray(ySize + uvSize * 2)

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val yRowStride = image.planes[0].rowStride
        val uvRowStride = image.planes[1].rowStride
        val uvPixelStride = image.planes[1].pixelStride

        var pos = 0

        // Y
        if (yRowStride == width) {
            yBuffer.get(nv21, 0, ySize)
            pos += ySize
        } else {
            val y = ByteArray(yRowStride)
            for (row in 0 until height) {
                yBuffer.get(y, 0, yRowStride)
                System.arraycopy(y, 0, nv21, pos, width)
                pos += width
            }
        }

        // UV (NV21 format = VU VU)
        val u = ByteArray(uvRowStride)
        val v = ByteArray(uvRowStride)
        for (row in 0 until height / 2) {
            vBuffer.get(v, 0, Math.min(uvRowStride, vBuffer.remaining()))
            uBuffer.get(u, 0, Math.min(uvRowStride, uBuffer.remaining()))
            var colPos = 0
            while (colPos < width) {
                nv21[pos++] = v[colPos]
                nv21[pos++] = u[colPos]
                colPos += uvPixelStride
            }
        }

        return nv21
    }
}
