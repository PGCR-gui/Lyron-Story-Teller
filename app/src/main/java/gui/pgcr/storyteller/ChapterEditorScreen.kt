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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChapterEditorScreen(
    chapterNumber: Int,
    chapter: Chapter,
    chapters: List<Chapter>,
    storyTitle: String,
    lastUpdated: Long,
    isChoiceStory: Boolean,
    onDone: () -> Unit,
    onChapterChange: (Chapter) -> Unit,
    onAddChoice: () -> Unit,
    onChangeChoice: (Int, StoryChoice) -> Unit,
    onDeleteChoice: (Int) -> Unit
) {
    var confirmEnding by remember(chapter.id) { mutableStateOf(false) }
    var imageError by remember(chapter.id) { mutableStateOf("") }
    var importError by remember(chapter.id) { mutableStateOf("") }
    var showSettings by remember(chapter.id) { mutableStateOf(false) }
    var showMenu by remember(chapter.id) { mutableStateOf(false) }
    var previewMode by remember(chapter.id) { mutableStateOf(false) }
    var pendingImagePosition by remember(chapter.id) { mutableStateOf(chapter.body.length) }
    var pendingReplaceId by remember(chapter.id) { mutableStateOf<String?>(null) }
    var editedAt by remember(chapter.id) { mutableStateOf(lastUpdated) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Toda alteração feita aqui atualiza a "última edição" mostrada sob o cabeçalho.
    val change: (Chapter) -> Unit = { updated ->
        editedAt = System.currentTimeMillis()
        onChapterChange(updated)
    }

    val chapterImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val replaceId = pendingReplaceId
        pendingReplaceId = null
        if (uri != null) {
            if (retainImageReadPermission(context, uri)) {
                if (replaceId != null) {
                    change(chapter.copy(images = chapter.images.map {
                        if (it.id == replaceId) it.copy(uri = uri.toString()) else it
                    }))
                } else {
                    val newImage = ChapterImage(uri.toString(), pendingImagePosition.coerceIn(0, chapter.body.length))
                    change(chapter.copy(images = chapter.images + newImage))
                }
                imageError = ""
            } else {
                imageError = "O Android não permitiu manter o acesso à foto. Escolha outra imagem ou tente novamente."
            }
        }
    }
    val txtImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val text = input.bufferedReader(Charsets.UTF_8).readText()
                change(chapter.copy(body = text, spans = emptyList(), paragraphs = emptyList()))
                importError = ""
            } ?: run {
                importError = "Não foi possível ler o arquivo .txt."
            }
        }.onFailure {
            importError = "Falha ao importar o arquivo: ${it.message ?: "erro desconhecido"}"
        }
    }

    val words = remember(chapter.body) { countWords(chapter.body) }
    val metaLine = remember(editedAt, words) {
        val locale = java.util.Locale("pt", "BR")
        val date = java.text.SimpleDateFormat("d MMM yyyy", locale).format(java.util.Date(editedAt))
        val count = java.text.NumberFormat.getInstance(locale).format(words)
        "$date  |  $count ${if (words == 1) "palavra" else "palavras"}"
    }
    val headerColor = lerp(Background, Accent, 0.16f)
    val barColor = lerp(CardColor, Accent, 0.14f)
    val errorText = imageError.ifBlank { importError }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        // Cabeçalho silencioso: voltar · título · opções · salvar
        Surface(color = headerColor) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDone, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text("←", color = Color.White, fontSize = 22.sp)
                }
                Column(
                    modifier = Modifier.weight(1f).clickable { showSettings = true }.padding(vertical = 2.dp)
                ) {
                    Text(
                        chapter.title.ifBlank { "Capítulo sem título" },
                        color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        (if (isChoiceStory) "Parte da história" else "Capítulo $chapterNumber") +
                            if (storyTitle.isNotBlank()) ": $storyTitle" else "",
                        color = SecondaryText, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showMenu = true }
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("⋯", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Opções", color = SecondaryText, fontSize = 10.sp)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Configurações do capítulo") },
                            onClick = { showMenu = false; showSettings = true }
                        )
                        DropdownMenuItem(
                            text = { Text(if (previewMode) "Voltar à edição" else "Pré-visualizar") },
                            onClick = { showMenu = false; previewMode = !previewMode; focusManager.clearFocus() }
                        )
                        DropdownMenuItem(
                            text = { Text("Importar texto (.txt)") },
                            onClick = { showMenu = false; txtImportPicker.launch(arrayOf("text/plain", "text/*")) }
                        )
                        DropdownMenuItem(
                            text = { Text("Exportar texto (.txt)") },
                            enabled = chapter.body.isNotBlank(),
                            onClick = {
                                showMenu = false
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, chapter.title.ifBlank { "Capítulo" })
                                    putExtra(Intent.EXTRA_TEXT, chapter.body)
                                }
                                context.startActivity(Intent.createChooser(share, "Exportar capítulo como texto"))
                            }
                        )
                    }
                }
                TextButton(onClick = onDone) {
                    Text("Salvar", color = Accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        // Linha discreta: data da última edição + contagem de palavras em tempo real
        Text(
            metaLine,
            color = SecondaryText, fontSize = 11.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)
        )
        if (errorText.isNotBlank()) {
            Text(
                errorText, color = Color(0xFFFF8B9A), fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        RichChapterEditor(
            chapter = chapter,
            onChapterChange = change,
            onInsertImage = { position ->
                pendingReplaceId = null
                pendingImagePosition = position.coerceIn(0, chapter.body.length)
                chapterImagePicker.launch(arrayOf("image/*"))
            },
            onReplaceImage = { imageId ->
                pendingReplaceId = imageId
                chapterImagePicker.launch(arrayOf("image/*"))
            },
            previewMode = previewMode,
            onTogglePreview = { previewMode = !previewMode; focusManager.clearFocus() },
            barColor = barColor,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = CardColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Configurações do capítulo", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = chapter.title,
                    onValueChange = { change(chapter.copy(title = it)) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Título do capítulo") }, singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Final alternativo", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Este capítulo encerra um dos caminhos da história.", color = SecondaryText, fontSize = 11.sp)
                    }
                    Switch(
                        checked = chapter.isEnding,
                        onCheckedChange = { checked ->
                            if (checked && chapter.choices.isNotEmpty()) confirmEnding = true
                            else change(chapter.copy(isEnding = checked))
                        }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Rascunho", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Capítulos em rascunho não entram na leitura pública.", color = SecondaryText, fontSize = 12.sp)
                    }
                    Switch(
                        checked = chapter.isDraft,
                        onCheckedChange = { change(chapter.copy(isDraft = it)) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Accent)
                    )
                }
                if (chapter.isEnding) {
                    EditorField(
                        label = "Texto exibido na caixa verde",
                        value = chapter.endingMessage,
                        onValueChange = { change(chapter.copy(endingMessage = it)) },
                        placeholder = "Ex.: Você chegou a um desfecho desta história.",
                        minLines = 3
                    )
                    Text("Este final não possui escolhas de saída. Desative o final para voltar a adicionar escolhas.", color = Color(0xFF9DE0B8), fontSize = 12.sp)
                } else {
                    HorizontalDivider(color = SecondaryText.copy(alpha = 0.2f))
                    Text("Escolhas deste capítulo", color = Color.White, fontWeight = FontWeight.SemiBold)
                    chapter.choices.forEachIndexed { choiceIndex, choice ->
                        ChoiceEditor(
                            choice = choice,
                            chapters = chapters,
                            onChange = { onChangeChoice(choiceIndex, it) },
                            onDelete = { onDeleteChoice(choiceIndex) }
                        )
                    }
                    TextButton(onClick = onAddChoice) { Text("+ Adicionar escolha", color = Accent) }
                }
                Text(
                    "$words palavras · ${chapter.body.length} caracteres",
                    color = SecondaryText, fontSize = 11.sp, modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
    if (confirmEnding) {
        AlertDialog(
            onDismissRequest = { confirmEnding = false },
            title = { Text("Transformar em final alternativo?") },
            text = {
                val count = chapter.choices.size
                Text(if (count == 1) "A escolha deste capítulo será removida, pois um final não pode levar a outros caminhos." else "As $count escolhas deste capítulo serão removidas, pois um final não pode levar a outros caminhos.")
            },
            dismissButton = { TextButton(onClick = { confirmEnding = false }) { Text("Cancelar") } },
            confirmButton = {
                TextButton(onClick = {
                    change(chapter.copy(isEnding = true, choices = emptyList()))
                    confirmEnding = false
                }) { Text("Marcar como final") }
            },
            containerColor = CardColor, titleContentColor = Color.White, textContentColor = SecondaryText
        )
    }
}

@Composable
internal fun StoryPathMapDialog(
    story: Story,
    onDismiss: () -> Unit,
    onChapterClick: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mapa de caminhos") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "Mapa do editor: toque em um capítulo para editar ou numa conexão para abrir o destino.",
                        color = SecondaryText, fontSize = 12.sp
                    )
                }
                itemsIndexed(story.chapters) { index, chapter ->
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onChapterClick(index) },
                            colors = CardDefaults.cardColors(containerColor = if (index == 0) Color(0xFF302746) else CardColor),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        when {
                                            chapter.isEnding -> "FINAL ALTERNATIVO"
                                            index == 0 -> "INÍCIO"
                                            else -> "CAPÍTULO ${index + 1}"
                                        },
                                        color = if (chapter.isEnding) Color(0xFF9DE0B8) else Accent,
                                        fontWeight = FontWeight.Bold, fontSize = 10.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (index == 0 && chapter.isEnding) Text("INÍCIO", color = Accent, fontSize = 10.sp)
                                    Text("ABRIR ›", color = SecondaryText, fontSize = 10.sp)
                                }
                                Text(chapter.title.ifBlank { "Capítulo ${index + 1}" }, color = Color.White, fontWeight = FontWeight.SemiBold)
                                if (chapter.isEnding) {
                                    Text("Este nó encerra um caminho da história.", color = Color(0xFF9DE0B8), fontSize = 11.sp)
                                } else if (chapter.choices.isEmpty()) {
                                    Text("Sem escolhas de saída", color = SecondaryText, fontSize = 11.sp)
                                }
                            }
                        }
                        chapter.choices.forEach { edge ->
                            val targetIndex = story.chapters.indexOfFirst { it.id == edge.nextChapterId }
                            val target = story.chapters.getOrNull(targetIndex)
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(start = 18.dp)
                                    .clickable(enabled = targetIndex >= 0) { if (targetIndex >= 0) onChapterClick(targetIndex) },
                                color = Background, shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Accent.copy(alpha = 0.18f))
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("↳", color = Accent, fontSize = 18.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(edge.text.ifBlank { "Escolha sem texto" }, color = Color.White, fontSize = 12.sp)
                                        Text(
                                            when {
                                                edge.endsStory -> "◇ FINAL DIRETO"
                                                target != null -> "↓ ${target.title} · tocar para navegar"
                                                else -> "⚠ DESTINO NÃO DEFINIDO"
                                            },
                                            color = if (edge.endsStory) Color(0xFF9DE0B8) else SecondaryText,
                                            fontSize = 10.sp
                                        )
                                    }
                                    if (target != null) Text("›", color = Accent, fontSize = 20.sp)
                                }
                            }
                        }
                        if (index < story.chapters.lastIndex) {
                            Text("⋮", color = Accent.copy(alpha = 0.55f), modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
        containerColor = CardColor, titleContentColor = Color.White, textContentColor = Color.White
    )
}

@Composable
internal fun ChoiceEditor(
    choice: StoryChoice,
    chapters: List<Chapter>,
    onChange: (StoryChoice) -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember(choice.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(Background, RoundedCornerShape(10.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = choice.text,
            onValueChange = { onChange(choice.copy(text = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Texto da escolha") },
            placeholder = { Text("Ex.: Abrir a porta") },
            singleLine = true
        )
        Box {
            OutlinedButton(onClick = { menuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (choice.endsStory) "Destino: Final da história" else "Destino: ${chapters.firstOrNull { it.id == choice.nextChapterId }?.title ?: "Selecionar capítulo"}")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                chapters.forEach { target ->
                    DropdownMenuItem(
                        text = { Text(target.title) },
                        onClick = { onChange(choice.copy(nextChapterId = target.id, endsStory = false)); menuExpanded = false }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Encerrar a história", color = Color(0xFF9DE0B8)) },
                    onClick = { onChange(choice.copy(nextChapterId = null, endsStory = true)); menuExpanded = false }
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDelete) { Text("Remover escolha", color = Color(0xFFFF8B9A)) }
        }
    }
}

// ---------------------------------------------------------------------------
// Editor imersivo de capítulo
// ---------------------------------------------------------------------------
