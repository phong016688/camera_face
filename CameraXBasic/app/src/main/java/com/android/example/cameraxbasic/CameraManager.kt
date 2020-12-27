package com.android.example.cameraxbasic

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import androidx.annotation.MainThread
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.core.VideoCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

interface ImageProcessManager {
    fun processImage(bitmap: Bitmap)
}

@MainThread
class CameraManager(private val context: Context) {
    private val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    private val imageProcessManager: ImageProcessManager? = null
    private val resolution: Size
    private val screenRotation: Int

    init {
        resolution = Size(640, 480)
        screenRotation = context.display?.rotation ?: 0
    }

    @SuppressLint("RestrictedApi")
    fun start(lifecycleOwner: LifecycleOwner) {
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()

                val lenFacing = when {
                    hasFrontCamera(cameraProvider) -> CameraSelector.LENS_FACING_FRONT
                    hasBackCamera(cameraProvider) -> CameraSelector.LENS_FACING_BACK
                    else -> CameraSelector.LENS_FACING_FRONT
                }
                val cameraSelector = CameraSelector.Builder().requireLensFacing(lenFacing).build()

                val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(resolution)
                        .setTargetRotation(screenRotation)
                        .setImageQueueDepth(STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                val videoCapture = VideoCapture.Builder()
                        .setVideoFrameRate(6)
                        .setBitRate(400 * 300)
                        .setTargetResolution(Size(300, 400))
                        .setCaptureOptionUnpacker()
                        .setTargetRotation(screenRotation)
                        .build()

                videoCapture.startRecording()

                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)

            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun hasBackCamera(cameraProvider: ProcessCameraProvider): Boolean {
        return cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
    }

    private fun hasFrontCamera(cameraProvider: ProcessCameraProvider): Boolean {
        return cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
    }

    data class NotFindCameraProvider(val messageError: String) : Exception(messageError)
}