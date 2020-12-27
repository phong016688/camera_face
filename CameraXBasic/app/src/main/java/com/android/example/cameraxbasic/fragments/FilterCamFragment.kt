package com.android.example.cameraxbasic.fragments

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.android.example.cameraxbasic.MainActivity
import com.android.example.cameraxbasic.R
import com.android.example.cameraxbasic.utils.FLAGS_FULLSCREEN
import com.bumptech.glide.Glide
import jp.co.cyberagent.android.gpuimage.GPUImageView
import jp.co.cyberagent.android.gpuimage.filter.*
import kotlinx.android.synthetic.main.fragment_filter_camera.view.*
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGrayscaleFilter as GPUImageGrayscaleFilter1

class FilterCamFragment : Fragment(R.layout.fragment_filter_camera) {
    private lateinit var GPUIImageView: GPUImageView
    private lateinit var containerAction: LinearLayout
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private val cameraExecutor by lazy { Executors.newSingleThreadExecutor() }
    private var bitmapCapture: Bitmap? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        GPUIImageView = view.GPUIView
        containerAction = view.containerAction
        addButtons()

        view.filter_camera_capture_button.setOnClickListener {
            bitmapCapture = GPUIImageView.capture()
            bitmapCapture?.apply {
                val file = CameraFragment.createFile(
                        MainActivity.getOutputDirectory(requireContext()),
                        CameraFragment.FILENAME,
                        CameraFragment.PHOTO_EXTENSION)
                if (!file.exists()) {
                    file.createNewFile()
                }
                val os = FileOutputStream(file)
                val stream = ByteArrayOutputStream()
                compress(Bitmap.CompressFormat.JPEG, 100, stream)
                val byteArray = stream.toByteArray()
                os.write(byteArray)
                os.close()

                Handler(Looper.getMainLooper()).post {
                    Glide.with(requireContext())
                            .load(this)
                            .into(view.filter_photo_view_button)
                }
            }
            view.filter_photo_view_button.setOnClickListener {
                Navigation.findNavController(requireActivity(), R.id.fragment_container)
                        .navigate(FilterCamFragmentDirections.filterToGallery(
                                MainActivity.getOutputDirectory(requireContext()).path)
                        )
            }
        }

        view.swichButton.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT && cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) == true) {
                CameraSelector.LENS_FACING_BACK
            } else if (lensFacing == CameraSelector.LENS_FACING_BACK && cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) == true) {
                CameraSelector.LENS_FACING_FRONT
            } else return@setOnClickListener
            bindCameraUseCases()
        }

        setupCamera()

        view.postDelayed({
            view.systemUiVisibility = FLAGS_FULLSCREEN
        }, 200L)
    }

    private fun addButtons() {
        addButton("no filter", GPUImageFilter())
        addButton("sketch", GPUImageSketchFilter())
        addButton("color invert", GPUImageColorInvertFilter())
        addButton("solarize", GPUImageSolarizeFilter())
        addButton("grayscale", GPUImageGrayscaleFilter1())
        addButton("brightness", GPUImageBrightnessFilter(.8f))
        addButton("contrast", GPUImageContrastFilter(2f))
        addButton("pixelation", GPUImagePixelationFilter().apply { setPixel(20F) })
        addButton("glass sphere", GPUImageGlassSphereFilter())
        addButton("crosshatch", GPUImageCrosshatchFilter())
        addButton("gamma", GPUImageGammaFilter(2f))
    }

    private fun addButton(text: String, filter: GPUImageFilter?) {
        val button = Button(requireContext()).apply {
            setText(text)
            setOnClickListener {
                GPUIImageView.filter = filter
            }
        }
        containerAction.addView(button,
                FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        )
    }

    private fun setupCamera() {
        ProcessCameraProvider.getInstance(requireContext()).apply {
            addListener({
                cameraProvider = get()

                lensFacing = when {
                    cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                            ?: false -> CameraSelector.LENS_FACING_FRONT
                    cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                            ?: false -> CameraSelector.LENS_FACING_BACK
                    else -> throw IllegalStateException("has support camera")
                }

                bindCameraUseCases()

            }, ContextCompat.getMainExecutor(context))
        }
    }

    override fun onPause() {
        cameraProvider?.unbindAll()
        super.onPause()
    }

    override fun onDestroy() {
        if (!cameraExecutor.isShutdown) {
            cameraExecutor.shutdown()
        }
        super.onDestroy()
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun bindCameraUseCases() = cameraProvider?.apply {
        val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
        var frameCount = 0
        imageAnalysis.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer {
            frameCount++
            val converter = YuvToRgbConverter(requireContext())
            val bitmap = Bitmap.createBitmap(it.width, it.height, Bitmap.Config.ARGB_8888)
            converter.yuvToRgb(it.image ?: return@Analyzer, bitmap)
            val bitmapRotate = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply {
                if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    postRotate(270f)
                    postScale(-1f, 1f)
                } else postRotate(90f)
            }, true)
            if (frameCount % 3 == 0)
                swapFace(bitmapRotate)
            it.close()
        })
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        cameraProvider?.unbindAll()
        cameraProvider?.bindToLifecycle(this@FilterCamFragment, cameraSelector, imageAnalysis)
    }

    private fun swapFace(bitmapRotate: Bitmap) {
        val byteArray = getPixelsRGBA(bitmapRotate)

        val faceInfo = FaceSDKNative.getInstance().FaceDetect(byteArray, bitmapRotate.width, bitmapRotate.height, 4)

        val bitmapResult = Bitmap.createBitmap(bitmapRotate.width, bitmapRotate.height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(bitmapResult)
        canvas.drawBitmap(bitmapRotate, 0f, 0f, null)

        if (faceInfo[0] > 1) {
            (0 until faceInfo[0] / 2).forEachIndexed { index, _ ->
                val indexRevert = index + faceInfo[0] / 2
                val left = faceInfo[1 + 4 * index]
                val top = faceInfo[2 + 4 * index]
                val right = faceInfo[3 + 4 * index]
                val bottom = faceInfo[4 + 4 * index]

                val left2 = faceInfo[1 + 4 * indexRevert]
                val top2 = faceInfo[2 + 4 * indexRevert]
                val right2 = faceInfo[3 + 4 * indexRevert]
                val bottom2 = faceInfo[4 + 4 * indexRevert]

                val face1 = Bitmap.createBitmap(bitmapRotate, left, top, right - left, bottom - top, Matrix().apply {
                    postScale((right2 - left2).toFloat() / (right - left).toFloat(), (bottom2 - top2).toFloat() / (bottom - top).toFloat())
                }, false)
                val face2 = Bitmap.createBitmap(bitmapRotate, left2, top2, right2 - left2, bottom2 - top2, Matrix().apply {
                    postScale((right - left).toFloat() / (right2 - left2).toFloat(), (bottom - top).toFloat() / (bottom2 - top2).toFloat())
                }, false)

                canvas.drawBitmap(face1, left2.toFloat(), top2.toFloat(), null)
                canvas.drawBitmap(face2, left.toFloat(), top.toFloat(), null)

                GPUIImageView.post {
                    GPUIImageView.setImage(bitmapResult)
                }
            }
        } else {
            GPUIImageView.post {
                GPUIImageView.setImage(bitmapResult)
            }
        }
    }

    private fun getPixelsRGBA(image: Bitmap): ByteArray? {
        // calculate how many bytes our image consists of
        val bytes = image.byteCount
        val buffer = ByteBuffer.allocate(bytes) // Create a new buffer
        image.copyPixelsToBuffer(buffer) // Move the byte data to the buffer
        return buffer.array()
    }
}