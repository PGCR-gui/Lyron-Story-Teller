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
internal fun RichChapterEditor(
    chapter: Chapter,
    onChapterChange: (Chapter) -> Unit,
    onInsertImage: (Int) -> Unit,
    onReplaceImage: (String) -> Unit,
    previewMode: Boolean,
    onTogglePreview: () -> Unit,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    var fieldValue by remember(chapter.id) {
        val annotated = chapter.toAnnotatedString()
        mutableStateOf(TextFieldValue(annotated, TextRange(annotated.length)))
    }
    val undoStack = remember(chapter.id) { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember(chapter.id) { mutableStateListOf<TextFieldValue>() }
    val maxHistory = 100
    // Cada item do histórico guarda uma cópia do capítulo inteiro; por isso o histórico também tem
    // um teto de caracteres (capítulos longos não podem acumular dezenas de cópias na memória).
    val maxHistoryChars = 1_500_000
    // Digitação seguida vira um único passo de "desfazer" (marcas de tempo do último passo).
    val undoClock = remember(chapter.id) { longArrayOf(0L, 0L) } // [0] última edição, [1] início da sequência
    fun trimHistory(stack: MutableList<TextFieldValue>) {
        var total = 0L
        stack.forEach { total += it.text.length }
        while (stack.size > 1 && (stack.size > maxHistory || total > maxHistoryChars)) {
            total -= stack.removeAt(0).text.length
        }
        if (stack.size == 1 && total > maxHistoryChars) stack.clear()
    }
    var activeSegment by remember(chapter.id) { mutableStateOf(-1) }
    var focusTarget by remember(chapter.id) { mutableStateOf(-1) }
    var focusTick by remember(chapter.id) { mutableStateOf(0) }
    var showFind by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }

    // Sincroniza quando o corpo muda de fora (ex.: importação de .txt)
    LaunchedEffect(chapter.id, chapter.body) {
        if (fieldValue.text != chapter.body) {
            fieldValue = TextFieldValue(chapter.toAnnotatedString())
            undoStack.clear()
            redoStack.clear()
        }
    }

    // O texto é dividido pelas fotos: cada foto fica no meio do fluxo, entre dois trechos editáveis.
    val text = fieldValue.text
    val sortedImages = chapter.images.sortedBy { it.position }
    val imageCount = sortedImages.size
    val bounds = ArrayList<Int>(imageCount + 2).also { list ->
        list.add(0)
        var previous = 0
        sortedImages.forEach { image ->
            previous = image.position.coerceIn(previous, text.length)
            list.add(previous)
        }
        list.add(text.length)
    }

    fun segmentForSelection(start: Int, end: Int): Int? {
        if (activeSegment in 0..imageCount && start >= bounds[activeSegment] && end <= bounds[activeSegment + 1]) {
            return activeSegment
        }
        for (j in 0..imageCount) {
            if (start >= bounds[j] && end <= bounds[j + 1]) return j
        }
        return null
    }

    fun syncChapter(value: TextFieldValue, images: List<ChapterImage>? = null) {
        val updatedImages = images ?: chapter.images.map { image ->
            image.copy(position = remapImagePosition(chapter.body, value.text, image.position))
        }
        onChapterChange(chapter.copy(
            body = value.text,
            spans = value.annotatedString.toRichSpans(),
            paragraphs = value.annotatedString.toRichParagraphs(),
            images = updatedImages
        ))
    }

    /** Uma edição dentro do trecho [segment] empurra só as fotos que vêm depois dele. */
    fun shiftImages(segment: Int, delta: Int): List<ChapterImage> {
        val order = sortedImages.map { it.id }
        return chapter.images.map { image ->
            if (order.indexOf(image.id) >= segment) image.copy(position = (image.position + delta).coerceAtLeast(0))
            else image
        }
    }

    fun commit(updated: TextFieldValue, preserve: Boolean = true, editSegment: Int? = null) {
        // Mudança só de cursor/seleção: não mexe em estilos nem no histórico.
        if (preserve && updated.text == fieldValue.text) {
            fieldValue = fieldValue.copy(selection = updated.selection, composition = updated.composition)
            return
        }
        val annotated = if (preserve) preserveFormattingAfterEdit(fieldValue.annotatedString, updated)
        else updated.annotatedString
        val synchronizedValue = updated.copy(annotatedString = annotated)
        val contentChanged = synchronizedValue.annotatedString != fieldValue.annotatedString
        val delta = synchronizedValue.text.length - text.length
        if (contentChanged) {
            val now = System.currentTimeMillis()
            val typingBurst = preserve && undoStack.isNotEmpty() &&
                now - undoClock[0] < 1_200L && now - undoClock[1] < 8_000L
            if (!typingBurst) {
                undoStack.add(fieldValue)
                trimHistory(undoStack)
                undoClock[1] = now
            }
            undoClock[0] = now
            redoStack.clear()
        }
        val images = editSegment?.let { shiftImages(it, delta) }
        fieldValue = synchronizedValue
        if (contentChanged) syncChapter(synchronizedValue, images)
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(fieldValue)
        trimHistory(redoStack)
        val previous = undoStack.removeAt(undoStack.lastIndex)
        undoClock[0] = 0L
        fieldValue = previous
        syncChapter(previous)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(fieldValue)
        trimHistory(undoStack)
        val next = redoStack.removeAt(redoStack.lastIndex)
        undoClock[0] = 0L
        fieldValue = next
        syncChapter(next)
    }

    fun onSegmentEdit(segment: Int, edited: TextFieldValue) {
        val whole = fieldValue.text
        val suffixLength = (text.length - bounds[segment + 1]).coerceAtLeast(0)
        val a = bounds[segment].coerceIn(0, whole.length)
        val b = (whole.length - suffixLength).coerceIn(a, whole.length)
        val newText = whole.substring(0, a) + edited.text + whole.substring(b)
        commit(
            TextFieldValue(
                annotatedString = AnnotatedString(newText),
                selection = TextRange(a + edited.selection.start, a + edited.selection.end),
                composition = edited.composition?.let { TextRange(a + it.start, a + it.end) }
            ),
            editSegment = segment
        )
    }

    fun toggleInline(has: (SpanStyle) -> Boolean, add: SpanStyle, strip: (SpanStyle) -> SpanStyle) {
        val start = fieldValue.selection.min
        val end = fieldValue.selection.max
        if (end <= start) return
        val current = fieldValue.annotatedString
        val updated = if (current.isRangeCovered(start, end, has)) {
            current.mapSpanStyles(start, end) { style ->
                if (has(style)) strip(style).takeIf { it != SpanStyle() } else style
            }
        } else {
            AnnotatedString.Builder().apply {
                append(current)
                addStyle(add, start, end)
            }.toAnnotatedString()
        }
        commit(fieldValue.copy(annotatedString = updated), preserve = false)
    }

    fun clearFormatting() {
        val start = fieldValue.selection.min
        val end = fieldValue.selection.max
        if (end <= start) return
        commit(
            fieldValue.copy(annotatedString = fieldValue.annotatedString.mapSpanStyles(start, end) { null }),
            preserve = false
        )
    }

    fun setHeading(level: Int) {
        val value = fieldValue.text
        if (value.isEmpty()) return
        val (start, end) = lineBounds(
            value,
            fieldValue.selection.min.coerceIn(0, value.length),
            fieldValue.selection.max.coerceIn(0, value.length)
        )
        if (end <= start) return
        val cleared = fieldValue.annotatedString.mapSpanStyles(start, end) { style ->
            if (headingLevelOf(style.fontSize) != 0) null else style
        }
        val updated = if (level == 0) cleared else AnnotatedString.Builder().apply {
            append(cleared)
            addStyle(SpanStyle(fontSize = headingSizeFor(level), fontWeight = FontWeight.Bold), start, end)
        }.toAnnotatedString()
        commit(fieldValue.copy(annotatedString = updated), preserve = false)
    }

    fun applyAlignment(alignment: TextAlign) {
        val value = fieldValue.text
        if (value.isEmpty()) return
        val (start, lineEnd) = lineBounds(
            value,
            fieldValue.selection.min.coerceIn(0, value.length),
            fieldValue.selection.max.coerceIn(0, value.length)
        )
        val end = minOf(lineEnd + 1, value.length)
        if (end <= start) return
        commit(fieldValue.copy(annotatedString = fieldValue.annotatedString.withAlignment(start, end, alignment)), preserve = false)
    }

    fun insertAtSelection(replacement: String) {
        val start = fieldValue.selection.min
        val end = fieldValue.selection.max
        val current = fieldValue.annotatedString
        val rebuilt = AnnotatedString.Builder().apply {
            append(current.subSequence(0, start))
            append(replacement)
            append(current.subSequence(end, current.length))
        }.toAnnotatedString()
        commit(
            fieldValue.copy(annotatedString = rebuilt, selection = TextRange(start + replacement.length), composition = null),
            preserve = false,
            editSegment = segmentForSelection(start, end)
        )
    }

    fun insertSceneDivider() = insertAtSelection("\n\n* * *\n\n")

    fun insertQuoteBlock() {
        val start = fieldValue.selection.min
        val end = fieldValue.selection.max
        val selected = if (end > start) fieldValue.text.substring(start, end) else "Citação"
        insertAtSelection("\n\n« $selected »\n\n")
    }

    fun runFind() {
        val query = findQuery
        if (query.isBlank()) return
        val from = fieldValue.selection.max.coerceAtMost(fieldValue.text.length)
        val index = fieldValue.text.indexOf(query, from)
        val found = if (index >= 0) index else fieldValue.text.indexOf(query)
        if (found >= 0) {
            fieldValue = fieldValue.copy(selection = TextRange(found, found + query.length))
            val segment = segmentForSelection(found, found + query.length)
            if (segment != null) {
                activeSegment = segment
                focusTarget = segment
                focusTick += 1
            }
        }
    }

    // Estado dos botões da barra
    val annotated = fieldValue.annotatedString
    val selMin = fieldValue.selection.min.coerceIn(0, text.length)
    val selMax = fieldValue.selection.max.coerceIn(0, text.length)
    val isBold = annotated.isRangeCovered(selMin, selMax) { it.fontWeight == FontWeight.Bold }
    val isItalic = annotated.isRangeCovered(selMin, selMax) { it.fontStyle == FontStyle.Italic }
    val isUnderline = annotated.isRangeCovered(selMin, selMax) { it.textDecoration?.contains(TextDecoration.Underline) == true }
    val isStrike = annotated.isRangeCovered(selMin, selMax) { it.textDecoration?.contains(TextDecoration.LineThrough) == true }
    val currentHeading = run {
        val (lineStart, lineEnd) = lineBounds(text, selMin, selMin)
        annotated.spanStyles
            .firstOrNull { it.start < lineEnd && it.end > lineStart && headingLevelOf(it.item.fontSize) != 0 }
            ?.let { headingLevelOf(it.item.fontSize) } ?: 0
    }

    Column(modifier = modifier) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (previewMode) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(chapter.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    ChapterBodyContent(chapter, 17f)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    for (j in 0..imageCount) {
                        val a = bounds[j]
                        val b = bounds[j + 1]
                        val isActive = activeSegment == j
                        val selection = fieldValue.selection
                        val segmentValue = TextFieldValue(
                            annotatedString = fieldValue.annotatedString.subSequence(a, b),
                            selection = if (isActive) TextRange(
                                (selection.start - a).coerceIn(0, b - a),
                                (selection.end - a).coerceIn(0, b - a)
                            ) else TextRange.Zero,
                            composition = if (isActive) fieldValue.composition?.let { c ->
                                if (c.start >= a && c.end <= b) TextRange(c.start - a, c.end - a) else null
                            } else null
                        )
                        ChapterTextSegment(
                            value = segmentValue,
                            placeholder = if (j == 0 && imageCount == 0 && text.isEmpty()) "Escreva seu capítulo..." else null,
                            isFocusTarget = focusTarget == j,
                            focusTick = focusTick,
                            onFocusHandled = { focusTarget = -1 },
                            onFocus = { activeSegment = j },
                            onValueChange = { onSegmentEdit(j, it) }
                        )
                        if (j < imageCount) {
                            val image = sortedImages[j]
                            InlineImageCard(
                                image = image,
                                onCaptionChange = { caption ->
                                    onChapterChange(chapter.copy(images = chapter.images.map {
                                        if (it.id == image.id) it.copy(caption = caption) else it
                                    }))
                                },
                                onReplace = { onReplaceImage(image.id) },
                                onRemove = {
                                    onChapterChange(chapter.copy(images = chapter.images.filterNot { it.id == image.id }))
                                }
                            )
                        }
                    }
                    // Área livre abaixo do texto: tocar nela continua escrevendo no fim do capítulo
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                activeSegment = imageCount
                                focusTarget = imageCount
                                focusTick += 1
                            }
                    )
                }
            }
        }
        ChapterFormatBar(
            barColor = barColor,
            previewMode = previewMode,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            hasSelection = selMax > selMin,
            bold = isBold,
            italic = isItalic,
            underline = isUnderline,
            strike = isStrike,
            heading = currentHeading,
            onUndo = { undo() },
            onRedo = { redo() },
            onBold = { toggleInline({ it.fontWeight == FontWeight.Bold }, SpanStyle(fontWeight = FontWeight.Bold)) { it.copy(fontWeight = null) } },
            onItalic = { toggleInline({ it.fontStyle == FontStyle.Italic }, SpanStyle(fontStyle = FontStyle.Italic)) { it.copy(fontStyle = null) } },
            onUnderline = {
                toggleInline(
                    { it.textDecoration?.contains(TextDecoration.Underline) == true },
                    SpanStyle(textDecoration = TextDecoration.Underline)
                ) { style ->
                    style.copy(textDecoration = if (style.textDecoration?.contains(TextDecoration.LineThrough) == true) TextDecoration.LineThrough else null)
                }
            },
            onStrike = {
                toggleInline(
                    { it.textDecoration?.contains(TextDecoration.LineThrough) == true },
                    SpanStyle(textDecoration = TextDecoration.LineThrough)
                ) { style ->
                    style.copy(textDecoration = if (style.textDecoration?.contains(TextDecoration.Underline) == true) TextDecoration.Underline else null)
                }
            },
            onClearFormat = { clearFormatting() },
            onAlign = { applyAlignment(it) },
            onHeading = { setHeading(it) },
            onQuote = { insertQuoteBlock() },
            onDivider = { insertSceneDivider() },
            onImage = { onInsertImage(fieldValue.selection.max) },
            onSearch = { showFind = true },
            onTogglePreview = onTogglePreview
        )
    }

    if (showFind) {
        AlertDialog(
            onDismissRequest = { showFind = false },
            title = { Text("Buscar no capítulo") },
            text = {
                OutlinedTextField(
                    value = findQuery,
                    onValueChange = { findQuery = it },
                    label = { Text("Texto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    runFind()
                    showFind = false
                }) { Text("Buscar", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showFind = false }) { Text("Cancelar", color = SecondaryText) }
            },
            containerColor = CardColor,
            titleContentColor = Color.White,
            textContentColor = SecondaryText
        )
    }
}

@Composable
internal fun ChapterTextSegment(
    value: TextFieldValue,
    placeholder: String?,
    isFocusTarget: Boolean,
    focusTick: Int,
    onFocusHandled: () -> Unit,
    onFocus: () -> Unit,
    onValueChange: (TextFieldValue) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusTick) {
        if (focusTick > 0 && isFocusTarget) {
            runCatching { focusRequester.requestFocus() }
            onFocusHandled()
        }
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocus() },
        textStyle = androidx.compose.ui.text.TextStyle(
            color = Color(0xFFEDEAF3), fontSize = 17.sp, lineHeight = 27.sp
        ),
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        decorationBox = { inner ->
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = if (placeholder != null) 28.dp else 12.dp)) {
                if (placeholder != null && value.text.isEmpty()) {
                    Text(placeholder, color = SecondaryText.copy(alpha = 0.7f), fontSize = 17.sp)
                }
                inner()
            }
        }
    )
}

/** Foto integrada ao fluxo do texto: miniatura, legenda editável, trocar (lápis) e remover. */
@Composable
internal fun InlineImageCard(
    image: ChapterImage,
    onCaptionChange: (String) -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        color = Color.White.copy(alpha = 0.07f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).clickable { onReplace() }) {
                StoredImage(image.uri, Modifier.fillMaxSize(), ContentScale.Crop, maxDimension = 256)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .background(Color(0xCC1B1626), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("✎", color = Color.White, fontSize = 12.sp) }
            }
            BasicTextField(
                value = image.caption,
                onValueChange = onCaptionChange,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFFEDEAF3), fontSize = 14.sp),
                cursorBrush = SolidColor(Accent),
                decorationBox = { inner ->
                    Box {
                        if (image.caption.isEmpty()) {
                            Text("Legenda da imagem", color = SecondaryText, fontSize = 14.sp)
                        }
                        inner()
                    }
                }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Top)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onRemove() }
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) { Text("🗑", color = Color(0xFFEDE7FA), fontSize = 16.sp) }
        }
    }
}

@Composable
internal fun EditorToolButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 40.dp, minHeight = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Accent.copy(alpha = 0.28f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f },
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
internal fun AlignGlyph(align: TextAlign, color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val widths = listOf(1f, 0.6f, 1f, 0.6f)
        widths.forEachIndexed { i, fraction ->
            val y = size.height * (0.2f + 0.2f * i)
            val w = size.width * fraction
            val x = when (align) {
                TextAlign.Center -> (size.width - w) / 2f
                TextAlign.End -> size.width - w
                else -> 0f
            }
            drawLine(color, Offset(x, y), Offset(x + w, y), strokeWidth = 2.dp.toPx())
        }
    }
}

@Composable
internal fun EyeGlyph(color: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        drawOval(
            color = color,
            topLeft = Offset(0f, size.height * 0.2f),
            size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.6f),
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(color = color, radius = size.height * 0.14f, center = Offset(size.width / 2f, size.height / 2f))
    }
}

/** Cápsula flutuante ancorada acima do teclado. */
@Composable
internal fun ChapterFormatBar(
    barColor: Color,
    previewMode: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    hasSelection: Boolean,
    bold: Boolean,
    italic: Boolean,
    underline: Boolean,
    strike: Boolean,
    heading: Int,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit,
    onStrike: () -> Unit,
    onClearFormat: () -> Unit,
    onAlign: (TextAlign) -> Unit,
    onHeading: (Int) -> Unit,
    onQuote: () -> Unit,
    onDivider: () -> Unit,
    onImage: () -> Unit,
    onSearch: () -> Unit,
    onTogglePreview: () -> Unit
) {
    val ink = Color(0xFFEDE7FA)
    val editing = !previewMode
    var styleMenu by remember { mutableStateOf(false) }
    var alignMenu by remember { mutableStateOf(false) }
    var headingMenu by remember { mutableStateOf(false) }

    Surface(
        color = barColor,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorToolButton(onClick = onUndo, enabled = editing && canUndo) { Text("↶", color = ink, fontSize = 20.sp) }
                EditorToolButton(onClick = onRedo, enabled = editing && canRedo) { Text("↷", color = ink, fontSize = 20.sp) }
                EditorToolButton(onClick = onBold, enabled = editing && hasSelection, active = bold) {
                    Text("B", color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                EditorToolButton(onClick = onItalic, enabled = editing && hasSelection, active = italic) {
                    Text("I", color = ink, fontSize = 18.sp, fontStyle = FontStyle.Italic)
                }
                EditorToolButton(onClick = onUnderline, enabled = editing && hasSelection, active = underline) {
                    Text("U", color = ink, fontSize = 18.sp, textDecoration = TextDecoration.Underline)
                }
                EditorToolButton(onClick = onStrike, enabled = editing && hasSelection, active = strike) {
                    Text("S", color = ink, fontSize = 18.sp, textDecoration = TextDecoration.LineThrough)
                }
                Box {
                    EditorToolButton(onClick = { styleMenu = true }, enabled = editing) {
                        Text("Aa", color = ink, fontSize = 17.sp)
                    }
                    DropdownMenu(expanded = styleMenu, onDismissRequest = { styleMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Limpar formatação da seleção") },
                            enabled = hasSelection,
                            onClick = { styleMenu = false; onClearFormat() }
                        )
                    }
                }
                Box {
                    EditorToolButton(onClick = { alignMenu = true }, enabled = editing, modifier = Modifier.width(52.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AlignGlyph(TextAlign.Start, ink)
                            Text("▾", color = ink, fontSize = 10.sp)
                        }
                    }
                    DropdownMenu(expanded = alignMenu, onDismissRequest = { alignMenu = false }) {
                        DropdownMenuItem(text = { Text("Alinhar à esquerda") }, onClick = { alignMenu = false; onAlign(TextAlign.Start) })
                        DropdownMenuItem(text = { Text("Centralizar") }, onClick = { alignMenu = false; onAlign(TextAlign.Center) })
                        DropdownMenuItem(text = { Text("Alinhar à direita") }, onClick = { alignMenu = false; onAlign(TextAlign.End) })
                    }
                }
                Box {
                    EditorToolButton(
                        onClick = { headingMenu = true },
                        enabled = editing,
                        active = heading != 0,
                        modifier = Modifier.width(52.dp)
                    ) {
                        Text(
                            when (heading) { 1 -> "H1"; 2 -> "H2"; else -> "H▾" },
                            color = ink, fontSize = 17.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    DropdownMenu(expanded = headingMenu, onDismissRequest = { headingMenu = false }) {
                        DropdownMenuItem(text = { Text("Parágrafo") }, onClick = { headingMenu = false; onHeading(0) })
                        DropdownMenuItem(text = { Text("Título (H1)") }, onClick = { headingMenu = false; onHeading(1) })
                        DropdownMenuItem(text = { Text("Subtítulo (H2)") }, onClick = { headingMenu = false; onHeading(2) })
                    }
                }
                EditorToolButton(onClick = onQuote, enabled = editing) {
                    Text("“", color = ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                EditorToolButton(onClick = onDivider, enabled = editing) {
                    Text("---", color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                EditorToolButton(onClick = onImage, enabled = editing) { Text("▧", color = ink, fontSize = 20.sp) }
                EditorToolButton(onClick = onSearch, enabled = editing) { Text("⌕", color = ink, fontSize = 22.sp) }
            }
            Box(modifier = Modifier.width(1.dp).height(24.dp).background(ink.copy(alpha = 0.15f)))
            EditorToolButton(onClick = onTogglePreview, active = previewMode, modifier = Modifier.padding(start = 2.dp)) {
                EyeGlyph(ink)
            }
        }
    }
}
