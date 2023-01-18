package com.giganbyte.jetpackcomposetfobjectdetection


import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.camera.core.ExperimentalGetImage

import androidx.camera.core.ImageAnalysis
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.support.common.ops.NormalizeOp

import androidx.camera.core.ImageProxy
import org.tensorflow.lite.support.image.TensorImage


class TfAnalyzer(context: Context, private val updateDetections: (List<ObjectDetectionHelper.ObjectPrediction>) -> Unit ) : ImageAnalysis.Analyzer {
    private lateinit var bitmapBuffer: Bitmap
    private var imageRotationDegrees: Int = 0
    private var pauseAnalysis = false //WARN this should be mutable in composable
    private val tfImageBuffer = TensorImage(DataType.UINT8)
    private val tfImageProcessor by lazy { // Lazy so that we only initialize it when we need it
         val cropSize = minOf(bitmapBuffer.width, bitmapBuffer.height)// what is the size of the image we want to crop to
         ImageProcessor.Builder() //tensorflow image processor
             .add(ResizeWithCropOrPadOp(cropSize, cropSize))
             .add(ResizeOp(
                 tfInputSize.height, tfInputSize.width, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
             .add(Rot90Op(imageRotationDegrees / 90))
             .add(NormalizeOp(0f, 1f))
             .build()
    }

    private val tflite by lazy {
         Interpreter(
             FileUtil.loadMappedFile(context, MODEL_PATH),
             Interpreter.Options().addDelegate(NnApiDelegate()))
    }

    private val detector by lazy {
         ObjectDetectionHelper(tflite, FileUtil.loadLabels(context, LABELS_PATH))
    }

     private val tfInputSize by lazy {
         val inputIndex = 0
         val inputShape = tflite.getInputTensor(inputIndex).shape()
         Size(inputShape[2], inputShape[1]) // Order of axis is: {1, height, width, 3}
     }
    //use diferent YuvToRgbConverter for different android versions

     private val converter = YuvToRgbConverter(context)
     private var frameCounter = 0
     private var lastFpsTimestamp = System.currentTimeMillis()

    @ExperimentalGetImage
    override fun analyze(image: ImageProxy) {
        if (!::bitmapBuffer.isInitialized) {
            // The image rotation and RGB image buffer are initialized only once
            // the analyzer has started running
            imageRotationDegrees = image.imageInfo.rotationDegrees
            bitmapBuffer = Bitmap.createBitmap(
                image.width, image.height, Bitmap.Config.ARGB_8888)
        }

        // Early exit: image analysis is in paused state
        if (pauseAnalysis) {
            image.close()
        }

        // Convert the image to RGB and place it in our shared buffer
        image.use { converter.yuvToRgb(image.image!!, bitmapBuffer) }

        // Process the image in Tensorflow
        val tfImage =  tfImageProcessor.process(tfImageBuffer.apply { load(bitmapBuffer) })

        // Perform the object detection for the current frame
        val predictions = detector.predict(tfImage)
        //log predictions
        Log.d("TfAnalyzer", "predictions: $predictions")

        //save predictions to Detection of DetectionsViewModel
        updateDetections(predictions)

        // Compute the FPS of the entire pipeline
        val frameCount = 10
        if (++frameCounter % frameCount == 0) {
            frameCounter = 0
            val now = System.currentTimeMillis()
            val delta = now - lastFpsTimestamp
            val fps = 1000 * frameCount.toFloat() / delta
            Log.d(TAG, "FPS: ${"%.02f".format(fps)}")
            lastFpsTimestamp = now
        }

        image.close()
    }

    companion object {
        private val TAG = TfAnalyzer::class.java.simpleName
        private const val ACCURACY_THRESHOLD = 0.5f
        private const val MODEL_PATH = "coco_ssd_mobilenet_v1_1.0_quant.tflite"
        private const val LABELS_PATH = "coco_ssd_mobilenet_v1_1.0_labels.txt"
    }
}
