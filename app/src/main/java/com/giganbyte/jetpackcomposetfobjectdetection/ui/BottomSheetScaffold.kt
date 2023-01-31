package com.giganbyte.jetpackcomposetfobjectdetection

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel

import kotlinx.coroutines.launch
import kotlin.math.roundToInt


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BottomSheetScaffold(
    modalViewModel: BottomSheetScaffoldViewModel,
    modelStatsViewModel: ModelStatsViewModel,
    previewViewModel: PreviewViewModel,
    content: @Composable () -> Unit,

    ) {

    val modalState by modalViewModel.scaffoldState.collectAsState()
    val modelStatsState by modelStatsViewModel.modelStatsState.collectAsState()
    val modelSnapshotsRecordState by modelStatsViewModel.modelSnapshotsRecordState.collectAsState()
    val previewState by previewViewModel.previewState.collectAsState()

    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()


    //TODO: Move slider to another .kt file its logic makes this file too big
    //discrete camera framerate slider because JC implementation of Slider sucks:
    // https://stackoverflow.com/questions/66386039/jetpack-compose-react-to-slider-changed-value
    val sliderPosition =
        remember(previewState.cameraState.maxCameraFramerate) { mutableStateOf(previewState.cameraState.maxCameraFramerate) }
    val tempSliderPosition =
        remember { mutableStateOf(previewState.cameraState.maxCameraFramerate) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isInteracting = isPressed || isDragged


    BottomSheetScaffold(
        sheetContent = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .align(Alignment.CenterHorizontally),

                ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {

                    Row() {

                        IconButton(onClick = {
                            val expanded = modalState.isBottomSheetSelectorExpanded
                            if (expanded) {
                                scope.launch {
                                    scaffoldState.bottomSheetState.collapse()
                                }
                            } else {
                                scope.launch {
                                    scaffoldState.bottomSheetState.expand()
                                }
                            }
                            modalViewModel.toggleBottomSheetSelector()
                        }) {
                            val expanded = modalState.isBottomSheetSelectorExpanded
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Expand",

                                )
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            //border

                            .zIndex(1f)
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth(0.5f)
                                .fillMaxHeight()
                                .zIndex(1f)
                        ) {
                            Text(
                                text = "FPS: ${modelStatsState.fps}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            )
                            Text(

                                text = "Model Inference time: ${modelStatsState.modelInferenceTime} ms",
                                modifier = Modifier
                                    .padding(8.dp)
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .zIndex(1f),

                            ) {
                            Text(
                                text = "Model: ${modalState.currentModel.name}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            )
                            Text(
                                text = "Resolution: ${previewState.modelResolution.width}x${previewState.modelResolution.height}",
                                modifier = Modifier.padding(8.dp)
                            )


                        }
                    }
                }


            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth(0.5f)

                        .zIndex(1f)
                ) {
                    Text(
                        text = "Avg. FPS: ${modelStatsState.avgFps}",
                        modifier = Modifier
                            .padding(8.dp)
                    )
                    Text(
                        text = "UI refresh rate: ${modelStatsState.uiRefreshRate}",
                        modifier = Modifier
                            .padding(8.dp)
                    )
                }
                Column(
                    modifier = Modifier

                        .zIndex(1f),

                    ) {

                    Text(
                        text = "Preview resolution: ${previewState.previewSize.width}x${previewState.previewSize.height}",
                        modifier = Modifier.padding(8.dp)
                    )
                    Text(
                        text = "Avg. Model Inference time: ${modelStatsState.avgModelInferenceTime} ms",
                        modifier = Modifier
                            .padding(8.dp)
                    )

                }
            }

            Row() {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(0.8.dp, 16.dp, 0.8.dp, 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text("Confidence: ${(previewState.confidence * 100).roundToInt()}%")
                    Slider(
                        value = previewState.confidence,
                        onValueChange = { previewViewModel.updateConfidence(it) },


                        valueRange = 0f..1f,
                        steps = 100,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    )

                    Spacer(Modifier.height(20.dp))

                    Text("Max camera fps: ${(previewState.cameraState.maxCameraFramerate)}")
                    Slider(
                        value = (if (isDragged) {
                            tempSliderPosition.value
                        } else {
                            sliderPosition.value
                        }).toFloat(),

                        onValueChange = { progress ->
                            val steps = 11
                            val valueRange = 10f..120f
                            //calculate snappedProgress drag to closest step value
                            val stepSize =
                                (valueRange.endInclusive - valueRange.start) / steps.toFloat()
                            var snappedProgress =
                                (Math.round(progress / stepSize) * stepSize).toFloat()

                            sliderPosition.value = snappedProgress.toInt()
                            tempSliderPosition.value = snappedProgress.toInt()
                        },
                        onValueChangeFinished = {
                            sliderPosition.value = tempSliderPosition.value
                            previewViewModel.updateMaxCameraFrameRate(tempSliderPosition.value)
                        },
                        valueRange = 10f..120f,
                        steps = 11,
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    )

                    modelDropdown()
                }
            }
        },
        scaffoldState = scaffoldState,
        topBar = {
            TopAppBar(
                //set bgcolor of tensorflow color
                backgroundColor = Color(0xFFff6f00),
                title = { Text("TfLite OD Model Demo") },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { scaffoldState.drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Localized description")
                    }
                },


                )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {

                    scope.launch {
                        if (modelSnapshotsRecordState.recordingStatus == RecordingStatus.NOT_RECORDING) {
                            modelStatsViewModel.startSnapshotsRecording()
                        } else if (modelSnapshotsRecordState.recordingStatus == RecordingStatus.RECORDING) {
                            modelStatsViewModel.stopSnapshotsRecording()
                        }
                    }
                }
            ) {
                when (modelSnapshotsRecordState.recordingStatus) {
                    RecordingStatus.RECORDING -> Icon(
                        Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = Color.Red
                    )
                    RecordingStatus.NOT_RECORDING -> Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play"
                    )
                    RecordingStatus.PROCESSING -> CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }

            }


            recordingSnackbar(
                scaffoldState.snackbarHostState,
                modelSnapshotsRecordState.recordingStatus
            )
            //show snackbar when RecordingStatus is changed from PROCESSING TO NOT_RECORDING


        },
        floatingActionButtonPosition = FabPosition.End,
        sheetPeekHeight = 128.dp,
        drawerContent = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Drawer content")
                Spacer(Modifier.height(20.dp))
                Button(onClick = { scope.launch { scaffoldState.drawerState.close() } }) {
                    Text("Click to close drawer")
                }
            }
        }
    ) {
        Box {
            content()
        }

    }
}


//create composable
@Composable
fun recordingSnackbar(
    //recording status
    snackbarHostState: SnackbarHostState,
    recordingStatus: RecordingStatus,

    ) {
    Log.d("recordingSnackbar", "recordingSnackbar: $recordingStatus")
    val previousRecordingStatus = remember { mutableStateOf(RecordingStatus.RECORDING) }
    val ignoreFirst = remember { mutableStateOf(true) }
    val showSnackbar = remember { mutableStateOf(false) }
    LaunchedEffect(key1 = recordingStatus) {
        if (!ignoreFirst.value) {
            showSnackbar.value =
                recordingStatus == RecordingStatus.NOT_RECORDING && (previousRecordingStatus.value == RecordingStatus.PROCESSING || previousRecordingStatus.value == RecordingStatus.RECORDING)
            previousRecordingStatus.value = recordingStatus
            if (showSnackbar.value) {
                snackbarHostState.showSnackbar(
                    message = "Recording finished, chart saved to gallery",
                    actionLabel = "Dismiss",
                    duration = SnackbarDuration.Short
                )
            }

        }
        ignoreFirst.value = false
    }
    Spacer(modifier = Modifier.size(0.dp, 0.dp))
}

@Composable
fun modelDropdown() {
    val modalViewModel: BottomSheetScaffoldViewModel = viewModel()
    val modalState by modalViewModel.scaffoldState.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(0.8.dp, 16.dp, 0.8.dp, 16.dp)
    ) {
        Text(
            text = "Selected Model:",
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(
                text = "${modalState.currentModel.name} : ${modalState.currentModel.path}",
                color = Color.Black,

                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = "Expand",
                modifier = Modifier
                    .align(Alignment.CenterVertically)
            )
        }
        if (expanded) {
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
            ) {
                items(modalState.models.size) { model ->
                    val model = modalState.models[model]
                    Text(
                        text = model.name + " : " + model.path,
                        color = Color.Black,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                modalViewModel.updateModel(model)
                                expanded = false
                            }
                            .padding(8.dp)
                    )
                }

            }
        }
    }
}
