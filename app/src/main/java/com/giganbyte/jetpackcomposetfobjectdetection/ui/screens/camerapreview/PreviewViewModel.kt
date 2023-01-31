package com.giganbyte.jetpackcomposetfobjectdetection

import android.content.Context
import android.graphics.RectF
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel

import com.giganbyte.jetpackcomposetfobjectdetection.ui.utils.MetricsUtil.convertPixelsToDp
import com.giganbyte.jetpackcomposetfobjectdetection.utils.tf.ObjectDetectionHelper
import com.giganbyte.jetpackcomposetfobjectdetection.utils.tf.TfAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.min

class PreviewViewModel(
    private val modelStatsViewModel: ModelStatsViewModel,
) : ViewModel() {


    private val _previewState = MutableStateFlow(PreviewState())


    val previewState: StateFlow<PreviewState> = _previewState.asStateFlow()


    fun updateDetections(detections: List<ObjectDetectionHelper.ObjectPrediction>) {

        //update raw detections
        _previewState.update {
            it.copy(detections = detections.filter { it.score > _previewState.value.confidence })
        }

        //update calculated detections to display
        _previewState.update {
            it.copy(calculatedDetectionLocations = it.detections.map { detection ->
                val location = mapOutputCoordinates(
                    location = detection.location,
                )
                //random color
                val color = Color(
                    red = (Math.random() * 255).toInt(),
                    green = (Math.random() * 255).toInt(),
                    blue = (Math.random() * 255).toInt()
                )



                DetectionLocation(
                    color = color,
                    location = location,

                    //convert and save topMargin to dp for composable
                    topMargin = convertPixelsToDp(location.top, null).dp,
                    leftMargin = convertPixelsToDp(location.left, null).dp,
                    width = convertPixelsToDp(
                        min(
                            _previewState.value.previewSize.width.toFloat(),
                            location.right - location.left
                        ), null
                    ).dp,
                    height = convertPixelsToDp(
                        min(
                            _previewState.value.previewSize.height.toFloat(),
                            location.bottom - location.top
                        ), null
                    ).dp,
                    label = detection.label,
                    score = detection.score
                )
            })
        }

    }

    fun updateConfidence(confidence: Float) {
        _previewState.update {
            it.copy(confidence = confidence)
        }
    }

    fun updateMaxCameraFrameRate(maxCameraFramerate: Int) {
        _previewState.update {
            it.copy(
                cameraState = it.cameraState.copy(
                    maxCameraFramerate = maxCameraFramerate
                )
            )
        }
    }

    fun updatePreviewSize(size: IntSize) {
        _previewState.update {
            it.copy(previewSize = size)
        }
    }

    fun updateResolution(resolution: Resolution) {
        _previewState.update {
            it.copy(modelResolution = resolution)
        }
    }

    fun TfAnalyzerBuilder(context: Context, model: Model): ImageAnalysis.Analyzer {

        val tfAnalizer = TfAnalyzer(
            context, model, ::updateDetections, ::updateResolution,
            modelStatsViewModel
        )
        return tfAnalizer
    }

    private fun mapOutputCoordinates(location: RectF): RectF {

        //maps location to the preview coordinates
        val previewSize = _previewState.value.previewSize

        val previewLocation = RectF(
            ((location.left) * previewSize.width),
            (location.top * previewSize.height),
            ((location.right) * previewSize.width),
            (location.bottom * previewSize.height)
        )

        val isBackFacing =
            _previewState.value.cameraState.lensFacing == CameraSelector.LENS_FACING_BACK
        val isFlippedOrientation =
            _previewState.value.cameraState.rotation == 90 || _previewState.value.cameraState.rotation == 270
        val rotatedLocation = if (
            (!isBackFacing && isFlippedOrientation) ||
            (isBackFacing && !isFlippedOrientation)
        ) {

            RectF(
                previewSize.width - previewLocation.right,
                previewSize.height - previewLocation.bottom,
                previewSize.width - previewLocation.left,
                previewSize.height - previewLocation.top
            )
        } else {
            previewLocation
        }

        val margin = 0.1f
        val requestedRatio = 1f
        val midX = (rotatedLocation.left + rotatedLocation.right) / 2f
        val midY = (rotatedLocation.top + rotatedLocation.bottom) / 2f

        return if (previewSize.width < previewSize.height) {
            RectF(
                (midX - (1f + margin) * requestedRatio * rotatedLocation.width() / 2f),
                (midY - (1f - margin) * rotatedLocation.height() / 2f),
                (midX + (1f + margin) * requestedRatio * rotatedLocation.width() / 2f),
                (midY + (1f - margin) * rotatedLocation.height() / 2f)
            )
        } else {
            RectF(
                (midX - (1f - margin) * rotatedLocation.width() / 2f),
                (midY - (1f + margin) * requestedRatio * rotatedLocation.height() / 2f),
                (midX + (1f - margin) * rotatedLocation.width() / 2f),
                (midY + (1f + margin) * requestedRatio * rotatedLocation.height() / 2f)
            )
        }
    }


}

data class PreviewState(
    val confidence: Float = 0.5f,
    val previewSize: IntSize = IntSize(1080, 1440),
    val aspectRatioF: Float = 3 / 4f,
    val detections: List<ObjectDetectionHelper.ObjectPrediction> = emptyList(),
    val calculatedDetectionLocations: List<DetectionLocation> = emptyList(),
    val cameraState: CameraState = CameraState(),
    val modelResolution: Resolution = Resolution(0, 0)
)

data class DetectionLocation(
    val color: Color,
    val location: RectF,
    val topMargin: Dp = 0.dp,
    val leftMargin: Dp = 0.dp,
    val width: Dp = 0.dp,
    val height: Dp = 0.dp,
    val label: String,
    val score: Float
)


data class CameraState(
    val rotation: Int = Surface.ROTATION_0,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val maxCameraFramerate: Int = 60,
    //define integer with value range between 30 and 120
    val imageRotationDegrees: Int = 0
)

