package com.example.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.Surface
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.opengl.GLSurfaceView

class MainActivity : AppCompatActivity() {
    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: com.example.app.GLRenderer
    private lateinit var cameraController: CameraController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simple layout programmatically
        val root = FrameLayout(this)
        glView = GLSurfaceView(this)
        glView.setEGLContextClientVersion(2)
        renderer = com.example.app.GLRenderer(this)
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        val saveBtn = Button(this).apply { text = "Save Frame" }
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = 20
        lp.topMargin = 20

        root.addView(glView)
        root.addView(saveBtn, lp)
        setContentView(root)

        // permissions
        val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val ok = perms.all { p -> ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED }
        if (!ok) {
            ActivityCompat.requestPermissions(this, perms, 1001)
        } else {
            startCamera()
        }

        saveBtn.setOnClickListener {
            renderer.saveCurrentFrame()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            startCamera()
        }
    }

    private fun startCamera() {
        cameraController = CameraController(this, renderer, glView)
        cameraController.startCamera()
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
    }

    override fun onPause() {
        super.onPause()
        glView.onPause()
        cameraController.stopCamera()
    }
}
