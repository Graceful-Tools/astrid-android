package com.gracefultools.astrid.ui.detail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.gracefultools.astrid.model.FileRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decode a picture no larger than it is drawn: a phone photo is 12 MP, a thumbnail is not. */
object Thumbnails {
    fun sampleSize(width: Int, height: Int, wanted: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= wanted && height / (sample * 2) >= wanted) sample *= 2
        return sample
    }

    fun decode(path: String, wanted: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, wanted) })
    }.getOrNull()
}

/**
 * A comment's picture, drawn where it arrived (iOS draws a thumbnail too). Downloaded through the
 * core the first time it is seen; tapping opens it full size in another app.
 */
@Composable
fun InlineImage(file: FileRow, fetch: (String, (String) -> Unit) -> Unit, onOpen: () -> Unit) {
    var path by remember(file.id) { mutableStateOf(file.localPath) }
    var bitmap by remember(file.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file.id) { if (path == null) fetch(file.id) { path = it } }
    LaunchedEffect(path) { path?.let { p -> bitmap = withContext(Dispatchers.IO) { Thumbnails.decode(p, 600) } } }
    bitmap?.let {
        Image(
            it.asImageBitmap(), contentDescription = file.name, contentScale = ContentScale.Crop,
            modifier = Modifier.padding(top = 6.dp).widthIn(max = 260.dp).heightIn(max = 200.dp)
                .clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpen),
        )
    }
}
