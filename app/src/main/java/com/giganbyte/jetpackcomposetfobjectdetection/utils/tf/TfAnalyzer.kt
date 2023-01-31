package com.giganbyte.jetpackcomposetfobjectdetection.utils.tf


import android.content.Context
import android.graphics.Bitmap
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
import com.giganbyte.jetpackcomposetfobjectdetection.Model
import com.giganbyte.jetpackcomposetfobjectdetection.ModelStatsViewModel
import com.giganbyte.jetpackcomposetfobjectdetection.Resolution
import org.tensorflow.lite.support.image.TensorImage


class TfAnalyzer(
    context: Context,
    model: Model,
    private val updateDetections: (List<ObjectDetectionHelper.ObjectPrediction>) -> Unit,
    private val updateModelResolution: (Resolution) -> Unit,
    private val modelStatsViewModel: ModelStatsViewModel
) : ImageAnalysis.Analyzer {

    private lateinit var bitmapBuffer: Bitmap
    private var imageRotationDegrees: Int =
        0 //TODO this should be from CameraState now is hardcoded , there is suspicion that TFanalizer image should be rotated 180 degrees after image debug analysis
    private var pauseAnalysis =
        false //TODO this should be mutable in composable or not? Investigate use case
    private val tfImageBuffer = TensorImage(DataType.UINT8)
    private val tfImageProcessor by lazy {
        val cropSize = minOf(
            bitmapBuffer.width,
            bitmapBuffer.height
        )   // what is the size of the image we want to crop to
        ImageProcessor.Builder() //tensorflow image processor
            .add(
                ResizeWithCropOrPadOp(
                    cropSize,
                    cropSize
                )
            ) //crop the image to the size of the model
            .add(
                ResizeOp(
                    tfInputSize.height, tfInputSize.width, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR
                )
            )
            .add(Rot90Op(imageRotationDegrees / 90))
            .add(NormalizeOp(0f, 1f))
            .build()
    }

    private val tflite by lazy {
        Interpreter(
            FileUtil.loadMappedFile(context, model.path),
            Interpreter.Options().addDelegate(NnApiDelegate())
        )
    }

    private val detector by lazy {
        ObjectDetectionHelper(tflite, FileUtil.loadLabels(context, model.labelsPath))
    }

    private val tfInputSize by lazy {
        val inputIndex = 0
        val inputShape = tflite.getInputTensor(inputIndex).shape()

        updateModelResolution(Resolution(inputShape[2], inputShape[1]))
        Size(inputShape[2], inputShape[1]) // Order of axis is: {1, height, width, 3}
        // Size(inputShape[1], inputShape[2])
    }

    //use diferent YuvToRgbConverter for different android versions
    //TODO: test cameraX YuvToRgbConverter and remove this
    private val converter = YuvToRgbConverter(context)


    //@OptIn(ExperimentalTime::class)
    @ExperimentalGetImage
    override fun analyze(image: ImageProxy) {

        modelStatsViewModel.startModelInferenceTimeMeasurement()

        if (!::bitmapBuffer.isInitialized) {
            // The image rotation and RGB image buffer are initialized only once
            // the analyzer has started running
            imageRotationDegrees = image.imageInfo.rotationDegrees
            bitmapBuffer = Bitmap.createBitmap(
                image.width, image.height, Bitmap.Config.ARGB_8888
            )
        }

        // Early exit: image analysis is in paused state
        if (pauseAnalysis) {
            image.close()
        }

        // Convert the image to RGB and place it in our shared buffer
        image.use { converter.yuvToRgb(image.image!!, bitmapBuffer) }

        // Process the image in Tensorflow

        val tfImage = tfImageProcessor.process(tfImageBuffer.apply { load(bitmapBuffer) })
/*
        //debug: save image to file, save every 10th frame
        if (fileCounter %10 == 0) {
            Log.d("TFLITE_TEST", "Saving image to file")

            val bitmap = tfImage.bitmap
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "TFLITE_TEST_image.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/TFLITE_TEST")
            }
            val contentUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            val outputStream = contentUri?.let { context.contentResolver.openOutputStream(it) }
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream?.close()
            fileCounter++

        }

        fileCounter++;
*/


        // Perform the object detection for the current frame
        val predictions = detector.predict(tfImage)


        //Log.d("TfAnalyzer", "predictions: $predictions")

        //save predictions to Detection of DetectionsViewModel
        updateDetections(predictions)
        modelStatsViewModel.stopModelInferenceTimeMeasurement()
        modelStatsViewModel.updateFpsCounter()

        image.close()
    }

    companion object {
        private val TAG = TfAnalyzer::class.java.simpleName
    }
}
