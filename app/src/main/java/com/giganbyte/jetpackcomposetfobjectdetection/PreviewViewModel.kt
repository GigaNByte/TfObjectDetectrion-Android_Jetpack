package com.giganbyte.jetpackcomposetfobjectdetection

import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.min

class PreviewViewModel : ViewModel(){

    // i still dont understand how it works (Backing properties) but it works
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
                Log.d("LOKATION", (location.right.toInt() - location.left.toInt()).toString())
                DetectionLocation(
                    color = color,
                    location = location,

                    //convert and save topMargin to dp for composable
                    topMargin = location.top.toFloat().dp,
                    leftMargin = location.left.toFloat().dp,
                    width = min(_previewState.value.previewSize.width, location.right.toInt() - location.left.toInt()).toFloat().dp,// seems like location.bottom.toInt() - location.top.toInt() is always larger than previewSize.width
                    height = min(_previewState.value.previewSize.height, location.bottom.toInt() - location.top.toInt()).toFloat().dp,
                    label = detection.label,
                    score = detection.score
                )
            })
        }

    }

    fun updatePreviewSize(size: IntSize) {
        _previewState.update {
            it.copy(previewSize = size)
        }
    }

    private fun mapOutputCoordinates(location: RectF): RectF {

        // Step 1: map location to the preview coordinates
        val previewSize = _previewState.value.previewSize

        val previewLocation = Rect(
            (location.left * previewSize.width).toInt(),
            (location.top * previewSize.height).toInt(),
            (location.right * previewSize.width).toInt(),
            (location.bottom * previewSize.height).toInt()
        )

        // Step 2: compensate for camera sensor orientation and mirroring
        val isFrontFacing = _previewState.value.cameraState.lensFacing == CameraSelector.LENS_FACING_FRONT
        val isFlippedOrientation = _previewState.value.cameraState.imageRotationDegrees  == 90 || _previewState.value.cameraState.imageRotationDegrees == 270
        val rotatedLocation = if (
            (!isFrontFacing && isFlippedOrientation) ||
            (isFrontFacing && !isFlippedOrientation)) {
            Rect(
                previewSize.width - previewLocation.right,
                previewSize.height - previewLocation.bottom,
                previewSize.width - previewLocation.left,
                previewSize.height - previewLocation.top
            )
        } else {
            previewLocation
        }

        // Step 3: compensate for 1:1 to 4:3 aspect ratio conversion + small margin
        val margin = 0.1f
        val requestedRatio = 4f / 3f
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
    val previewSize: IntSize = IntSize(0, 0),  //  1080 x 2138
    val detections: List<ObjectDetectionHelper.ObjectPrediction> = emptyList(),
    val calculatedDetectionLocations : List<DetectionLocation> = emptyList(),
    val cameraState: CameraState = CameraState()
)

data class DetectionLocation(

    // location=Rect(-222, 777 - 1278, 1987), topMargin=777.0.dp, leftMargin=-222.0.dp, width=1080.0.dp, height=1210.0.dp)
    val color: Color,
    val location: RectF,
    val topMargin: Dp = 0.dp,
    val leftMargin: Dp = 0.dp,
    val width : Dp = 0.dp,
    val height : Dp = 0.dp,
    val label: String,
    val score: Float
)

data class CameraState(
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val imageRotationDegrees: Int = 0
)
