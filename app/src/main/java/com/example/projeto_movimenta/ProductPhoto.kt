package com.example.projeto_movimenta

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

internal fun preparePhoto(open: () -> InputStream): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open().use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Imagem inválida.")
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 768) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val source = open().use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw IOException("Imagem inválida.")
    val orientation = try {
        open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    } catch (_: IOException) {
        ExifInterface.ORIENTATION_NORMAL
    }
    val transform = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
        }
    }
    val bitmap = Bitmap.createBitmap(source, 0, 0, source.width, source.height, transform, true)
    try {
        val output = ByteArrayOutputStream()
        for (quality in listOf(88, 75, 60, 45, 30, 15)) {
            output.reset()
            if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output) && output.size() in 1..262144) {
                return output.toByteArray()
            }
        }
        throw IOException("Não foi possível reduzir a imagem.")
    } finally {
        if (bitmap !== source) bitmap.recycle()
        source.recycle()
    }
}

@Composable
internal fun rememberProductPhoto(repository: InventoryRepository, product: Product?): ProductPhoto? {
    val photo by produceState<ProductPhoto?>(null, product?.id, product?.photoVersion) {
        value = null
        if (product != null && product.photoVersion != null) {
            try {
                value = withContext(Dispatchers.IO) { repository.photo(product.id) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // A imagem é opcional. O produto continua visível.
            }
        }
    }
    return photo
}

@Composable
internal fun ProductPhotoPreview(photo: ProductPhoto) {
    val bitmap = remember(photo.bytes) { BitmapFactory.decodeByteArray(photo.bytes, 0, photo.bytes.size)?.asImageBitmap() }
    if (bitmap != null) {
        Image(
            bitmap, contentDescription = "Foto do produto",
            modifier = Modifier.fillMaxWidth().height(160.dp), contentScale = ContentScale.Fit,
        )
    }
    photo.sourceCode?.let { ProductPhotoCredit(it) }
}

@Composable
internal fun ProductPhotoCredit(code: String) {
    val uriHandler = LocalUriHandler.current
    TextButton(onClick = { runCatching { uriHandler.openUri(OpenFoodFacts.sourcePage(code)) } }) {
        Text("Foto: Open Food Facts · CC BY-SA")
    }
}

@Composable
internal fun ProductThumbnail(repository: InventoryRepository, product: Product) {
    val photoVersion = product.photoVersion
    val bitmap by key(product.id, photoVersion) {
        produceState<ImageBitmap?>(null, repository, product.id, photoVersion) {
            if (photoVersion != null) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        val bytes = repository.photo(product.id)?.bytes ?: return@runCatching null
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        var sample = 1
                        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 192) sample *= 2
                        val options = BitmapFactory.Options().apply { inSampleSize = sample }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
                    }.getOrNull()
                }
            }
        }
    }
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier.size(64.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(image, "Foto de ${product.name}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                painterResource(R.drawable.ic_add_product), contentDescription = null,
                modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
