package com.giganbyte.jetpackcomposetfobjectdetection

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.border
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import androidx.lifecycle.viewmodel.compose.viewModel
import com.giganbyte.jetpackcomposetfobjectdetection.MetricsUtil.convertPixelsToDp

@SuppressLint("WrongConstant")
@Composable
fun CameraPreview(
    executor: Executor,
    previewViewModel: PreviewViewModel = viewModel(),
    modalViewModel: ModalBottomViewModel = viewModel(),

) {

    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var previewSizeHeight  by remember { mutableStateOf(0.dp) }

    var previewSizeWidth  by remember { mutableStateOf(0.dp) }

    val context = LocalContext.current //TODO: check if this is correct context for camera preview in activity i used activity context for camera executor
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val latestLifecycleEvent = remember { mutableStateOf(Lifecycle.Event.ON_ANY) }
    val isLifecycleOnResume = remember { mutableStateOf(false) }

    val previewState by previewViewModel.previewState.collectAsState()
    val modelState by modalViewModel.modalState.collectAsState()

    val previewView = remember { PreviewView(context) }


    //retrieve current lifecycle event to react to it later in composable
    DisposableEffect(lifecycle ){
        val observer = LifecycleEventObserver { _, event ->
            latestLifecycleEvent.value = event
            //on resume run startFpsCounter() coroutine from PreviewViewModel
            if (event == Lifecycle.Event.ON_RESUME) {
                isLifecycleOnResume.value = true
                previewViewModel.startFpsCounter()
            }
            //on pause stop coroutine
            if (event == Lifecycle.Event.ON_PAUSE) {
                isLifecycleOnResume.value = false
                previewViewModel.stopFpsCounter()
            }

        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }


    // should i use derivedStateOf and calculate  mapOutputCoordinates from composable state?
    // we need to prevent situation when previewSize was not updated yet if we already have previewState
    // for now i think we can stop rendering when previewSize is zero
    LaunchedEffect(previewSize) {
        previewViewModel.updatePreviewSize(previewSize)
    }

    LaunchedEffect(previewState.cameraState.lensFacing, modelState.currentModel) {
        val preview = Preview.Builder().build()

        val imageAnalysis: ImageAnalysis = ImageAnalysis.Builder()
            .setTargetAspectRatio(previewState.aspectRatio)
            .setTargetRotation(previewView.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(executor, previewViewModel.TfAnalyzerBuilder(context,modelState.currentModel))

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(previewState.cameraState.lensFacing)
            .build()

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

    ModalBottomLayout {
        Box(

            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier.fillMaxSize()
        ) {


            AndroidView({ previewView }, modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .aspectRatio(previewState.aspectRatioF)
                .onGloballyPositioned { coordinates ->
                    val size = coordinates.size
                    if (previewSize != size) {
                        previewSize = size
                        previewSizeHeight = convertPixelsToDp(size.height.toFloat(), context).dp
                        previewSizeWidth = convertPixelsToDp(size.width.toFloat(), context).dp
                    }

            })
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewSizeHeight)
                    .align(Alignment.TopStart)
                    .zIndex(8f)
                    .border(
                        width = 1.dp,
                        color = Color.Red,
                        shape = RectangleShape
                    )
            )

            {
                previewState.calculatedDetectionLocations.forEach { detection ->
                    LogCompositions("DetectionBox", detection.toString())
                    LogCompositions(tag = "PreviewSize", previewSize.toString())

                    previewViewModel.incrementFpsCounter()
                    Box(
                        modifier = Modifier
                            .offset(
                                x = detection.leftMargin,
                                y = detection.topMargin
                            )
                            .align(Alignment.TopStart)
                            .size(detection.width, detection.height)
                            .border(2.dp, detection.color, RectangleShape)
                            .aspectRatio(detection.width / detection.height)
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




