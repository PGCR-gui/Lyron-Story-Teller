package gui.pgcr.storyteller

import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gui.pgcr.storyteller.ui.theme.MyComposeApplicationTheme
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun retainImageReadPermission(context: Context, uri: Uri): Boolean {
    return runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.isSuccess
}

internal fun decodeStoredImage(context: Context, uriString: String, maxDimension: Int = 1600): Bitmap? {
    return try {
        val uri = Uri.parse(uriString)
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    } catch (_: Exception) {
        null
    }
}

@Composable
internal fun RemoteCoverImage(
    coverPath: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    /** Ex.: updatedAt da história; entra na chave do cache para uma capa trocada não ficar velha. */
    version: String? = null,
    /** Maior lado decodificado: miniaturas usam valores menores (menos memória), a capa dos detalhes usa o padrão. */
    maxDimension: Int = 1024
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, coverPath, version, maxDimension) {
        // memória → disco → rede (veja OfflineCache.kt); funciona sem internet depois da 1ª vez.
        value = if (coverPath.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching { loadCoverBitmap(context, coverPath, version, maxDimension) }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = contentScale)
    } else {
        DefaultCoverPlaceholder(modifier)
    }
}

@Composable
internal fun StoredImage(
    uri: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    maxDimension: Int = 1400
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri, maxDimension) {
        value = withContext(Dispatchers.IO) { decodeStoredImage(context, uri, maxDimension) }
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(bitmap = loaded.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = contentScale)
    } else {
        Box(
            modifier = modifier.background(SurfaceAlt, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) { Text("▧", color = Accent, fontSize = 28.sp) }
    }
}

/** Foto de perfil quadrada (corte central), 512 px no máximo, em JPEG — leve para subir ao bucket. */
internal fun avatarJpegBytes(context: Context, uriString: String): ByteArray? {
    val source = decodeStoredImage(context, uriString, 512) ?: return null
    val size = minOf(source.width, source.height)
    val square = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(square)
    canvas.drawColor(android.graphics.Color.WHITE)
    canvas.drawBitmap(source, -((source.width - size) / 2f), -((source.height - size) / 2f), null)
    val output = ByteArrayOutputStream()
    square.compress(Bitmap.CompressFormat.JPEG, 85, output)
    return output.toByteArray()
}

/** Pasta da foto de perfil do próprio usuário (armazenamento privado do app). */
private fun ownAvatarDir(context: Context): File = File(context.filesDir, "avatars").apply { mkdirs() }

/**
 * Guarda a foto de perfil DO USUÁRIO no armazenamento privado e devolve a Uri (nome novo a cada vez).
 * Não apaga mais nada aqui: o arquivo em uso só é removido por [pruneOwnAvatars], depois que o
 * perfil já aponta para o arquivo novo. Antes, esta função limpava a pasta inteira e, como o perfil
 * de autor também a usava, abrir qualquer perfil apagava a foto do próprio usuário.
 */
internal fun storeAvatarLocally(context: Context, bytes: ByteArray): String {
    val file = File(ownAvatarDir(context), "avatar_${System.currentTimeMillis()}.jpg")
    file.writeBytes(bytes)
    return Uri.fromFile(file).toString()
}

/** Remove fotos antigas do usuário, mantendo só a que o perfil usa agora ([keepUri]). */
internal fun pruneOwnAvatars(context: Context, keepUri: String?) {
    runCatching {
        ownAvatarDir(context).listFiles()?.forEach { f ->
            if (Uri.fromFile(f).toString() != keepUri) f.delete()
        }
    }
}

/**
 * Foto de OUTRO autor: fica no cache (o sistema pode limpar), com um arquivo por caminho do bucket,
 * sem mexer na foto do usuário.
 */
internal fun storeAuthorAvatarCached(context: Context, remotePath: String, bytes: ByteArray): String {
    val dir = File(context.cacheDir, "author_avatars").apply { mkdirs() }
    val file = File(dir, "a_${remotePath.hashCode().toUInt().toString(16)}.jpg")
    file.writeBytes(bytes)
    return Uri.fromFile(file).toString()
}

/** Copia a foto escolhida na galeria para o armazenamento privado (quadrada, 512 px, JPEG). */
internal fun importPickedAvatar(context: Context, pickedUri: String): String? {
    val bytes = avatarJpegBytes(context, pickedUri) ?: return null
    return runCatching {
        val file = File(ownAvatarDir(context), "avatar_${System.currentTimeMillis()}.jpg")
        file.writeBytes(bytes)
        Uri.fromFile(file).toString()
    }.getOrNull()
}

/** true se a imagem ainda pode ser lida (arquivo existe / permissão da Uri continua valendo). */
internal fun isImageReadable(context: Context, uriString: String): Boolean {
    return runCatching {
        context.contentResolver.openInputStream(Uri.parse(uriString))?.use { true } ?: false
    }.getOrDefault(false)
}
