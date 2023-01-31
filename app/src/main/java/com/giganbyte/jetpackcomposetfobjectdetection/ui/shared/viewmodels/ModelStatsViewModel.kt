package com.giganbyte.jetpackcomposetfobjectdetection


import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giganbyte.jetpackcomposetfobjectdetection.utils.InferenceTimer
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream


class ModelStatsViewModel(private val appContext: Context) : ViewModel() {

    private val _modelStatsState = MutableStateFlow(ModelStatsState())
    private val _modelStatsSnapshotsRecordState = MutableStateFlow(ModelStatsSnapshotsRecordState())

    val modelStatsState: StateFlow<ModelStatsState> = _modelStatsState.asStateFlow()
    val modelSnapshotsRecordState: StateFlow<ModelStatsSnapshotsRecordState> =
        _modelStatsSnapshotsRecordState.asStateFlow()

    private var updateStatsJob: Job? = null

    //for measuring inference time (method 1 using InferenceTimer, we define start and stop point by ourselves by invoking start() and stop() method of InferenceTimer)
    private var inferenceTime = MutableLiveData(0)
    private var avgInferenceTime = MutableLiveData(0)
    private var totalTime: MutableLiveData<Long> =
        MutableLiveData(0) //measured from first model run (inference start time)
    private val timer = InferenceTimer()

    //for measuring fps ( method 2: we define only measure time by invoking increment() method, not measure interval)
    private var fpsCounter = MutableLiveData(0)
    private var avgFps = MutableLiveData(0)
    private var frameCounter = 0
    private var totalFrameCounter = 0
    private var totalFpsCounter = 0
    private var lastFpsTimestamp = System.currentTimeMillis()


    /**
     * @param updatedUiFramesCounter - updatedUiFramesCounter: Int
     *
     * For measuring updated frames when drawing list of bounding boxes, method 3: manual increment without timer,
     *  prone to too long startUpdateStatsJob execution: can display wrong values (doubled up) when  startUpdateStatsJob() execution is longer than 1 second
     */
    var updatedUiFramesCounter = MutableLiveData(0)

    // TODO: Research if we can use Choreographer to measure fps etc. (https://developer.android.com/reference/android/view/Choreographer)

    /**
     * Starts coroutine job that updates UI with stats every second
     */
    fun startUpdateStatsJob() {
        // make sure that we don't have any other jobs running
        updateStatsJob?.cancel()
        updateStatsJob = viewModelScope.launch {
            while (isActive) {
                avgInferenceTime.postValue(timer.getAvgInferenceTime().toInt())
                totalTime.postValue(timer.getTotalTime())
                //update all stats every 1 second
                _modelStatsState.update {
                    it.copy(
                        avgFps = avgFps.value ?: 0,
                        uiRefreshRate = updatedUiFramesCounter.value ?: 0,
                        fps = fpsCounter.value ?: 0,
                        modelInferenceTime = inferenceTime.value ?: 0,
                        avgModelInferenceTime = avgInferenceTime.value ?: 0,
                        totalTime = totalTime.value ?: 0
                    )
                }

                fpsCounter.postValue(0)
                updatedUiFramesCounter.postValue(0)


                if (_modelStatsSnapshotsRecordState.value.recordingStatus == RecordingStatus.RECORDING) {
                    //declare snapshot
                    val snap = ModelStatsSnapshot(
                        fps = fpsCounter.value ?: 0,
                        modelInferenceTime = inferenceTime.value ?: 0,
                    )

                    addSnapshotToRecord(snap)

                }
                delay(1000)
            }
        }
    }


    fun isJobRunning(): Boolean {
        return updateStatsJob?.isActive ?: false
    }

    /**
     * Starts InferenceTimer to measure inference time
     */

    fun startModelInferenceTimeMeasurement() {
        timer.startMeasurement()
    }

    /**
     * Stops InferenceTimer to measure inference time
     */
    fun stopModelInferenceTimeMeasurement() {
        inferenceTime.postValue(timer.stopMeasurement().toInt())
    }

    /**
     * Updates fpsCounter and calculates avgFps, should be invoked every time when we ended Tf model inference
     */
    fun updateFpsCounter() {
        val frameCount = 10
        if (++frameCounter % frameCount == 0) {
            totalFrameCounter++
            frameCounter = 0

            val now = System.currentTimeMillis()
            val delta = now - lastFpsTimestamp
            var fps = 1f;


            //check if delta is not 0 to avoid division by 0
            if (delta != 0L) {
                fps = 1000 * frameCount.toFloat() / delta
            }
            lastFpsTimestamp = now

            //fps
            fpsCounter.postValue(fps.toInt())

            //avg fps
            totalFpsCounter += fps.toInt()
            avgFps.postValue(totalFpsCounter / totalFrameCounter)


        }
    }

    fun incrementUpdatedUiFramesCounter() {
        updatedUiFramesCounter.postValue(updatedUiFramesCounter.value?.plus(1))
    }

    fun stopFpsCounter() {
        fpsCounter.postValue(0)
        totalFrameCounter = 0
        totalFpsCounter = 0
        updatedUiFramesCounter.postValue(0)
        _modelStatsState.update {
            it.copy(fps = 0, uiRefreshRate = 0, avgFps = 0)
        }
        updateStatsJob?.cancel()
    }

    override fun onCleared() {
        stopUpdateStatsJob()
        super.onCleared()
    }

    fun stopUpdateStatsJob() {
        reset()
        updateStatsJob?.cancel()

    }

    //clear and run again
    private fun reset() {
        stopFpsCounter()
        timer.reset()
        inferenceTime.postValue(0)
        avgInferenceTime.postValue(0)
        totalTime.postValue(0)
        _modelStatsState.update {
            it.copy(
                fps = 0,
                uiRefreshRate = 0,
                avgFps = 0,
                modelInferenceTime = 0,
                avgModelInferenceTime = 0,
                totalTime = 0
            )
        }
    }

    fun addSnapshotToRecord(snapshot: ModelStatsSnapshot) {

        //check maxSnapshots limit
        if (_modelStatsSnapshotsRecordState.value.snapshotsRecord.size >= _modelStatsSnapshotsRecordState.value.maxSnapshots) {

            stopSnapshotsRecording()
        }
        //check if start time is not set, set start time of recording
        if (_modelStatsSnapshotsRecordState.value.startTime == 0L) {
            _modelStatsSnapshotsRecordState.update {
                it.copy(startTime = System.currentTimeMillis())
            }
        }

        _modelStatsSnapshotsRecordState.update {
            it.copy(
                snapshotsRecord = it.snapshotsRecord + snapshot
            )
        }

    }

    fun startSnapshotsRecording() {
        if (_modelStatsSnapshotsRecordState.value.recordingStatus == RecordingStatus.PROCESSING) {
            return
        } else if (_modelStatsSnapshotsRecordState.value.recordingStatus == RecordingStatus.RECORDING) {
            stopSnapshotsRecording()
        }

        resetRecording()

        _modelStatsSnapshotsRecordState.update {
            it.copy(
                recordingStatus = RecordingStatus.RECORDING,
                startTime = 0,
                endTime = 0,
                snapshotsRecord = emptyList()

            )
        }
    }

    fun resetRecording() {
        _modelStatsSnapshotsRecordState.update {
            it.copy(
                recordingStatus = RecordingStatus.NOT_RECORDING,
                startTime = 0,
                endTime = 0,
                totalTime = 0,
                avgFps = 0,
                avgModelInferenceTime = 0,
                snapshotsRecord = emptyList()
            )
        }
    }

    fun stopSnapshotsRecording() {
        val endTime = System.currentTimeMillis()

        if (_modelStatsSnapshotsRecordState.value.recordingStatus == RecordingStatus.PROCESSING) {
            return
        }
        if (_modelStatsSnapshotsRecordState.value.snapshotsRecord.isNotEmpty()) {
            ///check if we already stopped recording
            _modelStatsSnapshotsRecordState.update {
                it.copy(
                    endTime = endTime,
                    avgFps = it.snapshotsRecord.sumOf { snapshot -> snapshot.fps } / it.snapshotsRecord.size, //TODO: DIVISION BY ZERO
                    avgModelInferenceTime = it.snapshotsRecord.sumOf { snapshot -> snapshot.modelInferenceTime } / it.snapshotsRecord.size,//TODO: DIVISION BY ZERO
                    totalTime = endTime - it.startTime
                )
            }

            _modelStatsSnapshotsRecordState.update {
                it.copy(
                    recordingStatus = RecordingStatus.PROCESSING
                )
            }


            // after drawSnapshotsRecordingChart is done, set recordingStatus to NOT_RECORDING
            viewModelScope.launch {
                drawSnapshotsRecordingChart(
                    appContext,
                    _modelStatsSnapshotsRecordState.value
                )
                _modelStatsSnapshotsRecordState.update {
                    it.copy(
                        recordingStatus = RecordingStatus.NOT_RECORDING
                    )
                }
            }


        } else {
            _modelStatsSnapshotsRecordState.update {
                it.copy(
                    recordingStatus = RecordingStatus.NOT_RECORDING
                )
            }
        }

    }


    private fun drawSnapshotsRecordingChart(
        context: Context,
        record: ModelStatsSnapshotsRecordState
    ) {
        // https://stackoverflow.com/questions/2801116/converting-a-view-to-bitmap-without-displaying-it-in-android


        val lineChart = LineChart(context)

        lineChart.layoutParams = ViewGroup.LayoutParams(1200, 900)
        lineChart.setBackgroundColor(Color.WHITE)
        lineChart.description = Description().apply {
            text =
                "TfLite MobileNetV1_1.0(300x300) COCO Dataset, Measure time: ${record.snapshotsRecord.size - 1} s"
        }
        //convert record.snapshotsRecord List of fps and  modelInferenceTime to two listes of List<Entry>
        val fpsEntries = record.snapshotsRecord.mapIndexed { index, snapshot ->
            Entry(index.toFloat(), snapshot.fps.toFloat())
        }
        val inferenceTimeEntries = record.snapshotsRecord.mapIndexed { index, snapshot ->
            Entry(index.toFloat(), snapshot.modelInferenceTime.toFloat())
        }

        val fpsDataSet = LineDataSet(fpsEntries, "FPS")
        val inferenceDataSet = LineDataSet(inferenceTimeEntries, "Tf Model Inference Time (ms)")

        val avgFpsEntries = record.snapshotsRecord.mapIndexed { index, _ ->
            Entry(index.toFloat(), record.avgFps.toFloat())
        }
        val avgFpsData = LineDataSet(avgFpsEntries, "Avg FPS")

        val avgInferenceEntries = record.snapshotsRecord.mapIndexed { index, _ ->
            Entry(index.toFloat(), record.avgModelInferenceTime.toFloat())
        }
        val avgInferenceData = LineDataSet(avgInferenceEntries, "Avg Tf Model Inference Time (ms)")


        class SecondAxisValueFormatter : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "${value.toInt()}s"
            }
        }

        val xAxis = lineChart.xAxis
        xAxis.setLabelCount(record.snapshotsRecord.size, true)
        xAxis.valueFormatter = SecondAxisValueFormatter()
        lineChart.xAxis.granularity = 1f;
        lineChart.xAxis.axisMinimum = 0f;
        // Customize the appearance of the data sets as desired
        fpsDataSet.color = Color.RED
        inferenceDataSet.color = Color.BLUE
        avgFpsData.color = Color.GREEN
        avgInferenceData.color = Color.YELLOW
// Create a LineData object and set it to the chart
        val data = LineData(fpsDataSet, inferenceDataSet, avgFpsData, avgInferenceData)
        lineChart.data = data

// Finally, call the chart's invalidate() method to update the chart
        lineChart.invalidate()

        //Convert chart to bitmap
        lineChart.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        //log measuredWidth
        Log.d("measuredWidth", lineChart.measuredWidth.toString())
        val chartBitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(chartBitmap)
        lineChart.layout(0, 0, 1200, 900)
        lineChart.draw(canvas)

        //save chartBitmap to android gallery
        val chartFile =
            File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "chart.jpg")
        val fos = FileOutputStream(chartFile)


        Log.d(
            "chartFile",
            "saving chart to gallery: ${context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)}"
        )

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.TITLE, "chart.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        }
        val uri =
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            val out = context.contentResolver.openOutputStream(uri)
            if (out != null) {
                chartBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                out.flush()
                out.close()
            }

        }

    }

    private fun createBitmapFromLayout(tv: View): Bitmap? {
        val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        tv.measure(spec, spec)
        tv.layout(0, 0, tv.measuredWidth, tv.measuredHeight)
        val b = Bitmap.createBitmap(
            tv.measuredWidth, tv.measuredWidth,
            Bitmap.Config.ARGB_8888
        )
        val c = Canvas(b)
        c.translate((-tv.scrollX).toFloat(), (-tv.scrollY).toFloat())
        tv.draw(c)
        return b
    }
}

data class ModelStatsState(
    val totalTime: Long = 0,
    val fps: Int = 0, //fps
    val modelInferenceTime: Int = 0, //model inference time in ms
    val avgFps: Int = 0, //avg fps
    val avgModelInferenceTime: Int = 0, //avg inference time
    val uiRefreshRate: Int = 0, //number of UI frames that have been processed in the last second
    val confidence: Float = 0.5f,


    )


enum class RecordingStatus {
    NOT_RECORDING,
    RECORDING,
    PROCESSING
}

data class ModelStatsSnapshotsRecordState(
    val recordingStatus: RecordingStatus = RecordingStatus.NOT_RECORDING,
    val maxSnapshots: Int = 10,
    val startTime: Long = 0,
    val endTime: Long = 0,
    val totalTime: Long = 0,
    val avgFps: Int = 0, //avg fps
    val avgModelInferenceTime: Int = 0, //avg inference time
    val snapshotsRecord: List<ModelStatsSnapshot> = emptyList()
)


data class ModelStatsSnapshot(
    val fps: Int = 0,
    val modelInferenceTime: Int = 0,
)