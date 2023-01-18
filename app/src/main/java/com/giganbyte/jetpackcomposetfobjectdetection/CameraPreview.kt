package com.giganbyte.jetpackcomposetfobjectdetection

import android.content.Context
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.border
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.Lens
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.Ref
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlin.math.min

@Composable
fun CameraPreview(
    executor: Executor,
    analyzer: ImageAnalysis.Analyzer,
    previewViewModel: PreviewViewModel = viewModel()
) {

    //create fps counter for debugging  as state variable
    var fps by remember { mutableStateOf(0) }
    var fpsCounter by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            fps = fpsCounter

            Log.d("OBJECTS PS", fps.toString())
            fpsCounter = 0
            delay(1000)
        }
    }


    // 1
    var previewSize by remember { mutableStateOf(IntSize.Zero) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    //create mutable state that holds  mapOutputCoordinates

    val previewState by previewViewModel.previewState.collectAsState()
    val previewView = remember { PreviewView(context) }
//    val imageCapture: ImageCapture = remember { ImageCapture.Builder().build() }

    val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(previewState.cameraState.lensFacing)
        .build()

    //TODO
    // should i use derivedStateOf and calculate  mapOutputCoordinates from composable state?
    // we need to prevent situation when previewSize was not updated yet if we already have previewState
    // for now i think we can stop rendering when previewSize is zero
    LaunchedEffect(previewSize) {
        previewViewModel.updatePreviewSize(previewSize)
    }

    LaunchedEffect(previewState.cameraState.lensFacing) {
        val preview = Preview.Builder().build()

        val imageAnalysis: ImageAnalysis = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            //.setTargetRotation(view_finder.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(executor, analyzer)

        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysis
        )

        preview.setSurfaceProvider(previewView.surfaceProvider)
    }

    Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.fillMaxSize()
    ) {


        AndroidView({ previewView }, modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                previewSize = IntSize(coordinates.size.width, coordinates.size.height)
            })


        previewState.calculatedDetectionLocations.forEach { detection ->
            LogCompositions("DetectionBox",detection.toString())
            LogCompositions(tag = "PreviewSize",previewSize.toString())
            fpsCounter++
            Box(
                modifier = Modifier
                    .size(detection.width, detection.height)
                    .border(2.dp, detection.color, RectangleShape)
                    .align(Alignment.TopStart)
                    .offset(
                        x = detection.leftMargin,
                        y = detection.topMargin
                    )
                    .zIndex(5f)
            ) {
                //draw text label and score
                Text(
                    text = "${detection.label} ${detection.score}",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

class Ref(var value: Int)

@Composable
inline fun LogCompositions(tag: String, msg: String) {
    if (BuildConfig.DEBUG) {
        val ref = remember { Ref(0) }
        SideEffect { ref.value++ }
        Log.d(tag, "Compositions: $msg ${ref.value}")
    }
}


private suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    ProcessCameraProvider.getInstance(this).also { cameraProvider ->
        cameraProvider.addListener({
            continuation.resume(cameraProvider.get())
        }, ContextCompat.getMainExecutor(this))
    }
}




