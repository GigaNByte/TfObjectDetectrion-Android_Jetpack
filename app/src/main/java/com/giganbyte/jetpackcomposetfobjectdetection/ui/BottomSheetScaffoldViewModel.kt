package com.giganbyte.jetpackcomposetfobjectdetection

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class BottomSheetScaffoldViewModel : ViewModel() {

    private val _scaffoldState = MutableStateFlow(BottomSheetScaffoldState())
    val scaffoldState: StateFlow<BottomSheetScaffoldState> = _scaffoldState.asStateFlow()

    fun toggleBottomSheetSelector() {
        _scaffoldState.update {
            it.copy(isBottomSheetSelectorExpanded = !it.isBottomSheetSelectorExpanded)
        }
    }

    fun updateModel(model: Model) {
        _scaffoldState.update {
            it.copy(currentModel = model)
        }
    }

}

data class BottomSheetScaffoldState(
    val isBottomSheetSelectorExpanded: Boolean = false,
    val isBottomSheetExpanded: Boolean = false,

    //TODO: move it to new  viewmodel
    val models: List<Model> = Models.values().toList(),
    val currentModel: Model = models.first(),
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


//Models (This should be in some kind of repository)
//TODO: implement Interface to manage models and cooperate with ObjectDetectionHelper
object Models {
    private val list = listOf<Model>(
        //Model("EfficientDet Lite0", "lite-model_efficientdet_lite2_detection_metadata_1.tflite", "coco_ssd_mobilenet_v1_1.0_labels.txt"),
        Model(
            "MobileNet SSD",
            "coco_ssd_mobilenet_v1_1.0_quant.tflite",
            "coco_ssd_mobilenet_v1_1.0_labels.txt"
        ),
    )
    fun values() = list
}

