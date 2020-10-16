package com.android.example.cameraxbasic.fragments

import android.annotation.SuppressLint
import android.graphics.Bitmap
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
import kotlinx.android.synthetic.main.fragment_filter_camera.*
import kotlinx.android.synthetic.main.fragment_filter_camera.view.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
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
                    cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                            ?: false -> CameraSelector.LENS_FACING_BACK
                    cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                            ?: false -> CameraSelector.LENS_FACING_FRONT
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

        imageAnalysis.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer {
            val converter = YuvToRgbConverter(requireContext())

            val bitmap = Bitmap.createBitmap(it.width, it.height, Bitmap.Config.ARGB_8888)
            converter.yuvToRgb(it.image ?: return@Analyzer, bitmap)
            val bitmapRotate = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply {
                postRotate(90f)
            }, true)
            it.close()

            GPUIImageView.post {
                GPUIImageView.setImage(bitmapRotate)
            }
        })
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        cameraProvider?.unbindAll()
        cameraProvider?.bindToLifecycle(this@FilterCamFragment, cameraSelector, imageAnalysis)
    }
}