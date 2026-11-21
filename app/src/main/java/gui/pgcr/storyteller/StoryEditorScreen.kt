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
internal fun StoryEditorScreen(
    initialStory: Story?,
    activeChapterIndex: Int?,
    defaultAuthor: String,
    saveTrigger: Int,
    onChapterSelect: (Int?) -> Unit,
    onCancel: () -> Unit,
    onSave: (Story) -> Unit
) {
    var title by remember(initialStory) { mutableStateOf(initialStory?.title.orEmpty()) }
    var author by remember(initialStory) {
        mutableStateOf(initialStory?.author ?: defaultAuthor.ifBlank { "Autor" })
    }
    var selectedGenres by remember(initialStory) {
        mutableStateOf(initialStory?.genres?.toList().orEmpty())
    }
    var description by remember(initialStory) { mutableStateOf(initialStory?.description.orEmpty()) }
    var rating by remember(initialStory) { mutableStateOf(initialStory?.rating ?: "Livre") }
    var contentWarnings by remember(initialStory) {
        mutableStateOf(
            initialStory?.contentWarnings?.takeIf { it.isNotBlank() }
                ?: defaultContentWarningFor(initialStory?.rating ?: "Livre")
        )
    }
    var status by remember(initialStory) {
        mutableStateOf(initialStory?.status ?: STATUS_IN_PROGRESS)
    }
    var coverImageUri by remember(initialStory) { mutableStateOf(initialStory?.coverImageUri) }
    var tags by remember(initialStory) {
        mutableStateOf(initialStory?.tags?.toList().orEmpty())
    }
    var classifications by remember(initialStory) {
        mutableStateOf(initialStory?.classifications?.toList().orEmpty())
    }
    var newTagText by remember { mutableStateOf("") }
    var showGenrePicker by remember { mutableStateOf(false) }
    var showRatingPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val context = LocalContext.current
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (retainImageReadPermission(context, uri)) coverImageUri = uri.toString()
            else error = "O Android não permitiu manter o acesso à foto. Escolha outra imagem ou tente novamente."
        }
    }
    val blankChapter = remember(initialStory) { Chapter("Capítulo 1", "", id = UUID.randomUUID().toString()) }
    val chapters = remember(initialStory) {
        mutableStateListOf<Chapter>().apply {
            addAll(initialStory?.chapters ?: listOf(blankChapter))
        }
    }
    // Oneshot só é válido com exatamente 1 capítulo
    LaunchedEffect(chapters.size) {
        if (chapters.size != 1 && CLASSIFICATION_ONESHOT in classifications) {
            classifications = classifications.filterNot { it == CLASSIFICATION_ONESHOT }
        }
    }
    var showPreview by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }
    val draftStoryId = remember(initialStory) { initialStory?.id ?: UUID.randomUUID().toString() }
    val draftStory = Story(
        title = title.trim(),
        author = author.trim().ifBlank { defaultAuthor.ifBlank { "Autor" } },
        genres = selectedGenres,
        description = description.trim(),
        rating = rating.trim().ifBlank { "Livre" },
        chapters = chapters.toList(),
        coverImageUri = coverImageUri,
        tags = tags,
        classifications = classifications,
        contentWarnings = contentWarnings.trim(),
        status = status,
        editable = true,
        id = draftStoryId
    )
    val emptyStory = Story(
        title = "", author = defaultAuthor.ifBlank { "Autor" }, genres = emptyList(), description = "",
        rating = "Livre", chapters = listOf(blankChapter), tags = emptyList(),
        classifications = emptyList(), status = STATUS_IN_PROGRESS, editable = true, id = draftStoryId
    )
    val hasUnsavedChanges = activeChapterIndex == null &&
        (if (initialStory == null) draftStory != emptyStory else draftStory != initialStory)
    // Estes totais e o aviso "Não salvo" só aparecem no formulário da história; com um capítulo
    // aberto, recalculá-los a cada tecla só gastava memória e CPU à toa.
    val formVisible = activeChapterIndex == null
    val totalWords = if (formVisible) chapters.sumOf { countWords(it.body) } else 0
    val totalCharacters = if (formVisible) chapters.sumOf { it.body.length } else 0
    val activeChapter = activeChapterIndex?.let { chapters.getOrNull(it) }

    fun trySaveStory() {
        error = when {
            title.isBlank() -> "Informe o título da história."
            chapters.any { it.title.isBlank() } -> "Todos os capítulos precisam ter um título."
            chapters.any { it.isEnding && it.choices.isNotEmpty() } -> "Capítulos marcados como final não podem ter escolhas de saída."
            chapters.any { ch -> ch.choices.any { choice ->
                choice.text.isBlank() || (choice.endsStory == (choice.nextChapterId != null)) ||
                    (choice.nextChapterId != null && chapters.none { it.id == choice.nextChapterId })
            } } -> "Complete cada escolha: informe um capítulo de destino ou marque 'Encerra a história'."
            CLASSIFICATION_ORIGINAL in classifications && CLASSIFICATION_FANFIC in classifications ->
                "História Original e Fanfic não podem ser usadas na mesma história."
            CLASSIFICATION_ONESHOT in classifications && chapters.size != 1 ->
                "Oneshot só pode ser usada em histórias de um único capítulo."
            else -> ""
        }
        if (error.isBlank()) onSave(draftStory)
    }

    // Inicializa com o valor atual para só reagir a cliques novos de "Salvar",
    // não reaplicar ao reabrir o editor.
    var lastHandledSaveTrigger by remember { mutableStateOf(saveTrigger) }
    LaunchedEffect(saveTrigger) {
        if (saveTrigger == lastHandledSaveTrigger) return@LaunchedEffect
        lastHandledSaveTrigger = saveTrigger
        if (activeChapterIndex != null) onChapterSelect(null)
        else trySaveStory()
    }

    if (activeChapter != null && activeChapterIndex != null) {
        val chapterId = activeChapter.id
        ChapterEditorScreen(
            chapterNumber = activeChapterIndex + 1,
            chapter = activeChapter,
            chapters = chapters.toList(),
            storyTitle = title.trim(),
            lastUpdated = initialStory?.lastUpdated ?: System.currentTimeMillis(),
            isChoiceStory = chapters.any { it.choices.isNotEmpty() || it.isEnding },
            onDone = { onChapterSelect(null) },
            onChapterChange = { updated ->
                val index = chapters.indexOfFirst { it.id == chapterId }
                if (index >= 0) chapters[index] = updated
            },
            onAddChoice = {
                val index = chapters.indexOfFirst { it.id == chapterId }
                if (index >= 0 && !chapters[index].isEnding) {
                    chapters[index] = chapters[index].copy(choices = chapters[index].choices + StoryChoice(""))
                }
            },
            onChangeChoice = { choiceIndex, updated ->
                val index = chapters.indexOfFirst { it.id == chapterId }
                if (index >= 0) {
                    val current = chapters[index]
                    val updatedChoices = current.choices.toMutableList()
                    if (choiceIndex in updatedChoices.indices) {
                        updatedChoices[choiceIndex] = updated
                        chapters[index] = current.copy(choices = updatedChoices)
                    }
                }
            },
            onDeleteChoice = { choiceIndex ->
                val index = chapters.indexOfFirst { it.id == chapterId }
                if (index >= 0) {
                    val current = chapters[index]
                    chapters[index] = current.copy(choices = current.choices.filterIndexed { i, _ -> i != choiceIndex })
                }
            }
        )
    } else {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (initialStory == null) "Criar história" else "Editar história",
                        color = PrimaryText, fontSize = 26.sp, fontWeight = FontWeight.Bold
                    )
                    Text("Edite os dados da história e abra cada capítulo em sua própria tela.", color = SecondaryText, fontSize = 13.sp)
                }
                Surface(
                    color = if (hasUnsavedChanges) WarnColor.copy(alpha = 0.18f) else SuccessColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        if (hasUnsavedChanges) "Não salvo" else "Salvo",
                        color = if (hasUnsavedChanges) WarnColor else SuccessColor,
                        fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            EditorField("Título da história", title, { title = it }, "Ex.: A Cidade de Vidro")
            EditorField("Autor", author, { author = it }, "Nome do autor")
            Text("Gêneros (até $MAX_STORY_GENRES)", color = PrimaryText, fontWeight = FontWeight.SemiBold)
            Text(
                if (selectedGenres.isEmpty()) "Nenhum gênero selecionado"
                else selectedGenres.joinToString(" · "),
                color = if (selectedGenres.isEmpty()) SecondaryText else Accent,
                fontSize = 13.sp
            )
            OutlinedButton(onClick = { showGenrePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (selectedGenres.isEmpty()) "Escolher gêneros" else "Alterar gêneros",
                    color = Accent
                )
            }
            if (selectedGenres.isNotEmpty()) {
                selectedGenres.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { g ->
                            Surface(
                                color = SurfaceAlt,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.clickable {
                                    selectedGenres = selectedGenres.filterNot { it == g }
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(g, color = PrimaryText, fontSize = 12.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("×", color = DangerColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            Text("Classificação etária", color = PrimaryText, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = { showRatingPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(rating, color = Accent)
            }
            Text("Avisos de conteúdo", color = PrimaryText, fontWeight = FontWeight.SemiBold)
            Text(
                "Texto exibido junto à classificação. Pode editar livremente (padrão baseado na classificação escolhida).",
                color = SecondaryText,
                fontSize = 12.sp
            )
            OutlinedTextField(
                value = contentWarnings,
                onValueChange = { contentWarnings = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                label = { Text("Avisos de conteúdo") }
            )
            TextButton(
                onClick = { contentWarnings = defaultContentWarningFor(rating) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Restaurar texto padrão", color = Accent, fontSize = 12.sp)
            }
            Text("Status da história", color = PrimaryText, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                STORY_STATUSES.forEach { option ->
                    val selected = status == option
                    val color = when (option) {
                        STATUS_COMPLETE -> SuccessColor
                        STATUS_CANCELLED -> DangerColor
                        else -> InfoColor
                    }
                    Surface(
                        color = if (selected) color.copy(alpha = 0.25f) else CardColor,
                        shape = RoundedCornerShape(50),
                        border = if (selected) BorderStroke(1.dp, color) else null,
                        modifier = Modifier.clickable { status = option }
                    ) {
                        Text(
                            option,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = if (selected) color else SecondaryText,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            Text("Foto de capa", color = PrimaryText, fontWeight = FontWeight.SemiBold)
            if (coverImageUri != null) {
                StoredImage(coverImageUri!!, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)), ContentScale.Crop)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { coverPicker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) {
                    Text(if (coverImageUri == null) "Escolher capa" else "Trocar capa")
                }
                if (coverImageUri != null) {
                    TextButton(onClick = { coverImageUri = null }) { Text("Remover", color = DangerColor) }
                }
            }
            EditorField("Sinopse", description, { description = it }, "Apresente sua história", minLines = 4)
            Text("${description.length} caracteres", color = SecondaryText, fontSize = 11.sp, modifier = Modifier.align(Alignment.End))

            Text("Classificações", color = PrimaryText, fontWeight = FontWeight.SemiBold)
            Text(
                "Oneshot só em histórias de 1 capítulo. História Original e Fanfic não podem ser usadas juntas.",
                color = SecondaryText,
                fontSize = 11.sp
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                STORY_CLASSIFICATIONS.forEach { label ->
                    val selected = label in classifications
                    val oneshotBlocked = label == CLASSIFICATION_ONESHOT && chapters.size != 1
                    val conflictBlocked = when (label) {
                        CLASSIFICATION_ORIGINAL -> CLASSIFICATION_FANFIC in classifications
                        CLASSIFICATION_FANFIC -> CLASSIFICATION_ORIGINAL in classifications
                        else -> false
                    }
                    val enabled = !oneshotBlocked && (!conflictBlocked || selected)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) {
                                classifications = if (selected) {
                                    classifications.filterNot { it == label }
                                } else {
                                    val withoutConflict = when (label) {
                                        CLASSIFICATION_ORIGINAL -> classifications.filterNot { it == CLASSIFICATION_FANFIC }
                                        CLASSIFICATION_FANFIC -> classifications.filterNot { it == CLASSIFICATION_ORIGINAL }
                                        else -> classifications
                                    }
                                    withoutConflict + label
                                }
                            }
                            .padding(vertical = 2.dp)
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = null,
                            enabled = enabled,
                            colors = CheckboxDefaults.colors(
                                checkedColor = Accent,
                                uncheckedColor = SecondaryText,
                                disabledCheckedColor = Accent.copy(alpha = 0.4f),
                                disabledUncheckedColor = SecondaryText.copy(alpha = 0.4f)
                            )
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                label,
                                color = if (enabled) PrimaryText else SecondaryText.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Medium
                            )
                            if (oneshotBlocked) {
                                Text("Disponível apenas com um único capítulo", color = SecondaryText, fontSize = 10.sp)
                            } else if (conflictBlocked && !selected) {
                                Text("Incompatível com a classificação já selecionada", color = SecondaryText, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            Text("Tags", color = PrimaryText, fontWeight = FontWeight.SemiBold)
            Text("Tags livres para organizar e encontrar sua história.", color = SecondaryText, fontSize = 11.sp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = newTagText,
                    onValueChange = { newTagText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Nova tag") },
                    placeholder = { Text("Ex.: fofo, gore…") }
                )
                Button(
                    onClick = {
                        val tag = newTagText.trim()
                        if (tag.isNotBlank() && tag !in tags) {
                            tags = tags + tag
                            newTagText = ""
                        }
                    },
                    enabled = newTagText.trim().isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)
                ) {
                    Text("Adicionar")
                }
            }
            if (tags.isNotEmpty()) {
                tags.chunked(3).forEach { rowTags ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowTags.forEach { tag ->
                            Surface(
                                color = SurfaceAlt,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.clickable {
                                    tags = tags.filterNot { it == tag }
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(tag, color = PrimaryText, fontSize = 12.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("×", color = DangerColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Capítulos", color = PrimaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${chapters.size} capítulos · $totalWords palavras · $totalCharacters caracteres", color = SecondaryText, fontSize = 11.sp)
                }
                TextButton(onClick = { chapters.add(Chapter("Capítulo ${chapters.size + 1}", "", id = UUID.randomUUID().toString())) }) {
                    Text("+ Adicionar", color = Accent)
                }
            }
            OutlinedButton(onClick = { showMap = true }, modifier = Modifier.fillMaxWidth()) {
                Text("⌘  Visualizar mapa de caminhos")
            }

            chapters.forEachIndexed { index, chapter ->
                Card(colors = CardDefaults.cardColors(containerColor = CardColor), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Capítulo ${index + 1}", color = Accent, fontWeight = FontWeight.Bold)
                                if (chapter.isEnding) Text("FINAL ALTERNATIVO", color = SuccessColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = {
                                if (index > 0) {
                                    val item = chapters.removeAt(index)
                                    chapters.add(index - 1, item)
                                }
                            }, enabled = index > 0) { Text("↑", color = Accent) }
                            TextButton(onClick = {
                                if (index < chapters.lastIndex) {
                                    val item = chapters.removeAt(index)
                                    chapters.add(index + 1, item)
                                }
                            }, enabled = index < chapters.lastIndex) { Text("↓", color = Accent) }
                            TextButton(onClick = { if (chapters.size > 1) chapters.removeAt(index) }, enabled = chapters.size > 1) {
                                Text("Excluir", color = DangerColor)
                            }
                        }
                        Text(chapter.title.ifBlank { "Capítulo sem título" }, color = PrimaryText, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${countWords(chapter.body)} palavras · ${chapter.choices.size} escolhas",
                            color = SecondaryText, fontSize = 11.sp
                        )
                        Button(
                            onClick = { onChapterSelect(index) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceAlt, contentColor = Accent)
                        ) { Text("Editar capítulo ${index + 1}") }
                    }
                }
            }

            if (error.isNotBlank()) Text(error, color = DangerColor, fontSize = 13.sp)
            OutlinedButton(
                onClick = { showPreview = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() || chapters.any { it.body.isNotBlank() }
            ) { Text("Pré-visualizar história") }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                Button(
                    onClick = { trySaveStory() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)
                ) { Text("Salvar história") }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showMap) {
        StoryPathMapDialog(
            story = draftStory,
            onDismiss = { showMap = false },
            onChapterClick = { chapterIndex ->
                showMap = false
                onChapterSelect(chapterIndex)
            }
        )
    }
    if (showPreview) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            title = { Text(draftStory.title.ifBlank { "Pré-visualização" }) },
            text = {
                Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (draftStory.coverImageUri != null) {
                        StoredImage(
                            draftStory.coverImageUri,
                            Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)),
                            ContentScale.Crop,
                            maxDimension = 1000
                        )
                    }
                    Text(
                        "${draftStory.author} · ${draftStory.genres.joinToString(" · ").ifBlank { draftStory.genre }} · ${draftStory.rating}",
                        color = SecondaryText,
                        fontSize = 12.sp
                    )
                    if (draftStory.description.isNotBlank()) Text(draftStory.description, color = PrimaryText, fontSize = 14.sp)
                    HorizontalDivider(color = SecondaryText.copy(alpha = 0.25f))
                    draftStory.chapters.forEachIndexed { index, chapter ->
                        Text(chapter.title.ifBlank { "Capítulo ${index + 1}" }, color = Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        if (chapter.body.isBlank() && chapter.images.isEmpty()) {
                            Text("Este capítulo ainda não possui texto.", color = PrimaryText, fontSize = 14.sp)
                        } else {
                            ChapterBodyContent(chapter = chapter, fontSize = 14f)
                        }
                        if (chapter.isEnding) Text("FINAL ALTERNATIVO · ${chapter.endingMessage}", color = SuccessColor, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPreview = false }) { Text("Voltar ao editor") } },
            containerColor = CardColor, titleContentColor = PrimaryText, textContentColor = PrimaryText
        )
    }
    if (showGenrePicker) {
        AlertDialog(
            onDismissRequest = { showGenrePicker = false },
            title = { Text("Gêneros (até $MAX_STORY_GENRES)") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Selecione até $MAX_STORY_GENRES gêneros para esta história.",
                        color = SecondaryText,
                        fontSize = 13.sp
                    )
                    STORY_GENRES.forEach { genreOption ->
                        val selected = genreOption in selectedGenres
                        val atLimit = selectedGenres.size >= MAX_STORY_GENRES && !selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !atLimit) {
                                    selectedGenres = if (selected) {
                                        selectedGenres.filterNot { it == genreOption }
                                    } else {
                                        selectedGenres + genreOption
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = null,
                                enabled = !atLimit,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Accent,
                                    uncheckedColor = SecondaryText,
                                    disabledUncheckedColor = SecondaryText.copy(alpha = 0.35f)
                                )
                            )
                            Text(
                                genreOption,
                                color = if (atLimit) SecondaryText.copy(alpha = 0.5f) else PrimaryText,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGenrePicker = false }) { Text("Pronto", color = Accent) }
            },
            containerColor = CardColor,
            titleContentColor = PrimaryText,
            textContentColor = PrimaryText
        )
    }
    if (showRatingPicker) {
        AlertDialog(
            onDismissRequest = { showRatingPicker = false },
            title = { Text("Classificação etária") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AGE_RATINGS.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val wasDefault = contentWarnings == defaultContentWarningFor(rating) || contentWarnings.isBlank()
                                    rating = option
                                    if (wasDefault) contentWarnings = defaultContentWarningFor(option)
                                    showRatingPicker = false
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = rating == option,
                                onClick = {
                                    val wasDefault = contentWarnings == defaultContentWarningFor(rating) || contentWarnings.isBlank()
                                    rating = option
                                    if (wasDefault) contentWarnings = defaultContentWarningFor(option)
                                    showRatingPicker = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = Accent)
                            )
                            Text(option, color = PrimaryText)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRatingPicker = false }) { Text("Fechar", color = Accent) }
            },
            containerColor = CardColor,
            titleContentColor = PrimaryText,
            textContentColor = PrimaryText
        )
    }
}
