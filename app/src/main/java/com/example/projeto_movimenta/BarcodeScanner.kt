package com.example.projeto_movimenta

import android.view.ViewOutlineProvider
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
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
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
internal fun BarcodeScannerDialog(onResult: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalView.current.findViewTreeLifecycleOwner()
    val latestResult by rememberUpdatedState(onResult)
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            outlineProvider = ViewOutlineProvider.BOUNDS
            clipToOutline = true
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(context, lifecycle, previewView) {
        val executor = ContextCompat.getMainExecutor(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_ITF, Barcode.FORMAT_CODE_128,
            )
            .build()
        val scanner = BarcodeScanning.getClient(options)
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        val preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
        var provider: ProcessCameraProvider? = null
        var active = true
        var accepted = false

        analysis.setAnalyzer(executor) { frame ->
            val image = frame.image
            if (image == null || !active || accepted) {
                frame.close()
            } else {
                try {
                    scanner.process(InputImage.fromMediaImage(image, frame.imageInfo.rotationDegrees))
                        .addOnSuccessListener { codes ->
                            val value = codes.firstNotNullOfOrNull { code ->
                                code.rawValue?.takeIf(StockRules::isBarcode)
                            }
                            if (active && !accepted && value != null) {
                                accepted = true
                                latestResult(value)
                            }
                        }
                        .addOnFailureListener {
                            if (active) error = "Não foi possível ler o código. Você pode digitá-lo."
                        }
                        .addOnCompleteListener { frame.close() }
                } catch (_: Exception) {
                    frame.close()
                    if (active) error = "Não foi possível ler o código. Você pode digitá-lo."
                }
            }
        }
        providerFuture.addListener({
            if (active) {
                try {
                    val cameraProvider = providerFuture.get()
                    provider = cameraProvider
                    cameraProvider.bindToLifecycle(requireNotNull(lifecycle), CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (_: Exception) {
                    error = "Câmera indisponível. Você pode digitar o código."
                }
            }
        }, executor)

        onDispose {
            active = false
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            scanner.close()
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Escanear código de barras") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val message = error
                if (message == null) {
                    Text("Aponte a câmera para o código de barras.")
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().height(240.dp).clipToBounds())
                } else {
                    Text(message)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Fechar leitor") } },
    )
}
