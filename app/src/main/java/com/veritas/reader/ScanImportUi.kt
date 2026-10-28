package com.veritas.reader

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberScanImportAction(onImageSelected: (Uri) -> Unit, onError: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    var showSources by rememberSaveable { mutableStateOf(false) }
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onImageSelected(it)
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val path = pendingPhoto
        pendingPhoto = null
        if (path != null) {
            try {
                ScanCaptureStore.finish(context, path, captured)?.let(onImageSelected)
            } catch (error: Exception) {
                ScanCaptureStore.discard(context, Uri.fromFile(File(path)))
                onError("Could not read the photo. Please try again.")
            }
        }
    }
    if (showSources) {
        ScanImportSourceSheet(
            onDismiss = { showSources = false },
            onCapture = {
                showSources = false
                try {
                    val file = ScanCaptureStore.create(context)
                    pendingPhoto = file.absolutePath
                    camera.launch(ScanCaptureStore.cameraUri(context, file))
                } catch (error: Exception) {
                    pendingPhoto?.let { ScanCaptureStore.discard(context, Uri.fromFile(File(it))) }
                    pendingPhoto = null
                    onError("Could not open the camera. You can choose an existing photo instead.")
                }
            },
            onChoosePhoto = {
                showSources = false
                try {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } catch (error: Exception) {
                    onError("Could not open photos. Please try the file browser.")
                }
            }
        )
    }
    return { showSources = true }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScanImportSourceSheet(onDismiss: () -> Unit, onCapture: () -> Unit, onChoosePhoto: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Scan / OCR", style = MaterialTheme.typography.titleLarge)
            Text("Extract printed text from a new or existing photo.", style = MaterialTheme.typography.bodyMedium)
            Surface(onClick = onCapture, shape = MaterialTheme.shapes.large) {
                ListItem(headlineContent = { Text("Take photo") },
                    supportingContent = { Text("Capture a page with your camera") },
                    leadingContent = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) })
            }
            Surface(onClick = onChoosePhoto, shape = MaterialTheme.shapes.large) {
                ListItem(headlineContent = { Text("Choose photo") },
                    supportingContent = { Text("Select an image from Photos or Collections") },
                    leadingContent = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) })
            }
        }
    }
}
