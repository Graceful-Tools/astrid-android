package com.gracefultools.astrid.ui.common

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A picked photo copied where the core can read it; the core uploads it. */
private suspend fun copyPhoto(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val type = context.contentResolver.getType(uri)
        val ext = when (type) { "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> "jpg" }
        val file = java.io.File(context.cacheDir, "picked-photo-${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        file.absolutePath
    }.getOrNull()
}

/**
 * The system photo picker; answers a path the core can upload. Your own profile photo
 * (`updateProfile`) and a Custom Agent's (`setCustomAgentPhoto`, AATD-95) both take one.
 */
@Composable
internal fun rememberPhotoPicker(onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { copyPhoto(context, uri)?.let(onPicked) }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}
