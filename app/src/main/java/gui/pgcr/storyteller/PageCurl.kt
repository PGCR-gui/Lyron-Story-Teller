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

@Composable
internal fun BookSheetCard(
    text: String,
    fontSize: Float,
    lineHeightMult: Float = 1.55f,
    pageMarginDp: Float = 20f,
    fontFamily: FontFamily,
    theme: ReaderThemeColors,
    shadowIntensity: Float
) {
    // Página limpa: sem contador interno (fica só no rodapé) e sem bordas
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface)
    ) {
        Text(
            text = text,
            color = theme.text,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * lineHeightMult).sp,
            fontFamily = fontFamily,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = pageMarginDp.dp, vertical = 16.dp)
        )
        if (shadowIntensity > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = shadowIntensity))
            )
        }
    }
}

/** Shape que recorta a página atual exatamente até a linha de dobra dinâmica. */
internal class PageCurlClipShape(
    private val progress: Float,
    private val touchYRatio: Float
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val w = size.width
        val h = size.height
        val foldX = w * (1f - progress * 1.15f)
        val tilt = (touchYRatio - 0.5f) * h * 0.38f * sin(progress * Math.PI).toFloat()
        val topX = (foldX - tilt * 0.5f).coerceIn(-w * 0.5f, w * 1.5f)
        val bottomX = (foldX + tilt * 0.5f).coerceIn(-w * 0.5f, w * 1.5f)
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(topX, 0f)
            lineTo(bottomX, h)
            lineTo(0f, h)
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

/** Desenha o verso enrolado da folha, o cilindro com reflexo de luz e a sombra projetada. */
@Composable
internal fun PageCurlOverlay(
    progress: Float,
    touchYRatio: Float,
    pageColor: Color,
    textColor: Color,
    previewText: String,
    fontSize: Float,
    lineHeightMult: Float = 1.55f,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val backPaperColor = lerp(pageColor, Color.White, 0.14f)
    val foldHighlight = lerp(pageColor, Color.White, 0.35f)
    val foldCrease = lerp(pageColor, Color.Black, 0.22f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val foldX = w * (1f - progress * 1.15f)
        val tilt = (touchYRatio - 0.5f) * h * 0.38f * sin(progress * Math.PI).toFloat()
        val topFoldX = foldX - tilt * 0.5f
        val bottomFoldX = foldX + tilt * 0.5f

        val curlGrow = (progress / 0.08f).coerceIn(0f, 1f)
        val curlWidth = (w * 0.34f * sin((progress * 0.85f + 0.1f) * Math.PI).toFloat() * curlGrow).coerceAtLeast(18f * curlGrow)
        val backTopX = topFoldX - curlWidth
        val backBottomX = bottomFoldX - curlWidth

        // 1. Sombra projetada pela folha dobrada sobre a página seguinte (à direita da dobra)
        val shadowWidth = curlWidth * 0.85f
        val shadowPath = Path().apply {
            moveTo(topFoldX, 0f)
            lineTo(topFoldX + shadowWidth, 0f)
            lineTo(bottomFoldX + shadowWidth, h)
            lineTo(bottomFoldX, h)
            close()
        }
        drawPath(
            path = shadowPath,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.28f * (1f - progress * 0.5f)),
                    Color.Transparent
                ),
                startX = minOf(topFoldX, bottomFoldX),
                endX = maxOf(topFoldX, bottomFoldX) + shadowWidth
            )
        )

        // 2. Verso da folha (cilindro enrolado com gradiente cilíndrico realista)
        val backPath = Path().apply {
            moveTo(backTopX, 0f)
            lineTo(topFoldX, 0f)
            lineTo(bottomFoldX, h)
            lineTo(backBottomX, h)
            close()
        }
        val minBackX = minOf(backTopX, backBottomX)
        val maxFoldX = maxOf(topFoldX, bottomFoldX)
        if (maxFoldX > minBackX + 1f) {
            drawPath(
                path = backPath,
                brush = Brush.horizontalGradient(
                    0.0f to foldCrease,
                    0.22f to backPaperColor,
                    0.55f to foldHighlight,
                    0.88f to backPaperColor,
                    1.0f to foldCrease,
                    startX = minBackX,
                    endX = maxFoldX
                )
            )
        }

        // 3. Texto espelhado sutilmente visível no verso da folha (transparência do papel)
        if (curlWidth > 36f && previewText.isNotBlank()) {
            val angleDeg = Math.toDegrees(atan2((bottomFoldX - topFoldX).toDouble(), h.toDouble())).toFloat()
            clipPath(backPath) {
                rotate(degrees = -angleDeg, pivot = Offset((topFoldX + bottomFoldX) / 2f, h / 2f)) {
                    drawContext.canvas.save()
                    val centerX = (minBackX + maxFoldX) / 2f
                    drawContext.canvas.translate(centerX, 0f)
                    drawContext.canvas.scale(-0.85f, 1f)
                    drawContext.canvas.translate(-centerX, 0f)
                    val layoutResult = textMeasurer.measure(
                        text = AnnotatedString(previewText.take(450)),
                        style = androidx.compose.ui.text.TextStyle(
                            color = textColor.copy(alpha = 0.24f),
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * lineHeightMult).sp,
                            fontFamily = fontFamily
                        ),
                        constraints = androidx.compose.ui.unit.Constraints(
                            maxWidth = (w * 0.85f).toInt().coerceAtLeast(100),
                            maxHeight = h.toInt().coerceAtLeast(100)
                        )
                    )
                    drawContext.canvas.restore()
                }
            }
        }

        // 4. Borda externa do rolo (sombra suave à esquerda do cilindro)
        val outerShadowWidth = curlWidth * 0.35f
        val outerShadowPath = Path().apply {
            moveTo(backTopX - outerShadowWidth, 0f)
            lineTo(backTopX, 0f)
            lineTo(backBottomX, h)
            lineTo(backBottomX - outerShadowWidth, h)
            close()
        }
        drawPath(
            path = outerShadowPath,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.20f)
                ),
                startX = minBackX - outerShadowWidth,
                endX = minBackX
            )
        )
    }
}
