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
import androidx.compose.ui.draw.alpha
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StoryDetailsScreen(
    story: Story,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    hasProgress: Boolean,
    onContinueClick: () -> Unit,
    onReadClick: () -> Unit,
    onChapterClick: (Int) -> Unit,
    /** Quantos capítulos (a partir do 1º) o leitor já pode abrir; os demais aparecem bloqueados. */
    unlockedChapterCount: Int = Int.MAX_VALUE,
    canEdit: Boolean,
    onEditClick: () -> Unit,
    onExportClick: () -> Unit,
    canPublishOnline: Boolean = false,
    onPublishOnline: () -> Unit = {},
    canDelete: Boolean = false,
    onDeleteClick: () -> Unit = {},
    transferBusy: Boolean,
    onStatsClick: () -> Unit,
    stats: StoryStats,
    onAuthorClick: (() -> Unit)? = null
) {
    val isChoiceStory = story.isChoiceStory
    var synopsisExpanded by remember(story.id) { mutableStateOf(false) }
    val statusColor = when (story.status) {
        STATUS_COMPLETE -> SuccessColor
        STATUS_CANCELLED -> DangerColor
        else -> InfoColor
    }
    val currentEndingKeys = buildSet {
        story.chapters.filter { it.isEnding }.forEach { add("chapter:${it.id}") }
        story.chapters.forEach { chapter ->
            chapter.choices.filter { it.endsStory }.forEach { add("choice:${it.id}") }
        }
        val lastChapter = story.chapters.lastOrNull()
        if (!isChoiceStory && lastChapter != null && !lastChapter.isEnding) add("chapter:${lastChapter.id}")
    }
    val currentPathKeys = buildSet {
        story.chapters.forEach { chapter ->
            chapter.choices.filter { choice ->
                choice.endsStory || story.chapters.any { it.id == choice.nextChapterId }
            }.forEach { add("choice:${it.id}") }
        }
        story.chapters.forEachIndexed { index, chapter ->
            val continues = !isChoiceStory || (chapter.choices.isEmpty() && !chapter.isEnding)
            if (continues && index < story.chapters.lastIndex) {
                add("linear:${chapter.id}->${story.chapters[index + 1].id}")
            }
        }
    }
    val pathCount = stats.pathTraversals.filterKeys { it in currentPathKeys }.values.sum()
    val endingCount = stats.endingVisits.filterKeys { it in currentEndingKeys }.values.sum()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceAlt, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (story.coverImageUri != null) {
                    StoredImage(
                        story.coverImageUri,
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 320.dp)
                            .wrapContentHeight(),
                        contentScale = ContentScale.Fit
                    )
                    // Selo de classificação indicativa no canto inferior direito da capa
                    Image(
                        painter = painterResource(id = classifDrawableRes(story.rating)),
                        contentDescription = "Classificação ${story.rating}",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .height(48.dp)
                            .wrapContentWidth()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(170.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("𒀱", color = Accent, fontSize = 64.sp)
                        Image(
                            painter = painterResource(id = classifDrawableRes(story.rating)),
                            contentDescription = "Classificação ${story.rating}",
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                                .height(48.dp)
                                .wrapContentWidth()
                        )
                    }
                }
            }
        }
        item {
            Text(
                story.genres.joinToString(" · ").ifBlank { story.genre }.uppercase(),
                color = Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(story.title, color = PrimaryText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            if (onAuthorClick != null && story.author.isNotBlank()) {
                Text(
                    "por ${story.author}",
                    color = Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable(onClick = onAuthorClick)
                )
            } else {
                Text("por ${story.author}", color = SecondaryText, fontSize = 14.sp)
            }
            if (!story.editable) {
                Text("Somente leitura", color = WarnColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                InfoChip(story.rating)
                if (!isChoiceStory) InfoChip("${story.chapters.size} capítulos")
                else InfoChip("História interativa")
                Surface(color = statusColor.copy(alpha = 0.2f), shape = RoundedCornerShape(50)) {
                    Text(
                        story.status,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (story.classifications.isNotEmpty() || story.tags.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (story.classifications.isNotEmpty()) {
                        Text("Tipo", color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            story.classifications.forEach { InfoChip(it) }
                        }
                    }
                    if (story.tags.isNotEmpty()) {
                        if (story.classifications.isNotEmpty()) Spacer(Modifier.height(12.dp))
                        Text("Tags", color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            story.tags.forEach { tag ->
                                InfoChip(
                                    text = "#$tag",
                                    modifier = Modifier.widthIn(max = 260.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Text("Sinopse", color = PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (story.description.isBlank()) {
                Text("Sem sinopse.", color = SecondaryText, fontSize = 15.sp)
            } else {
                val longSynopsis = story.description.length > 280 || story.description.count { it == '\n' } >= 3
                Text(
                    story.description,
                    color = SecondaryText,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                    maxLines = if (synopsisExpanded || !longSynopsis) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis
                )
                if (longSynopsis) {
                    TextButton(
                        onClick = { synopsisExpanded = !synopsisExpanded },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            if (synopsisExpanded) "Mostrar menos" else "Ler sinopse completa",
                            color = Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        item {
            // Classificação indicativa + aviso de conteúdo numa peça só (veja RatingCard.kt).
            ContentRatingCard(
                rating = story.rating,
                warnings = story.contentWarnings.ifBlank { defaultContentWarningFor(story.rating) }
            )
        }
        item {
            if (hasProgress) {
                Button(
                    onClick = onContinueClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)
                ) {
                    Text("Continuar de onde parou")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onReadClick, modifier = Modifier.weight(1f)) {
                        Text("Recomeçar do início", color = Accent)
                    }
                    OutlinedButton(onClick = onFavoriteClick, modifier = Modifier.weight(1f)) {
                        Text(if (isFavorite) "★ Favorita" else "☆ Favoritar", color = if (isFavorite) WarnColor else Accent)
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onReadClick, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)) {
                        Text(if (stats.completedReadings > 0 || stats.endingVisits.isNotEmpty()) "Ler novamente" else "Começar leitura")
                    }
                    OutlinedButton(onClick = onFavoriteClick, modifier = Modifier.weight(1f)) {
                        Text(if (isFavorite) "★ Favorita" else "☆ Favoritar", color = if (isFavorite) WarnColor else Accent)
                    }
                }
            }
            if (isChoiceStory) {
                OutlinedButton(onClick = onStatsClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Estatísticas · $pathCount caminhos · $endingCount finais", color = Accent)
                }
            }
            OutlinedButton(onClick = onExportClick, enabled = !transferBusy, modifier = Modifier.fillMaxWidth()) {
                Text(if (transferBusy) "Preparando arquivo…" else "Exportar história .lyr (com fotos)", color = Accent)
            }
            if (canPublishOnline) {
                Button(
                    onClick = onPublishOnline,
                    enabled = !transferBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (transferBusy) "Publicando…" else "Publicar online (somente leitura)")
                }
            }
            if (canEdit) {
                OutlinedButton(onClick = onEditClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Editar história", color = Accent)
                }
            }
            if (canDelete) {
                OutlinedButton(onClick = onDeleteClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Excluir da biblioteca", color = DangerColor)
                }
            }
        }
        if (!isChoiceStory) {
            item { Text("Capítulos", color = PrimaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            items(story.chapters.size) { index ->
                val locked = index >= unlockedChapterCount
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (locked) Modifier.alpha(0.5f) else Modifier.clickable { onChapterClick(index) }),
                    colors = CardDefaults.cardColors(containerColor = CardColor),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(story.chapters[index].title, color = PrimaryText, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (locked) "Capítulo ${index + 1} · termine o anterior para liberar" else "Capítulo ${index + 1}",
                                color = SecondaryText,
                                fontSize = 12.sp
                            )
                        }
                        Text(if (locked) "🔒" else "›", color = Accent, fontSize = if (locked) 20.sp else 28.sp)
                    }
                }
            }
        } else {
            item {
                Text(
                    "Esta é uma história interativa. A navegação acontece pelas escolhas durante a leitura — não há índice linear de capítulos.",
                    color = SecondaryText,
                    fontSize = 13.sp
                )
            }
        }
    }
}


@Composable
internal fun StoryStatsScreen(story: Story, stats: StoryStats) {
    val isChoiceStory = story.chapters.any { it.choices.isNotEmpty() || it.isEnding }
    val availableEndings = buildList {
        story.chapters.filter { it.isEnding }.forEach { chapter ->
            add("chapter:${chapter.id}" to "${chapter.title} · final alternativo")
        }
        story.chapters.forEach { chapter ->
            chapter.choices.filter { it.endsStory }.forEach { choice ->
                add("choice:${choice.id}" to "${choice.text} · final pela escolha em ${chapter.title}")
            }
        }
        val lastChapter = story.chapters.lastOrNull()
        if (!isChoiceStory && lastChapter != null && !lastChapter.isEnding) {
            add("chapter:${lastChapter.id}" to "${lastChapter.title} · final da história")
        }
    }
    val paths = buildList {
        story.chapters.forEach { chapter ->
            chapter.choices.filter { choice ->
                choice.endsStory || story.chapters.any { it.id == choice.nextChapterId }
            }.forEach { choice ->
                val target = story.chapters.firstOrNull { it.id == choice.nextChapterId }
                val destinationLabel = when {
                    choice.endsStory -> "final da história"
                    target != null -> target.title
                    else -> "destino removido"
                }
                add("choice:${choice.id}" to "${chapter.title} → $destinationLabel · ${choice.text}")
            }
        }
        story.chapters.forEachIndexed { index, from ->
            val allowsLinearContinuation = !isChoiceStory || (from.choices.isEmpty() && !from.isEnding)
            val to = story.chapters.getOrNull(index + 1)
            if (allowsLinearContinuation && to != null) {
                add("linear:${from.id}->${to.id}" to "${from.title} → ${to.title} · sequência")
            }
        }
    }
    val availableEndingKeys = availableEndings.map { it.first }.toSet()
    val currentPathKeys = paths.map { it.first }.toSet()
    val totalPaths = stats.pathTraversals.filterKeys { it in currentPathKeys }.values.sum()
    val uniquePaths = paths.count { (key, _) -> (stats.pathTraversals[key] ?: 0) > 0 }
    val endingsReached = stats.endingVisits.count { (key, visits) -> key in availableEndingKeys && visits > 0 }
    val completedEndingVisits = stats.endingVisits.filterKeys { it in availableEndingKeys }.values.sum()
    val visitedChapters = stats.chapterVisits.count { (id, visits) -> visits > 0 && story.chapters.any { it.id == id } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(story.title, color = PrimaryText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Estatísticas de leitura", color = SecondaryText, fontSize = 14.sp)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("Finais", "${availableEndings.size}", "definidos", Modifier.weight(1f))
                StatCard("Finais", "$endingsReached", "alcançados", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("Caminhos", "$totalPaths", "percorridos", Modifier.weight(1f))
                StatCard("Rotas", "$uniquePaths", "diferentes exploradas", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("Leituras", "${stats.completedReadings}", "concluídas", Modifier.weight(1f))
                StatCard("Capítulos", "$visitedChapters", "visitados", Modifier.weight(1f))
            }
        }
        item {
            Text("Finais definidos na história", color = PrimaryText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("$completedEndingVisits finalizações registradas", color = SecondaryText, fontSize = 12.sp)
        }
        if (availableEndings.isEmpty()) {
            item { Text("Esta história ainda não tem finais alternativos nem escolhas que encerrem a leitura.", color = SecondaryText, fontSize = 13.sp) }
        } else {
            items(availableEndings.size) { index ->
                val (key, label) = availableEndings[index]
                val visits = stats.endingVisits[key] ?: 0
                Card(colors = CardDefaults.cardColors(containerColor = CardColor), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, color = PrimaryText, fontSize = 13.sp)
                            Text(if (visits > 0) "Alcançado $visits vezes" else "Ainda não alcançado", color = if (visits > 0) SuccessColor else SecondaryText, fontSize = 11.sp)
                        }
                        Text("$visits", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Text("Caminhos percorridos", color = PrimaryText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("As transições registradas durante as leituras desta história.", color = SecondaryText, fontSize = 12.sp)
        }
        val traversed = paths.filter { (key, _) -> (stats.pathTraversals[key] ?: 0) > 0 }
            .sortedByDescending { (key, _) -> stats.pathTraversals[key] ?: 0 }
        if (traversed.isEmpty()) {
            item { Text("Nenhum caminho foi percorrido ainda. Comece uma leitura e faça uma escolha para ver os dados aqui.", color = SecondaryText, fontSize = 13.sp) }
        } else {
            items(traversed.size) { index ->
                val (key, label) = traversed[index]
                val count = stats.pathTraversals[key] ?: 0
                Card(colors = CardDefaults.cardColors(containerColor = CardColor), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = PrimaryText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("$count×", color = Accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
