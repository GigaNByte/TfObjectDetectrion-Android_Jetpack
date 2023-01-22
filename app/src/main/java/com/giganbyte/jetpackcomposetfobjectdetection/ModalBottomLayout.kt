package com.giganbyte.jetpackcomposetfobjectdetection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.MaterialTheme.colors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

//create composable ModalBottomLayout

@OptIn(ExperimentalMaterialApi::class)

@Composable
fun ModalBottomLayout(
    content: @Composable () -> Unit,

){
     val modalViewModel: ModalBottomViewModel = viewModel()
     val modalState by modalViewModel.modalState.collectAsState()

    val previewViewModel: PreviewViewModel = viewModel()
    val previewState by previewViewModel.previewState.collectAsState()


    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()

    //BottomSheetScaffold from material design library for displaying fps and other info

    BottomSheetScaffold(
        sheetContent = {
            Box(
                Modifier.fillMaxWidth().height(128.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Swipe up to expand sheet")
            }
            Column(
                Modifier.fillMaxWidth().padding(64.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                //fps
                Text("FPS: ${previewState.fps}")
                //model
                Text("Model: ${modalState.currentModel.name}")
                //model resolution
                Text("Model resolution: ${previewState.modelResolution.width}x${previewState.modelResolution.height}")
                //preview resolution
                Text("Preview resolution: ${previewState.previewSize.width}x${previewState.previewSize.height}")
                Text("Sheet content")
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        scope.launch { scaffoldState.bottomSheetState.collapse() }
                    }
                ) {
                    Text("Click to collapse sheet")
                }
            }
        },
        scaffoldState = scaffoldState,
        topBar = {
            TopAppBar(
                title = { Text("Bottom sheet scaffold") },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { scaffoldState.drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Localized description")
                    }
                }
            )
        },
        floatingActionButton = {
            var clickCount by remember { mutableStateOf(0) }
            FloatingActionButton(
                onClick = {
                    // show snackbar as a suspend function
                    scope.launch {
                        scaffoldState.snackbarHostState.showSnackbar("Snackbar #${++clickCount}")
                    }
                }
            ) {
                Icon(Icons.Default.Favorite, contentDescription = "Localized description")
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        sheetPeekHeight = 128.dp,
        drawerContent = {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Drawer content")
                Spacer(Modifier.height(20.dp))
                Button(onClick = { scope.launch { scaffoldState.drawerState.close() } }) {
                    Text("Click to close drawer")
                }
            }
        }
    ){
        Box {
            content()
        }

    }
}