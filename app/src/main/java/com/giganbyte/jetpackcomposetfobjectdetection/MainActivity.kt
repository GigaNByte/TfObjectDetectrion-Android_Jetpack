package com.giganbyte.jetpackcomposetfobjectdetection

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min

class MainActivity : ComponentActivity() {

    //get view model for camera
    private val PreviewViewModel by viewModels<PreviewViewModel>()
    private lateinit var cameraExecutor: ExecutorService
    private var shouldShowCamera: MutableState<Boolean> = mutableStateOf(false)
    private val executor = Executors.newSingleThreadExecutor()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("gigamorswin", "Permission granted")

        } else {
            Log.i("gigamorswin", "Permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            if (shouldShowCamera.value) {
                Log.i("gigamorswin", "Camera preview")
                //JetpackComposeTfObjectDetectionTheme {
                    CameraPreview(
                        executor = executor,
                        analyzer = TfAnalyzer(this, PreviewViewModel::updateDetections),
                    )
            }
        }

        requestCameraPermission()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.i("kilo", "Permission previously granted")
                shouldShowCamera.value = true
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.CAMERA
            ) -> Log.i("kilo", "Show camera permissions dialog")

            else -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /*
    private fun reportPrediction(
        prediction: ObjectDetectionHelper.ObjectPrediction?
    ) = view_finder.post {

        // Early exit: if prediction is not good enough, don't report it
        if (prediction == null || prediction.score < ACCURACY_THRESHOLD) {
            box_prediction.visibility = View.GONE
            text_prediction.visibility = View.GONE
            return@post
        }

        // Location has to be mapped to our local coordinates
        val location = mapOutputCoordinates(prediction.location)

        // Update the text and UI
        text_prediction.text = "${"%.2f".format(prediction.score)} ${prediction.label}"
        (box_prediction.layoutParams as ViewGroup.MarginLayoutParams).apply {
            topMargin = location.top.toInt()
            leftMargin = location.left.toInt()
            width = min(view_finder.width, location.right.toInt() - location.left.toInt())
            height = min(view_finder.height, location.bottom.toInt() - location.top.toInt())
        }

        // Make sure all UI elements are visible
        box_prediction.visibility = View.VISIBLE
        text_prediction.visibility = View.VISIBLE
    }
*/
    /**
     * Helper function used to map the coordinates for objects coming out of
     * the model into the coordinates that the user sees on the screen.
     */
    /*
    private fun mapOutputCoordinates(location: RectF): RectF {

        // Step 1: map location to the preview coordinates
        val previewLocation = RectF(
            location.left * view_finder.width,
            location.top * view_finder.height,
            location.right * view_finder.width,
            location.bottom * view_finder.height
        )

        // Step 2: compensate for camera sensor orientation and mirroring
        val isFrontFacing = lensFacing == CameraSelector.LENS_FACING_FRONT
        val isFlippedOrientation = imageRotationDegrees == 90 || imageRotationDegrees == 270
        val rotatedLocation = if (
            (!isFrontFacing && isFlippedOrientation) ||
            (isFrontFacing && !isFlippedOrientation)) {
            RectF(
                view_finder.width - previewLocation.right,
                view_finder.height - previewLocation.bottom,
                view_finder.width - previewLocation.left,
                view_finder.height - previewLocation.top
            )
        } else {
            previewLocation
        }

        // Step 3: compensate for 1:1 to 4:3 aspect ratio conversion + small margin
        val margin = 0.1f
        val requestedRatio = 4f / 3f
        val midX = (rotatedLocation.left + rotatedLocation.right) / 2f
        val midY = (rotatedLocation.top + rotatedLocation.bottom) / 2f
        return if (view_finder.width < view_finder.height) {
            RectF(
                midX - (1f + margin) * requestedRatio * rotatedLocation.width() / 2f,
                midY - (1f - margin) * rotatedLocation.height() / 2f,
                midX + (1f + margin) * requestedRatio * rotatedLocation.width() / 2f,
                midY + (1f - margin) * rotatedLocation.height() / 2f
            )
        } else {
            RectF(
                midX - (1f - margin) * rotatedLocation.width() / 2f,
                midY - (1f + margin) * requestedRatio * rotatedLocation.height() / 2f,
                midX + (1f - margin) * rotatedLocation.width() / 2f,
                midY + (1f + margin) * requestedRatio * rotatedLocation.height() / 2f
            )
        }
    }
*/
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

