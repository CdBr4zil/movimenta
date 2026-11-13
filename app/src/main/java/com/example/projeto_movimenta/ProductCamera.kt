package com.example.projeto_movimenta

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewOutlineProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun rememberCameraAccess(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onGranted() else onDenied()
    }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
}

@Composable
internal fun ProductCameraDialog(onUse: (ByteArray) -> Unit, onGallery: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalView.current.findViewTreeLifecycleOwner()
    val scope = rememberCoroutineScope()
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val view = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            outlineProvider = ViewOutlineProvider.BOUNDS
            clipToOutline = true
        }
    }
    val capture = remember(view) { ImageCapture.Builder().build() }
    var ready by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var active by remember { mutableStateOf(false) }

    DisposableEffect(view, lifecycle) {
        active = true
        val preview = Preview.Builder().build().apply { surfaceProvider = view.surfaceProvider }
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            if (active) {
                try {
                    provider = future.get().also {
                        it.bindToLifecycle(requireNotNull(lifecycle), CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                    }
                    ready = true
                } catch (_: Exception) {
                    cameraError = "Câmera indisponível. Você pode escolher uma foto da galeria."
                }
            }
        }, executor)
        onDispose {
            active = false
            provider?.unbind(preview, capture)
        }
    }

    fun takePhoto() {
        val file = try {
            File.createTempFile("movimenta-photo-", ".jpg", context.cacheDir)
        } catch (_: Exception) {
            cameraError = "Não foi possível preparar a captura."
            return
        }
        capturing = true
        cameraError = null
        try {
            view.display?.let { capture.targetRotation = it.rotation }
            capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        if (!active) {
                            file.delete()
                            return
                        }
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            try {
                                bytes = withContext(Dispatchers.IO) { preparePhoto { file.inputStream() } }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                cameraError = "Não foi possível preparar a foto. Tente novamente ou use a galeria."
                            } finally {
                                file.delete()
                                capturing = false
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        if (active) {
                            capturing = false
                            cameraError = "Não foi possível tirar a foto. Tente novamente ou use a galeria."
                        }
                    }
                },
            )
        } catch (_: Exception) {
            file.delete()
            capturing = false
            cameraError = "Não foi possível tirar a foto. Tente novamente ou use a galeria."
        }
    }

    val photo = bytes
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (photo == null) "Câmera do produto" else "Conferir foto") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (photo != null) ProductPhotoPreview(ProductPhoto(photo))
                else if (ready || cameraError == null) {
                    AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().height(240.dp).clipToBounds())
                }
                cameraError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (cameraError != null) TextButton(onClick = onGallery) { Text("Escolher da galeria") }
            }
        },
        confirmButton = {
            if (photo != null) TextButton(onClick = { onUse(photo) }) { Text("Usar foto") }
            else TextButton(onClick = ::takePhoto, enabled = ready && !capturing) {
                Text(if (capturing) "Capturando…" else "Capturar")
            }
        },
        dismissButton = {
            if (photo != null) TextButton(onClick = { bytes = null }) { Text("Refazer") }
            else TextButton(onClick = onClose) { Text("Cancelar") }
        },
    )
}
