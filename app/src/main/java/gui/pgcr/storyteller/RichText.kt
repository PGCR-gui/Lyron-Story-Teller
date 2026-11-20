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

internal fun remapImagePosition(previousText: String, updatedText: String, position: Int): Int {
    var prefix = 0
    val prefixLimit = minOf(previousText.length, updatedText.length)
    while (prefix < prefixLimit && previousText[prefix] == updatedText[prefix]) prefix++
    var suffix = 0
    while (
        suffix < previousText.length - prefix &&
        suffix < updatedText.length - prefix &&
        previousText[previousText.length - 1 - suffix] == updatedText[updatedText.length - 1 - suffix]
    ) suffix++
    val oldTailStart = previousText.length - suffix
    val newTailStart = updatedText.length - suffix
    val safe = position.coerceIn(0, previousText.length)
    return when {
        safe < prefix -> safe
        safe > oldTailStart -> (safe + updatedText.length - previousText.length).coerceIn(0, updatedText.length)
        safe == oldTailStart -> newTailStart
        else -> prefix
    }
}


internal val HEADING_1_SIZE = 1.5.em
internal val HEADING_2_SIZE = 1.25.em

internal fun headingSizeFor(level: Int): TextUnit = when (level) {
    1 -> HEADING_1_SIZE
    2 -> HEADING_2_SIZE
    else -> TextUnit.Unspecified
}

internal fun headingLevelOf(size: TextUnit): Int = when (size) {
    HEADING_1_SIZE -> 1
    HEADING_2_SIZE -> 2
    else -> 0
}

internal fun Chapter.toAnnotatedString(): AnnotatedString {
    return AnnotatedString.Builder(body).apply {
        spans.forEach { span ->
            val start = span.start.coerceIn(0, body.length)
            val end = span.end.coerceIn(start, body.length)
            if (end > start) addStyle(
                SpanStyle(
                    fontSize = headingSizeFor(span.heading),
                    fontWeight = if (span.bold || span.heading > 0) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    textDecoration = when {
                        span.underline && span.strikethrough -> TextDecoration.Underline + TextDecoration.LineThrough
                        span.underline -> TextDecoration.Underline
                        span.strikethrough -> TextDecoration.LineThrough
                        else -> null
                    }
                ), start, end
            )
        }
        paragraphs.forEach { paragraph ->
            val start = paragraph.start.coerceIn(0, body.length)
            val end = paragraph.end.coerceIn(start, body.length)
            val alignment = when (paragraph.alignment) {
                "center" -> TextAlign.Center
                "right" -> TextAlign.End
                else -> TextAlign.Start
            }
            if (end > start) addStyle(ParagraphStyle(textAlign = alignment), start, end)
        }
    }.toAnnotatedString()
}

/**
 * Mantém os estilos já aplicados quando o usuário continua digitando.
 * O BasicTextField pode devolver o texto atualizado sem todos os intervalos
 * de estilo anteriores; por isso remapeamos os intervalos para os novos offsets.
 */
internal fun preserveFormattingAfterEdit(
    previous: AnnotatedString,
    updated: TextFieldValue
): AnnotatedString {
    val oldText = previous.text
    val newText = updated.text
    var prefix = 0
    val prefixLimit = minOf(oldText.length, newText.length)
    while (prefix < prefixLimit && oldText[prefix] == newText[prefix]) prefix++

    var suffix = 0
    while (
        suffix < oldText.length - prefix &&
        suffix < newText.length - prefix &&
        oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
    ) suffix++

    val oldTailStart = oldText.length - suffix
    val newTailStart = newText.length - suffix
    val delta = newText.length - oldText.length

    fun mapOffset(offset: Int, isEnd: Boolean): Int {
        val safe = offset.coerceIn(0, oldText.length)
        if (safe < prefix) return safe
        if (safe > oldTailStart) return (safe + delta).coerceIn(0, newText.length)
        if (oldTailStart == prefix && safe == prefix) {
            return if (isEnd) newTailStart else prefix
        }
        return if (isEnd) newTailStart else prefix
    }

    return AnnotatedString.Builder(newText).apply {
        previous.spanStyles.forEach { range ->
            val start = mapOffset(range.start, isEnd = false)
            val end = mapOffset(range.end, isEnd = true)
            if (end > start) addStyle(range.item, start, end)
        }
        previous.paragraphStyles.forEach { range ->
            val start = mapOffset(range.start, isEnd = false)
            val end = mapOffset(range.end, isEnd = true)
            if (end > start) addStyle(range.item, start, end)
        }
        // O valor atualizado pode conter uma nova formatação aplicada pela barra.
        // Adicionamos por último para que uma ação nova prevaleça sobre a anterior.
        updated.annotatedString.spanStyles.forEach { range ->
            val start = range.start.coerceIn(0, newText.length)
            val end = range.end.coerceIn(start, newText.length)
            if (end > start) addStyle(range.item, start, end)
        }
        updated.annotatedString.paragraphStyles.forEach { range ->
            val start = range.start.coerceIn(0, newText.length)
            val end = range.end.coerceIn(start, newText.length)
            if (end > start) addStyle(range.item, start, end)
        }
    }.toAnnotatedString()
}

internal fun AnnotatedString.toRichSpans(): List<RichSpan> = spanStyles.mapNotNull { range ->
    val style = range.item
    val bold = style.fontWeight == FontWeight.Bold
    val italic = style.fontStyle == FontStyle.Italic
    val underline = style.textDecoration?.contains(TextDecoration.Underline) == true
    val strikethrough = style.textDecoration?.contains(TextDecoration.LineThrough) == true
    val heading = headingLevelOf(style.fontSize)
    if (!bold && !italic && !underline && !strikethrough && heading == 0) null
    else RichSpan(range.start, range.end, bold, italic, underline, strikethrough, heading)
}

internal fun AnnotatedString.toRichParagraphs(): List<RichParagraph> = paragraphStyles.mapNotNull { range ->
    val alignment = when (range.item.textAlign) {
        TextAlign.Center -> "center"
        TextAlign.End, TextAlign.Right -> "right"
        else -> "left"
    }
    if (range.end <= range.start) null else RichParagraph(range.start, range.end, alignment)
}

/** Limites [início, fim) da linha (parágrafo) que contém a seleção; o fim não inclui o '\n'. */
internal fun lineBounds(text: String, lo: Int, hi: Int): Pair<Int, Int> {
    val start = if (lo <= 0) 0 else text.lastIndexOf('\n', lo - 1) + 1
    val newline = text.indexOf('\n', hi)
    val end = if (newline < 0) text.length else newline
    return start to end
}

/** Verdadeiro se todo o intervalo estiver coberto por estilos que satisfazem [has]. */
internal fun AnnotatedString.isRangeCovered(start: Int, end: Int, has: (SpanStyle) -> Boolean): Boolean {
    if (end <= start) return false
    var position = start
    spanStyles
        .filter { has(it.item) && it.end > start && it.start < end }
        .sortedBy { it.start }
        .forEach { range ->
            if (range.start > position) return false
            if (range.end > position) position = range.end
            if (position >= end) return true
        }
    return position >= end
}

/** Aplica [transform] só à parte dos estilos que cai dentro de [start, end); null remove o estilo. */
internal fun AnnotatedString.mapSpanStyles(
    start: Int,
    end: Int,
    transform: (SpanStyle) -> SpanStyle?
): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    spanStyles.forEach { range ->
        if (range.end <= start || range.start >= end) {
            builder.addStyle(range.item, range.start, range.end)
        } else {
            if (range.start < start) builder.addStyle(range.item, range.start, start)
            val changed = transform(range.item)
            if (changed != null) builder.addStyle(changed, maxOf(range.start, start), minOf(range.end, end))
            if (range.end > end) builder.addStyle(range.item, end, range.end)
        }
    }
    paragraphStyles.forEach { builder.addStyle(it.item, it.start, it.end) }
    return builder.toAnnotatedString()
}

/** Troca o alinhamento do intervalo sem acumular estilos de parágrafo sobrepostos. */
internal fun AnnotatedString.withAlignment(start: Int, end: Int, align: TextAlign): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    spanStyles.forEach { builder.addStyle(it.item, it.start, it.end) }
    paragraphStyles.forEach { range ->
        if (range.end <= start || range.start >= end) {
            builder.addStyle(range.item, range.start, range.end)
        } else {
            if (range.start < start) builder.addStyle(range.item, range.start, start)
            if (range.end > end) builder.addStyle(range.item, end, range.end)
        }
    }
    builder.addStyle(ParagraphStyle(textAlign = align), start, end)
    return builder.toAnnotatedString()
}

/** Conta palavras percorrendo o texto uma vez, sem criar listas (o split com Regex alocava todas as palavras a cada tecla). */
internal fun countWords(text: String): Int {
    var count = 0
    var inWord = false
    for (ch in text) {
        if (ch.isWhitespace()) {
            inWord = false
        } else if (!inWord) {
            inWord = true
            count++
        }
    }
    return count
}
