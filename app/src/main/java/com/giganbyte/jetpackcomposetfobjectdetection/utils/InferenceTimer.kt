package com.giganbyte.jetpackcomposetfobjectdetection.utils


import android.os.SystemClock

class InferenceTimer() {
    private var hasStarted = false
    private var startTime: Long = 0
    private var endTime: Long = 0
    private var totalTime: Long = 0
    private var count: Int = 0
    fun startMeasurement() {
        startTime = SystemClock.elapsedRealtime()
        hasStarted = true
    }

    fun stopMeasurement(): Long {
        if (hasStarted) {
            endTime = SystemClock.elapsedRealtime()
            val inferenceTime = endTime - startTime
            totalTime += inferenceTime
            count++
            hasStarted = false
            return inferenceTime
        } else {
            return 0
        }
    }

    fun getAvgInferenceTime(): Long {
        return if (count == 0) {
            0
        } else {
            totalTime / count
        }
    }

    //get total time
    fun getTotalTime(): Long {
        return totalTime
    }

    //reset timer
    fun reset() {
        startTime = 0
        endTime = 0
        totalTime = 0
        count = 0
    }
}