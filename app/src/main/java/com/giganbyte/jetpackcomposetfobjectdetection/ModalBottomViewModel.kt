package com.giganbyte.jetpackcomposetfobjectdetection

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ModalBottomViewModel : ViewModel() {

    private val _modalState = MutableStateFlow(ModalBottomState())
    val modalState: StateFlow<ModalBottomState> = _modalState.asStateFlow()


    fun onModelSelectorDismissed() {
        _modalState.update {
            it.copy(isModelSelectorExpanded = false)
        }
    }

    //onModelSelected
    fun onModelSelected(model: Model) {
        _modalState.update {
            it.copy(currentModel = model)
        }
    }

}


//create modalBottomState data class
data class ModalBottomState(

    val isModalSelectorExpanded: Boolean = true,
    val isModelSelectorExpanded: Boolean = true,
    val models: List<Model> = Models.values().toList(),
    val currentModel: Model = models.first(),
    val currentModelResolution: Resolution = Resolution(0,0),

)

data class Model(
    val name: String,
    val path: String,
    val labelsPath: String
)

data class Resolution(
    val width: Int,
    val height: Int
)


//Models but do not use enums but associative list (This should be in some kind of repository)
object Models {
    private val list = listOf<Model>(
        Model("MobileNet SSD", "coco_ssd_mobilenet_v1_1.0_quant.tflite", "coco_ssd_mobilenet_v1_1.0_labels.txt"),
        Model("YOLO V4 416 FP32", "yolov4-tiny-custom_2000_tflite.tflite", "yolov4.txt"),
        Model("YOLO V4 416 FP16", "yolov4-tiny-custom_2000_tflite.tflite", "yolov4.txt"),
    )
    fun values() = list
}

