package com.giganbyte.jetpackcomposetfobjectdetection

import android.content.Context
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.giganbyte.jetpackcomposetfobjectdetection.MetricsUtil.convertPixelsToDp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.min

class PreviewViewModel : ViewModel(){


    private val _previewState = MutableStateFlow(PreviewState())
    private val modalBottomViewModel = ModalBottomViewModel() // TODO : i still think that i should rethink separation of view models

    val previewState: StateFlow<PreviewState> = _previewState.asStateFlow()
    var fpsCounterJob: Job? = null
    var fpsCounter = MutableLiveData(0)

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
                    topMargin = convertPixelsToDp(location.top,null).dp,
                    leftMargin = convertPixelsToDp(location.left,null).dp,
                    width = convertPixelsToDp(min(_previewState.value.previewSize.width.toFloat(), location.right - location.left),null).dp,// seems like location.bottom.toInt() - location.top.toInt() is always larger than previewSize.width
                    height = convertPixelsToDp(min(_previewState.value.previewSize.height.toFloat(), location.bottom - location.top),null).dp,
                    label = detection.label,
                    score = detection.score
                )
            })
        }

    }
    fun startFpsCounter() {
        fpsCounterJob = viewModelScope.launch {
            while (isActive) {

                //update _previewState.value.fps with fpsCounter.value
                _previewState.update {
                    it.copy(fps = fpsCounter.value ?: 0)
                }

                Log.d("OBJECTS PS", _previewState.value.fps.toString())
                fpsCounter.postValue(0)
                delay(1000)
            }
        }
    }

    fun incrementFpsCounter() {
        fpsCounter.postValue(fpsCounter.value?.plus(1))
    }
    fun stopFpsCounter() {
        fpsCounter.postValue(0)
        _previewState.update {
            it.copy(fps = 0)
        }
        fpsCounterJob?.cancel()
    }
    override fun onCleared() {
        super.onCleared()
        fpsCounterJob?.cancel()
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
    fun TfAnalyzerBuilder(context: Context,model: Model): ImageAnalysis.Analyzer {
        val  tfAnalizer =  TfAnalyzer(context,model, ::updateDetections,::updateResolution   )
        return tfAnalizer
    }

    private fun mapOutputCoordinates(location: RectF): RectF {

        // Step 1: map location to the preview coordinates
        val previewSize = _previewState.value.previewSize

        val previewLocation = RectF(
            (location.left * previewSize.width),
            (location.top * previewSize.height),
            (location.right * previewSize.width),
            (location.bottom * previewSize.height)
        )

        // Step 2: compensate for camera sensor orientation and mirroring
        val isBackFacing = _previewState.value.cameraState.lensFacing == CameraSelector.LENS_FACING_BACK
        val isFlippedOrientation = _previewState.value.cameraState.imageRotationDegrees  == 90 || _previewState.value.cameraState.imageRotationDegrees == 270
        val rotatedLocation = if (
            (!isBackFacing && isFlippedOrientation) ||
            (isBackFacing && !isFlippedOrientation)) {
            RectF(
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
        val requestedRatio = previewState.value.aspectRatioF
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
    val fps : Int = 0, //fps
    val confidence: Float = 0.5f,
    val previewSize: IntSize = IntSize(0, 0),  //  1080 x 2138
    val aspectRatio: Int = 1,
    val aspectRatioF: Float = 1f / 1f,
    val detections: List<ObjectDetectionHelper.ObjectPrediction> = emptyList(),
    val calculatedDetectionLocations : List<DetectionLocation> = emptyList(),
    val cameraState: CameraState = CameraState(),
    val modelResolution: Resolution = Resolution(0,0)
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
