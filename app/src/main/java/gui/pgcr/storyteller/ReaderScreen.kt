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
import androidx.compose.foundation.gestures.detectTapGestures
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
internal fun ChapterBodyContent(chapter: Chapter, fontSize: Float) {
    val annotated = chapter.toAnnotatedString()
    val images = chapter.images.sortedBy { it.position }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        var cursor = 0
        images.forEach { image ->
            val position = image.position.coerceIn(cursor, chapter.body.length)
            if (position > cursor) {
                Text(
                    annotated.subSequence(cursor, position),
                    color = PrimaryText, fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.7f).sp
                )
            }
            StoredImage(
                image.uri,
                Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 360.dp).clip(RoundedCornerShape(14.dp)),
                ContentScale.Fit
            )
            cursor = position
        }
        if (cursor < chapter.body.length) {
            Text(
                annotated.subSequence(cursor, chapter.body.length),
                color = PrimaryText, fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.7f).sp
            )
        }
    }
}

@Composable
internal fun ReaderScreen(
    story: Story,
    chapterIndex: Int,
    initialPageIndex: Int,
    openMenuTick: Int,
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    lineHeightMult: Float,
    onLineHeightChange: (Float) -> Unit,
    pageMarginDp: Float,
    onPageMarginChange: (Float) -> Unit,
    readerThemeId: String,
    onReaderThemeChange: (String) -> Unit,
    readerMode: String,
    onReaderModeChange: (String) -> Unit,
    readerFontFamily: String,
    onReaderFontFamilyChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChoice: (StoryChoice) -> Unit,
    onFinishChapter: () -> Unit,
    canGoBack: Boolean,
    showEnding: Boolean,
    onDismissEnding: () -> Unit
) {
    val context = LocalContext.current
    val chapter = story.chapters[chapterIndex]
    val scrollState = rememberScrollState()
    val theme = readerThemeColors(readerThemeId)
    val fontFamily = remember(readerFontFamily) { resolveFontFamily(readerFontFamily, loadCustom = true, context = context) }
    var showReaderMenu by remember { mutableStateOf(false) }

    // O contador openMenuTick só sobe (e é guardado fora do leitor). Se o menu abrisse sempre que o valor fosse > 0,
    // ele reabriria sozinho a cada vez que o leitor fosse aberto de novo. Por isso só reage a MUDANÇAS do contador.
    var handledMenuTick by remember { mutableStateOf(openMenuTick) }
    LaunchedEffect(openMenuTick) {
        if (openMenuTick != handledMenuTick) {
            handledMenuTick = openMenuTick
            showReaderMenu = true
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var pageContainerSize by remember { mutableStateOf(IntSize.Zero) }

    val marginPx = with(density) { pageMarginDp.dp.toPx() }
    // Reserva: padding vertical da página (16*2) + ~1 linha de segurança
    val verticalPadPx = with(density) { (32.dp + (fontSize * lineHeightMult).sp.toDp() + 8.dp).toPx() }

    val pages = remember(chapter.body, fontSize, readerMode, readerFontFamily, pageContainerSize, lineHeightMult, pageMarginDp) {
        if (readerMode != "paginated") {
            listOf(chapter.body)
        } else if (pageContainerSize.width <= 0 || pageContainerSize.height <= 0) {
            paginateTextFallback(chapter.body, charsPerPage = (420 * (18f / fontSize)).toInt().coerceIn(180, 750))
        } else {
            val availableWidth = (pageContainerSize.width - marginPx * 2f).toInt().coerceAtLeast(120)
            val availableHeight = (pageContainerSize.height - verticalPadPx).toInt().coerceAtLeast(120)
            paginateTextByLayout(
                body = chapter.body,
                textMeasurer = textMeasurer,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * lineHeightMult).sp,
                    fontFamily = fontFamily
                ),
                maxWidthPx = availableWidth,
                maxHeightPx = availableHeight
            )
        }
    }

    var pageIndex by remember(story.id, readerMode) {
        mutableStateOf(initialPageIndex.coerceAtLeast(0))
    }
    var appliedInitial by remember(story.id) { mutableStateOf(false) }
    LaunchedEffect(pages.size, initialPageIndex, story.id) {
        if (!appliedInitial && pages.isNotEmpty()) {
            pageIndex = initialPageIndex.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
            appliedInitial = true
        } else if (pageIndex > pages.lastIndex) {
            pageIndex = pages.lastIndex.coerceAtLeast(0)
        }
    }

    // --- Avanço controlado: só passa de capítulo depois de chegar ao fim dele (ou ao fazer uma escolha) ---
    LaunchedEffect(chapterIndex) { scrollState.scrollTo(0) }
    val scrollAtEnd by remember {
        derivedStateOf { scrollState.maxValue != Int.MAX_VALUE && scrollState.value >= scrollState.maxValue - 48 }
    }
    val atChapterEnd = if (readerMode == "paginated") pageIndex >= pages.lastIndex else scrollAtEnd
    val latestAtChapterEnd by rememberUpdatedState(atChapterEnd)
    var finishedChapters by remember(story.id) { mutableStateOf(setOf<String>()) }
    LaunchedEffect(chapter.id, atChapterEnd) {
        if (atChapterEnd && chapter.id !in finishedChapters) {
            // Espera o layout do capítulo novo assentar para não marcar como lido por engano.
            kotlinx.coroutines.delay(200)
            if (latestAtChapterEnd) finishedChapters = finishedChapters + chapter.id
        }
    }
    // Capítulo já terminado antes pode ser atravessado de novo sem reler tudo.
    val canProceed = atChapterEnd || chapter.id in finishedChapters

    // Salva progresso sempre que capítulo/página mudam
    LaunchedEffect(story.id, chapterIndex, pageIndex) {
        saveReadingProgress(context, story.id, chapterIndex, pageIndex)
    }

    // Animação de troca de capítulo
    val chapterAnim = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    var prevChapterIndex by remember { mutableStateOf(chapterIndex) }
    LaunchedEffect(chapterIndex) {
        if (prevChapterIndex != chapterIndex) {
            pageIndex = 0
            val forward = chapterIndex > prevChapterIndex
            chapterAnim.snapTo(if (forward) 0.18f else -0.18f)
            chapterAnim.animateTo(0f, animationSpec = tween(400, easing = FastOutSlowInEasing))
            prevChapterIndex = chapterIndex
        }
    }

    /**
     * Curl unificado:
     * - progress 0 = página atual parada
     * - progress 1 = página atual totalmente virada, revela under
     * - forward: under = próxima; ao terminar pageIndex++
     * - reverse: under = anterior; ao terminar pageIndex--
     * NÃO altera pageIndex no meio da animação.
     */
    val curlAnim = remember { Animatable(0f) }
    var curlDirection by remember { mutableStateOf(1) } // 1 frente, -1 trás
    var curlTouchYRatio by remember { mutableStateOf(0.55f) }

    fun animatePageTurn(forward: Boolean, startYRatio: Float = 0.55f) {
        if (curlAnim.isRunning) return
        if (forward) {
            if (pageIndex >= pages.lastIndex) return
            coroutineScope.launch {
                curlDirection = 1
                curlTouchYRatio = startYRatio
                curlAnim.snapTo(0.02f)
                curlAnim.animateTo(1f, animationSpec = tween(460, easing = FastOutSlowInEasing))
                pageIndex++
                curlAnim.snapTo(0f)
            }
        } else {
            if (pageIndex <= 0) return
            coroutineScope.launch {
                curlDirection = -1
                curlTouchYRatio = startYRatio
                curlAnim.snapTo(0.02f)
                curlAnim.animateTo(1f, animationSpec = tween(460, easing = FastOutSlowInEasing))
                pageIndex--
                curlAnim.snapTo(0f)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .graphicsLayer {
                val t = chapterAnim.value
                translationX = t * size.width * 0.28f
                alpha = 1f - kotlin.math.abs(t) * 1.2f
            }
    ) {
        if (readerMode == "paginated") {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .onSizeChanged { pageContainerSize = it }
            ) {
                val progress = curlAnim.value
                val reverse = curlDirection == -1
                // Avançar: a página atual se enrola para a esquerda (progress 0 -> 1) e revela a próxima.
                // Voltar: é o movimento inverso, como num livro de verdade: a página anterior
                // volta da borda esquerda, desenrolando sobre a página atual (progresso invertido).
                val curlP = if (reverse) 1f - progress else progress
                val currentText = pages.getOrElse(pageIndex) { "" }
                val underText = if (reverse) currentText else pages.getOrElse(pageIndex + 1) { "" }
                val overText = if (reverse) pages.getOrElse(pageIndex - 1) { "" } else currentText

                if (progress > 0.001f) {
                    BookSheetCard(
                        text = underText,
                        fontSize = fontSize,
                        lineHeightMult = lineHeightMult,
                        pageMarginDp = pageMarginDp,
                        fontFamily = fontFamily,
                        theme = theme,
                        shadowIntensity = (1f - curlP) * 0.28f
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (progress <= 0.001f) {
                        BookSheetCard(
                            text = currentText,
                            fontSize = fontSize,
                            lineHeightMult = lineHeightMult,
                            pageMarginDp = pageMarginDp,
                            fontFamily = fontFamily,
                            theme = theme,
                            shadowIntensity = 0f
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    clip = true
                                    shape = PageCurlClipShape(progress = curlP, touchYRatio = curlTouchYRatio)
                                }
                        ) {
                            BookSheetCard(
                                text = overText,
                                fontSize = fontSize,
                                lineHeightMult = lineHeightMult,
                                pageMarginDp = pageMarginDp,
                                fontFamily = fontFamily,
                                theme = theme,
                                shadowIntensity = 0f
                            )
                        }
                        PageCurlOverlay(
                            progress = curlP,
                            touchYRatio = curlTouchYRatio,
                            pageColor = theme.surface,
                            textColor = theme.text,
                            previewText = overText,
                            fontSize = fontSize,
                            lineHeightMult = lineHeightMult,
                            fontFamily = fontFamily,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Toque nas laterais (25% de cada lado) vira a página; arrastar continua funcionando.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(pageIndex, pages.size, chapterIndex) {
                            detectTapGestures { offset ->
                                val w = size.width.coerceAtLeast(1).toFloat()
                                val yRatio = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0.15f, 0.85f)
                                when {
                                    offset.x <= w * 0.25f -> animatePageTurn(false, yRatio)
                                    offset.x >= w * 0.75f -> animatePageTurn(true, yRatio)
                                }
                            }
                        }
                        .pointerInput(pageIndex, pages.size, chapterIndex) {
                            var dragStartX = 0f
                            var totalDragX = 0f
                            var activeDir = 0
                            detectDragGestures(
                                onDragStart = { offset ->
                                    dragStartX = offset.x
                                    totalDragX = 0f
                                    activeDir = 0
                                    curlTouchYRatio = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0.15f, 0.85f)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (curlAnim.isRunning && activeDir == 0) return@detectDragGestures
                                    totalDragX += dragAmount.x
                                    val width = size.width.coerceAtLeast(1).toFloat()
                                    if (activeDir == 0 && kotlin.math.abs(totalDragX) > 14f) {
                                        if (totalDragX < 0f && pageIndex < pages.lastIndex) {
                                            activeDir = 1
                                            curlDirection = 1
                                        } else if (totalDragX > 0f && pageIndex > 0) {
                                            activeDir = -1
                                            curlDirection = -1
                                        }
                                    }
                                    if (activeDir == 1) {
                                        val p = (-totalDragX / (width * 0.80f)).coerceIn(0f, 1f)
                                        curlTouchYRatio = (change.position.y / size.height.coerceAtLeast(1)).coerceIn(0.15f, 0.85f)
                                        coroutineScope.launch { curlAnim.snapTo(p) }
                                    } else if (activeDir == -1) {
                                        // Arraste para a direita aumenta o progresso (vira a página atual para revelar a anterior)
                                        val p = (totalDragX / (width * 0.80f)).coerceIn(0f, 1f)
                                        curlTouchYRatio = (change.position.y / size.height.coerceAtLeast(1)).coerceIn(0.15f, 0.85f)
                                        coroutineScope.launch { curlAnim.snapTo(p) }
                                    }
                                },
                                onDragEnd = {
                                    val progressNow = curlAnim.value
                                    coroutineScope.launch {
                                        when (activeDir) {
                                            1 -> {
                                                if (progressNow > 0.28f) {
                                                    curlAnim.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
                                                    pageIndex++
                                                    curlAnim.snapTo(0f)
                                                } else {
                                                    curlAnim.animateTo(0f, tween(200))
                                                }
                                            }
                                            -1 -> {
                                                if (progressNow > 0.28f) {
                                                    curlAnim.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
                                                    pageIndex--
                                                    curlAnim.snapTo(0f)
                                                } else {
                                                    curlAnim.animateTo(0f, tween(200))
                                                }
                                            }
                                            else -> {
                                                // Toque simples nas faixas laterais (~22% cada lado)
                                                val w = size.width.toFloat()
                                                val leftZone = w * 0.22f
                                                val rightZone = w * 0.78f
                                                when {
                                                    dragStartX <= leftZone -> animatePageTurn(false, curlTouchYRatio)
                                                    dragStartX >= rightZone -> animatePageTurn(true, curlTouchYRatio)
                                                }
                                            }
                                        }
                                    }
                                },
                                onDragCancel = {
                                    coroutineScope.launch { curlAnim.animateTo(0f, tween(160)) }
                                }
                            )
                        }
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = pageMarginDp.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = chapter.toAnnotatedString(),
                    color = theme.text,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * lineHeightMult).sp,
                    fontFamily = fontFamily
                )
                chapter.images.sortedBy { it.position }.forEach { image ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            StoredImage(image.uri, Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(8.dp)), ContentScale.Crop, maxDimension = 900)
                            if (image.caption.isNotBlank()) {
                                Text(image.caption, color = theme.secondary, fontSize = 12.sp, fontFamily = fontFamily)
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onPrevious,
                enabled = canGoBack || (!story.isChoiceStory && chapterIndex > 0),
                border = BorderStroke(1.dp, theme.secondary.copy(alpha = 0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.text)
            ) { Text("Anterior") }

            if (readerMode == "paginated") {
                Text(
                    "${pageIndex + 1} / ${pages.size.coerceAtLeast(1)}",
                    color = theme.secondary,
                    fontSize = 13.sp,
                    fontFamily = fontFamily
                )
            } else {
                Text(
                    "Cap. ${chapterIndex + 1}/${story.chapters.size}",
                    color = theme.secondary,
                    fontSize = 13.sp,
                    fontFamily = fontFamily
                )
            }

            val hasChoices = chapter.choices.isNotEmpty() && !chapter.isEnding
            // Botão "Fim": final alternativo ou último capítulo (sem escolhas).
            val isFinalButton = !hasChoices && (chapter.isEnding || chapterIndex >= story.chapters.lastIndex)
            // Os botões são só de capítulo; as páginas viram por arraste ou toque nas laterais.
            Button(
                onClick = {
                    if (isFinalButton) onFinishChapter() else onNext()
                },
                enabled = !hasChoices && canProceed,
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent, contentColor = theme.background)
            ) {
                Text(
                    when {
                        hasChoices -> "Escolha"
                        isFinalButton -> "Fim"
                        else -> "Próximo"
                    }
                )
            }
        }

        if (chapter.choices.isNotEmpty() && !chapter.isEnding && canProceed) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chapter.choices.forEach { choice ->
                    Button(
                        onClick = { onChoice(choice) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
                    ) { Text(choice.text, fontFamily = fontFamily) }
                }
            }
        }
    }

    if (showReaderMenu) {
        AlertDialog(
            onDismissRequest = { showReaderMenu = false },
            title = { Text("Leitura", color = PrimaryText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Tema", color = SecondaryText, fontSize = 12.sp)
                    READER_THEME_OPTIONS.forEach { (id, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { onReaderThemeChange(id) }.padding(vertical = 2.dp)
                        ) {
                            RadioButton(selected = readerThemeId == id, onClick = { onReaderThemeChange(id) }, colors = RadioButtonDefaults.colors(selectedColor = Accent))
                            Text(label, color = PrimaryText, fontSize = 14.sp)
                        }
                    }
                    Text("Fonte", color = SecondaryText, fontSize = 12.sp)
                    READER_FONT_OPTIONS.forEach { (id, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { onReaderFontFamilyChange(id) }.padding(vertical = 2.dp)
                        ) {
                            RadioButton(selected = readerFontFamily == id, onClick = { onReaderFontFamilyChange(id) }, colors = RadioButtonDefaults.colors(selectedColor = Accent))
                            ReaderFontSample(fontId = id, label = label, modifier = Modifier.weight(1f))
                        }
                    }
                    CustomFontSection(selectedId = readerFontFamily, onSelect = onReaderFontFamilyChange)
                    Text("Tamanho: ${fontSize.toInt()} sp", color = SecondaryText, fontSize = 12.sp)
                    Slider(
                        value = fontSize,
                        onValueChange = onFontSizeChange,
                        valueRange = 8f..32f,
                        steps = 23,
                        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
                    )
                    Text("Espaçamento de linhas: ${"%.2f".format(lineHeightMult)}×", color = SecondaryText, fontSize = 12.sp)
                    Slider(
                        value = lineHeightMult,
                        onValueChange = onLineHeightChange,
                        valueRange = 1.2f..2.2f,
                        steps = 19,
                        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
                    )
                    Text("Margem: ${pageMarginDp.toInt()} dp", color = SecondaryText, fontSize = 12.sp)
                    Slider(
                        value = pageMarginDp,
                        onValueChange = onPageMarginChange,
                        valueRange = 8f..40f,
                        steps = 15,
                        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
                    )
                    Text("Modo de leitura", color = SecondaryText, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = readerMode == "continuous",
                            onClick = { onReaderModeChange("continuous") },
                            label = { Text("Contínuo") }
                        )
                        FilterChip(
                            selected = readerMode == "paginated",
                            onClick = { onReaderModeChange("paginated") },
                            label = { Text("Paginado") }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReaderMenu = false }) { Text("Fechar", color = Accent) }
            },
            containerColor = CardColor,
            titleContentColor = PrimaryText,
            textContentColor = SecondaryText
        )
    }

    if (showEnding) {
        val endTitle = when {
            chapter.isEnding -> "Fim"
            story.status == STATUS_COMPLETE -> "Fim da história"
            story.status == STATUS_CANCELLED -> "História cancelada"
            else -> "História em andamento"
        }
        val endMessage = when {
            // Final alternativo: mostra a mensagem escrita pelo autor para ESTE final.
            chapter.isEnding ->
                chapter.endingMessage.ifBlank { "Você chegou a um desfecho desta história." }
            story.status == STATUS_COMPLETE ->
                "Você chegou ao fim dessa história!"
            story.status == STATUS_CANCELLED ->
                "O autor decidiu que não irá mais continuar essa história."
            else -> {
                val date = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "BR"))
                    .format(java.util.Date(story.lastUpdated))
                "Esta história ainda está incompleta…\nAtualizado pela última vez em: $date"
            }
        }
        AlertDialog(
            onDismissRequest = onDismissEnding,
            title = { Text(endTitle) },
            text = { Text(endMessage) },
            confirmButton = { TextButton(onClick = onDismissEnding) { Text("OK") } },
            containerColor = CardColor, titleContentColor = PrimaryText, textContentColor = SecondaryText
        )
    }
}

/**
 * Paginação exata usando TextMeasurer: calcula quantas linhas inteiras cabem na altura útil
 * da página para a fonte/tamanho escolhidos, garantindo que nenhuma linha seja picotada.
 */
internal fun paginateTextByLayout(
    body: String,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    style: androidx.compose.ui.text.TextStyle,
    maxWidthPx: Int,
    maxHeightPx: Int
): List<String> {
    if (body.isBlank()) return listOf("")
    val layout = textMeasurer.measure(
        text = AnnotatedString(body),
        style = style,
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxWidthPx)
    )
    val lineCount = layout.lineCount
    if (lineCount == 0) return listOf(body)

    val pages = mutableListOf<String>()
    var pageStartLine = 0
    while (pageStartLine < lineCount) {
        val pageTopY = layout.getLineTop(pageStartLine)
        var lastFittingLine = pageStartLine
        for (line in pageStartLine until lineCount) {
            val lineBottom = layout.getLineBottom(line)
            // folga generosa para não cortar a última linha (descenders + padding visual)
            if (lineBottom - pageTopY <= maxHeightPx - 8f) {
                lastFittingLine = line
            } else {
                break
            }
        }
        val startOffset = layout.getLineStart(pageStartLine)
        val endOffset = layout.getLineEnd(lastFittingLine, visibleEnd = true)
        val slice = body.substring(startOffset, endOffset.coerceIn(startOffset, body.length)).trimEnd()
        if (slice.isNotEmpty()) {
            pages.add(slice)
        }
        pageStartLine = lastFittingLine + 1
    }
    return pages.ifEmpty { listOf("") }
}

internal fun paginateTextFallback(body: String, charsPerPage: Int): List<String> {
    if (body.isBlank()) return listOf("")
    val pages = mutableListOf<String>()
    var i = 0
    while (i < body.length) {
        var end = (i + charsPerPage).coerceAtMost(body.length)
        if (end < body.length) {
            val space = body.lastIndexOf(' ', end)
            if (space > i + charsPerPage / 3) end = space
        }
        pages.add(body.substring(i, end).trim())
        i = end
        while (i < body.length && body[i].isWhitespace()) i++
    }
    return pages.ifEmpty { listOf("") }
}
