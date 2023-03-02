
# Jetpack Compose TensorFlow Lite Object Detection Demo App

<center>

![](./docs/app.gif)

</center>

Demo application for MobileNet SSD deep learning object detection sample model supporting MobileNet SSD ready model in TFlite format, which was trained on COCO (Common object) dataset and contains 80 object categories. This model is publicly available on TF Hub.

The application detects objects in the camera's captured frames in real time and displays detection frames (called bboxes) on the current camera view with the ability to track telemetry data regarding model speed and performance.

The application is also a technological demo, the source code of the application and its architecture are to make it easier for programmers to create applications that support Object Detection on the Android platform, or adapt the current application to other TFLite models.

As at January 31, 2023 there is no official Tensorflow demo app for real-time object detection on Android using these libraries simultaneously in Kotlin.


# Functionalities

- Displaying the current camera view along with drawing object frames
    
- Real-time performance data display
    
    - FPS – the number of frames per second supported by the camera and the model
    - Model Inference Time – sum of camera frame conversion time to model input data and model interpreter response time
    - UI Refresh rate – the number of refreshes of drawing detection frames per second in the Ui component responsible for camera preview (CameraPreview.kt).
    - Average stats: FPS and Model Inference Time
- Frames are to be drawn according to the mAP threshold (average accuracy of the detected object), adjustable dynamically by the user via the UI interface
    
- Ability to change the number of frames per second provided by the camera by the user using the UI interface
    
- Ability to record performance stats telemetry
    
- Generating a telemetry graph in the form of a .png file for the user's photo gallery

# Technologies

- Application platform**: Android (minSdk 30)**
- Programming language: **Kotlin 1.8.0**
- A set of libraries for creating a UI in a declarative way: **Android Jetpack Compose: 1.1.1**
- Android Jetpack Compose UI Components**: Material Design 1.1.1**
- Icons**: Material Design Icons 1.1.1**
- API for camera and frame analysis: **CameraX: 1.2.0**
- ML OD (Machine learning Object Detection) support: **TensorFlow Lite 2.11.0**
- Chart generation: **MPAndroidChart 3.10**
- Package Manager: **Gradle**


The application consists of the main module **com.giganbyte.jetpackcomposetfobjectdetection,** which consists of the following sub-modules and classes:

- UI module - pairs of View + ViewModel classes grouped by views (screens), along with the main view, which manages the other views and acts as a menu.
    
    - CameraPreview.kt view with the ViewModel class: PreviewViewModel.kt responsible for handling the preview and running data analysis from the camera to the TFLite model
    - Main view of the so-called A skeleton (Scaffold) for embedding views: BottomSheetScaffold.kt with the ViewModel class, responsible for the layout of the application
    - Shared ViewModel class ModelStatsViewModel.kt responsible for managing the state related to the state of current statistics and generating a telemetry record with export to the chart.
- Utils module - contains classes that do not contain @Composable functions. These classes are used by the ViewModel classes in the UI module, most of them related to integration with the Tensorflow Lite framework.
    
- kt – contains application start point, main activity with permission requests
    

The TFLite model with a text file containing object category labels is located in the Assets folder

# Sources

1.  TensorFlow Lite: [https://www.tensorflow.org/lite?hl=pl](https://www.tensorflow.org/lite?hl=pl)
2.  Android Jetpack: [https://developer.android.com/jetpack](https://developer.android.com/jetpack)
3.  CameraX: [https://developer.android.com/training/camerax](https://developer.android.com/training/camerax)
4.  COCO – Common objects in Context: [https://cocodataset.org/#home](https://cocodataset.org/#home)
5.  Model TensorFlow Lite MobileNet SSD COCO: [https://tfhub.dev/tensorflow/lite-model/ssd_mobilenet_v1/1/metadata/2](https://tfhub.dev/tensorflow/lite-model/ssd_mobilenet_v1/1/metadata/2)
6.  Official TensorFlow Android demo: [https://www.tensorflow.org/lite/android/tutorials/object_detection?hl=pl](https://www.tensorflow.org/lite/android/tutorials/object_detection?hl=pl)