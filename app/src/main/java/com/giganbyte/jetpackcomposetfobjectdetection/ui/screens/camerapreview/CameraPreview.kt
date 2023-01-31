package com.giganbyte.jetpackcomposetfobjectdetection

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.giganbyte.jetpackcomposetfobjectdetection.ui.utils.MetricsUtil.convertPixelsToDp
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.round

@androidx.camera.camera2.interop.ExperimentalCamera2Interop
@Composable
fun CameraPreview(
    appContext: Context,
    executor: Executor,
    modalViewModel: BottomSheetScaffoldViewModel = viewModel(),
) {

    val modelStatsViewModel: ModelStatsViewModel = remember { ModelStatsViewModel(appContext) }
    val previewViewModel: PreviewViewModel = remember { PreviewViewModel(modelStatsViewModel) }

    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var previewSizeHeight by remember { mutableStateOf(0.dp) }
    var previewSizeWidth by remember { mutableStateOf(0.dp) }

    val context =
        LocalContext.current //TODO: check if this is correct context (or appContext(activity context) ) for camera preview in activity i used activity context for camera executor
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val latestLifecycleEvent = remember { mutableStateOf(Lifecycle.Event.ON_ANY) }
    val isLifecycleOnResume = remember { mutableStateOf(false) }


    val previewState by previewViewModel.previewState.collectAsState()
    val modelState by modalViewModel.scaffoldState.collectAsState()

    val previewView = remember { PreviewView(context) }

    //retrieve current lifecycle event to react to it later in composable
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            latestLifecycleEvent.value = event
            if (event == Lifecycle.Event.ON_RESUME) {
                isLifecycleOnResume.value = true
                if (!modelStatsViewModel.isJobRunning()) {
                    modelStatsViewModel.startUpdateStatsJob()
                }
                //TODO CLASS2: How to move this DisposableEffect into state? Its not safe to rely on client of state to clean started job
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                isLifecycleOnResume.value = false
                modelStatsViewModel.stopFpsCounter()
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
        //convert to px
        previewViewModel.updatePreviewSize(previewSize)
    }

    LaunchedEffect(previewState.cameraState, modelState.currentModel) {
        modelStatsViewModel.stopUpdateStatsJob()

        //https://stackoverflow.com/questions/57254960/setting-target-resolution-for-camerax-not-working-as-in-documentation
        val preview = Preview.Builder()
            .setTargetResolution(Size(480, 640))
            .build()

        val builder = ImageAnalysis.Builder()
            .setTargetResolution(Size(480, 640))
            .setTargetRotation(previewState.cameraState.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)

        val ext: Camera2Interop.Extender<*> = Camera2Interop.Extender(builder)

        // CONTROL_AE_TARGET_FPS_RANGE should be as range but to maximize model performance tests we set as fixed range
        ext.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
            Range<Int>(
                previewState.cameraState.maxCameraFramerate,
                previewState.cameraState.maxCameraFramerate
            )
        )
        val imageAnalysis = builder.build()

        imageAnalysis.setAnalyzer(
            executor,
            previewViewModel.TfAnalyzerBuilder(context, modelState.currentModel)
        )

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
        modelStatsViewModel.startUpdateStatsJob()
    }

    BottomSheetScaffold(
        modalViewModel = modalViewModel,
        modelStatsViewModel = modelStatsViewModel,
        previewViewModel = previewViewModel,
    ) {
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

                modelStatsViewModel.incrementUpdatedUiFramesCounter()

                previewState.calculatedDetectionLocations.forEach { detection ->

                    Box(
                        modifier = Modifier
                            .offset(
                                x = detection.leftMargin,
                                y = detection.topMargin
                            )
                            .align(Alignment.TopStart)
                            .size(detection.width, detection.height)
                            .border(2.dp, detection.color, RectangleShape)
                            .zIndex(5f)
                    ) {
                        Text(
                            text = "${detection.label}  ${round(detection.score * 100)}%",
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .border(2.dp, detection.color, RectangleShape)
                        )
                    }
                }
            }
        }

    }
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { cameraProvider ->
            cameraProvider.addListener({
                continuation.resume(cameraProvider.get())
            }, ContextCompat.getMainExecutor(this))
        }
    }




